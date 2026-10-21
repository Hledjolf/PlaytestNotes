package playtestnotes.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import playtestnotes.json.Json;

/**
 * Playtest Notes without the server: the {@code /note} and {@code /notes} commands, the settings, the state
 * file and the live-apply hook's work. The adapter turns commands into calls here and sends the replies.
 */
public final class NoteService {
    /** What a command produced: lines for the sender, and a line for other players (with who gets it). */
    public record Reply(List<String> lines, String echo, String audience) {
        static Reply of(String... lines) {
            return new Reply(List.of(lines), null, "none");
        }
    }

    private final Path dataDir;
    private final Host host;
    private final Clock clock;
    private final LongSupplier nanos;
    private final NoteWriter writer;
    private final Map<String, Long> lastNote = new HashMap<>();

    private volatile Settings settings;
    private State state;

    public NoteService(Path dataDir, Host host, Clock clock, LongSupplier nanos) {
        this.dataDir = dataDir;
        this.host = host;
        this.clock = clock;
        this.nanos = nanos;
        this.writer = new NoteWriter(dataDir, host);
        this.settings = Settings.defaults();
        this.state = State.empty();
    }

    public Path settingsFile() {
        return dataDir.resolve(Settings.FILE);
    }

    public Path stateFile() {
        return dataDir.resolve(State.FILE);
    }

    public Settings settings() {
        return settings;
    }

    public NoteWriter writer() {
        return writer;
    }

    public synchronized State state() {
        return state;
    }

    /**
     * Boot: writes the first-run settings file if there is none, loads settings and state. Problems are logged;
     * a settings file that can't be parsed, or an output folder that can't be written, falls back to defaults.
     */
    public void boot() {
        try {
            if (Settings.writeFirstRun(settingsFile())) host.info("[PlaytestNotes] wrote " + Settings.FILE + " with the default settings");
        } catch (IOException e) {
            host.warn("[PlaytestNotes] can't write " + settingsFile() + ": " + e.getMessage());
        }
        Settings s;
        try {
            Settings.Loaded l = Settings.load(settingsFile());
            l.warnings().forEach(w -> host.warn("[PlaytestNotes] " + Settings.FILE + ": " + w));
            s = l.settings();
        } catch (IOException | RuntimeException e) {
            host.warn("[PlaytestNotes] " + Settings.FILE + " can't be read (" + e.getMessage() + "); using the defaults until it's fixed");
            s = Settings.defaults();
        }
        try {
            NoteWriter.checkWritable(writer.folder(s.outputFolder()));
        } catch (IOException | RuntimeException e) {
            host.warn("[PlaytestNotes] OutputFolder \"" + s.outputFolder() + "\" can't be written (" + e + "); notes go to \"notes\" until it's fixed");
            s = withFolder(s, "notes");
        }
        settings = s;
        synchronized (this) {
            state = State.load(stateFile(), w -> host.warn("[PlaytestNotes] " + w));
        }
    }

    public synchronized void saveState() {
        try {
            state.save(stateFile());
        } catch (IOException e) {
            host.warn("[PlaytestNotes] can't save " + State.FILE + ": " + e.getMessage());
        }
    }

    // ---- /note -----------------------------------------------------------------------------------------------

    public Reply note(Sender who, String rawText) {
        Settings s = settings;
        if (!s.enabled()) return Reply.of("Playtest Notes is turned off.");
        String refused = mayWrite(who, s);
        if (refused != null) return Reply.of(refused);
        String text = rawText == null ? "" : rawText.strip();
        if (text.isEmpty()) return Reply.of("Usage: /" + s.commandName() + " <text>. Words like #" + (s.tags().isEmpty() ? "bug" : s.tags().get(0)) + " become tags.");
        int len = NoteParser.length(text);
        if (len > s.maxNoteLength()) return Reply.of("That note is " + len + " characters; the limit is " + s.maxNoteLength() + ". Nothing was written.");
        long now = nanos.getAsLong();
        synchronized (lastNote) {
            Long prev = lastNote.get(who.cooldownKey());
            if (prev != null && now - prev < (long) (s.cooldownSeconds() * 1e9)) {
                return Reply.of("Too soon after your last note (cooldown " + Json.write(s.cooldownSeconds()) + " s). Nothing was written.");
            }
            lastNote.put(who.cooldownKey(), now);
        }
        NoteParser.Parsed p = NoteParser.parse(text, s.tags());
        Instant t = clock.instant();
        String world = worldOf(who);
        Map<String, String> v = new HashMap<>();
        v.put("time", Templates.time(t, s.timeFormat(), s.useUtc()));
        v.put("player", who.name());
        v.put("world", world);
        v.put("x", who.hasPosition() ? Templates.coordinate(who.x(), s.coordinatePrecision()) : "-");
        v.put("y", who.hasPosition() ? Templates.coordinate(who.y(), s.coordinatePrecision()) : "-");
        v.put("z", who.hasPosition() ? Templates.coordinate(who.z(), s.coordinatePrecision()) : "-");
        v.put("text", p.text());
        v.put("tags", p.tagText());
        String line = Templates.expand(s.lineTemplate(), v).stripTrailing();
        String chatLine = ChatText.line(s.lineTemplate(), v);
        Path file;
        try {
            file = writer.append(s, t, world, who.name(), line);
        } catch (IOException | RuntimeException e) {
            host.warn("[PlaytestNotes] can't write a note: " + e);
            synchronized (lastNote) {
                lastNote.remove(who.cooldownKey());
            }
            return Reply.of("The note couldn't be written: " + e.getMessage());
        }
        synchronized (this) {
            state = state.noted(relative(file));
        }
        saveState();
        return switch (s.echoToChat()) {
            case "none" -> new Reply(List.of(), null, "none");
            case "operators" -> new Reply(List.of("Noted. " + chatLine), who.name() + " noted: " + chatLine, "operators");
            case "everyone" -> new Reply(List.of("Noted. " + chatLine), who.name() + " noted: " + chatLine, "everyone");
            default -> new Reply(List.of("Noted. " + chatLine), null, "none");
        };
    }

    // ---- /notes ----------------------------------------------------------------------------------------------

    public Reply notes(Sender who, List<String> args) {
        Settings s = settings;
        String cmd = "/" + s.listCommandName();
        if (args.isEmpty()) return tail(who, 5);
        String sub = args.get(0).toLowerCase(Locale.ROOT);
        if (sub.equals("file")) {
            if (!who.operator()) return Reply.of("Only operators can see where the notes file is.");
            Path f = currentOrTarget(who);
            return Reply.of((Files.exists(f) ? "Notes file: " : "Notes file (not started yet): ") + f.toAbsolutePath());
        }
        if (sub.equals("mark")) {
            if (!s.enabled()) return Reply.of("Playtest Notes is turned off.");
            String refused = mayWrite(who, s);
            if (refused != null) return Reply.of(refused);
            String label = String.join(" ", args.subList(1, args.size())).strip();
            if (label.isEmpty()) return Reply.of("Usage: " + cmd + " mark <label>");
            if (NoteParser.length(label) > s.maxNoteLength()) return Reply.of("That label is longer than " + s.maxNoteLength() + " characters. Nothing was written.");
            try {
                writer.append(s, clock.instant(), worldOf(who), who.name(), "## " + label);
            } catch (IOException | RuntimeException e) {
                host.warn("[PlaytestNotes] can't write a mark: " + e);
                return Reply.of("The heading couldn't be written: " + e.getMessage());
            }
            return Reply.of("Started a new part: " + label);
        }
        if (sub.matches("\\d{1,3}")) {
            int n = Integer.parseInt(sub);
            if (n < 1 || n > 20) return Reply.of("Show 1 to 20 lines: " + cmd + " <n>");
            return tail(who, n);
        }
        return Reply.of("Usage: " + cmd + " [1-20] | " + cmd + " mark <label> | " + cmd + " file");
    }

    private Reply tail(Sender who, int n) {
        Path f = currentOrTarget(who);
        try {
            List<String> lines = writer.tail(f, n);
            if (lines.isEmpty()) return Reply.of("No notes yet today.");
            List<String> out = new ArrayList<>(ChatText.plainAll(lines));
            return new Reply(out, null, "none");
        } catch (IOException e) {
            return Reply.of("The notes file couldn't be read: " + e.getMessage());
        }
    }

    private Path currentOrTarget(Sender who) {
        Path c = writer.current();
        return c != null ? c : writer.target(settings, clock.instant(), worldOf(who), who.name());
    }

    // ---- the live-apply hook ----------------------------------------------------------------------------------

    /**
     * The work of {@code onSettingsChanged}: re-reads the settings file, checks a new output folder or file name,
     * swaps the settings in and writes a settings-change line. Throws {@link IllegalStateException} (on purpose)
     * when the new folder can't be written or the file name is invalid; the old settings stay in force.
     */
    public boolean settingsChanged(Map<String, Object> changed) {
        sleepFor(debugDelay());
        Settings old = settings;
        Settings.Loaded l;
        try {
            l = Settings.load(settingsFile());
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Playtest Notes: " + Settings.FILE + " can't be read: " + e.getMessage(), e);
        }
        Settings next = l.settings();
        l.warnings().forEach(w -> host.warn("[PlaytestNotes] " + Settings.FILE + ": " + w));
        if (changed.containsKey("FileNameTemplate") && changed.get("FileNameTemplate") instanceof String t && !Settings.validFileTemplate(t)) {
            throw new IllegalStateException("Playtest Notes: FileNameTemplate \"" + t + "\" must end in .md or .txt");
        }
        if (changed.containsKey("OutputFolder") || !next.outputFolder().equals(old.outputFolder())) {
            try {
                NoteWriter.checkWritable(writer.folder(next.outputFolder()));
            } catch (IOException | RuntimeException e) {
                throw new IllegalStateException("Playtest Notes: OutputFolder \"" + next.outputFolder() + "\" can't be written: " + e, e);
            }
        }
        settings = next;
        List<String> parts = new ArrayList<>();
        for (var e : changed.entrySet()) {
            Object before = old.get(e.getKey());
            if (sameValue(before, e.getValue())) continue;
            parts.add(e.getKey() + " " + shown(before) + " -> " + shown(e.getValue()));
        }
        if (!parts.isEmpty()) {
            try {
                writer.appendToCurrent("- " + Templates.time(clock.instant(), next.timeFormat(), next.useUtc()) + " *settings changed: " + String.join(", ", parts) + " (by MSM)*");
            } catch (IOException e) {
                host.warn("[PlaytestNotes] can't write the settings-change line: " + e.getMessage());
            }
        }
        return true;
    }

    /** {@code Debug.HookDelayMillis} as it is in the file now (0 if the file can't be read). */
    int debugDelay() {
        try {
            return Settings.load(settingsFile()).settings().hookDelayMillis();
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    private static void sleepFor(int millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private static String mayWrite(Sender who, Settings s) {
        if (who.isConsole()) return null;
        return switch (s.whoCanNote()) {
            case "operators" -> who.operator() ? null : "Only operators can write notes on this server.";
            case "permission" -> who.operator() || who.hasWritePermission() ? null : "You need the playtestnotes.write permission to write notes.";
            default -> null;
        };
    }

    private String worldOf(Sender who) {
        return who.world() != null ? who.world() : host.defaultWorld();
    }

    private String relative(Path file) {
        Path abs = file.toAbsolutePath().normalize(), base = dataDir.toAbsolutePath().normalize();
        return abs.startsWith(base) ? base.relativize(abs).toString().replace('\\', '/') : abs.toString();
    }

    /** Whether a settings-window value equals the running one (numbers compare by value, whatever their type). */
    static boolean sameValue(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) return x.doubleValue() == y.doubleValue();
        return java.util.Objects.equals(a, b);
    }

    private static String shown(Object v) {
        if (v == null) return "(unset)";
        if (v instanceof Integer i) return Integer.toString(i);
        return v instanceof String s ? s : Json.write(v);
    }

    private static Settings withFolder(Settings s, String folder) {
        return new Settings(s.enabled(), folder, s.fileNameTemplate(), s.lineTemplate(), s.timeFormat(), s.useUtc(), s.coordinatePrecision(),
            s.maxNoteLength(), s.cooldownSeconds(), s.includeModList(), s.modListIncludeBuiltIn(), s.echoToChat(), s.whoCanNote(), s.commandName(), s.tags(), s.hookDelayMillis());
    }
}
