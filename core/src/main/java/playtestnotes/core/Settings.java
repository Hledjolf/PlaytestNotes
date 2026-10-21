package playtestnotes.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import playtestnotes.json.Json;

/**
 * The mod's settings, read from {@code notes/settings.json}. Immutable: a change builds a new object, which the
 * plugin swaps in atomically. Every key is optional in the file; a missing key gets its default, and a value
 * that is the wrong type or out of range also gets its default, with a warning.
 */
public record Settings(
    boolean enabled,
    String outputFolder,
    String fileNameTemplate,
    String lineTemplate,
    String timeFormat,
    boolean useUtc,
    int coordinatePrecision,
    int maxNoteLength,
    double cooldownSeconds,
    boolean includeModList,
    boolean modListIncludeBuiltIn,
    String echoToChat,
    String whoCanNote,
    String commandName,
    List<String> tags,
    int hookDelayMillis) {

    public static final String FILE = "notes/settings.json";

    public static final List<String> TIME_FORMATS = List.of("HH:mm", "HH:mm:ss", "h:mm a", "h:mm:ss a", "ISO-8601", "epoch-ms");
    public static final List<String> ECHO = List.of("none", "self", "operators", "everyone");
    public static final List<String> WHO = List.of("everyone", "permission", "operators");

    public static final String DEFAULT_LINE = "- {time} **{player}** @ {world} ({x}, {y}, {z}): {text} {tags}";
    public static final List<String> DEFAULT_TAGS = List.of("bug", "idea", "balance", "art", "perf", "ui");

    public static Settings defaults() {
        return new Settings(true, "notes", "{date}_{world}.md", DEFAULT_LINE, "HH:mm:ss", false, 0, 280, 0.5, true, false,
            "self", "everyone", "note", DEFAULT_TAGS, 0);
    }

    /** The keys written on first run, in this order. Every other key stays out of the file until someone sets it. */
    public static String firstRunJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        Settings d = defaults();
        m.put("Enabled", d.enabled);
        m.put("OutputFolder", d.outputFolder);
        m.put("FileNameTemplate", d.fileNameTemplate);
        m.put("LineTemplate", d.lineTemplate);
        m.put("MaxNoteLength", (long) d.maxNoteLength);
        m.put("CooldownSeconds", d.cooldownSeconds);
        m.put("Tags", new ArrayList<Object>(d.tags));
        return Json.pretty(m);
    }

    /** A loaded settings object plus any problems found (each one a sentence for the log). */
    public record Loaded(Settings settings, List<String> warnings) {}

    /** Writes the first-run file if there is none. Returns true if it wrote one. Never touches an existing file. */
    public static boolean writeFirstRun(Path file) throws IOException {
        if (Files.exists(file)) return false;
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, firstRunJson(), StandardCharsets.UTF_8);
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    /** Reads the file. A missing file gives the defaults; a file that isn't valid JSON throws. */
    public static Loaded load(Path file) throws IOException {
        if (!Files.exists(file)) return new Loaded(defaults(), List.of());
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** Parses settings text. Throws {@link Json.ParseException} for text that isn't a JSON object. */
    public static Loaded parse(String text) {
        Map<String, Object> m = Json.parseObject(text);
        List<String> w = new ArrayList<>();
        Settings d = defaults();
        Reader r = new Reader(m, w);
        Object debug = m.get("Debug");
        Map<?, ?> dbg = debug instanceof Map<?, ?> dm ? dm : Map.of();
        if (debug != null && !(debug instanceof Map)) w.add("Debug should be an object; ignored.");
        int delay = new Reader(castMap(dbg), w).integer("HookDelayMillis", 0, 0, 5000, "Debug.");
        String fileName = r.string("FileNameTemplate", d.fileNameTemplate);
        if (!validFileTemplate(fileName)) {
            w.add("FileNameTemplate \"" + fileName + "\" must end in .md or .txt; using " + d.fileNameTemplate + ".");
            fileName = d.fileNameTemplate;
        }
        String command = r.string("CommandName", d.commandName).strip();
        if (command.startsWith("/")) command = command.substring(1);
        if (!command.matches("[A-Za-z][A-Za-z0-9_-]{0,31}")) {
            w.add("CommandName \"" + command + "\" must be a single word of letters, digits, _ or -; using note.");
            command = d.commandName;
        }
        return new Loaded(new Settings(
            r.bool("Enabled", d.enabled),
            r.string("OutputFolder", d.outputFolder),
            fileName,
            r.string("LineTemplate", d.lineTemplate),
            r.option("TimeFormat", d.timeFormat, TIME_FORMATS),
            r.bool("UseUtc", d.useUtc),
            r.integer("CoordinatePrecision", d.coordinatePrecision, 0, 3, ""),
            r.integer("MaxNoteLength", d.maxNoteLength, 20, 2000, ""),
            r.number("CooldownSeconds", d.cooldownSeconds, 0, 10),
            r.bool("IncludeModList", d.includeModList),
            r.bool("ModListIncludeBuiltIn", d.modListIncludeBuiltIn),
            r.option("EchoToChat", d.echoToChat, ECHO),
            r.option("WhoCanNote", d.whoCanNote, WHO),
            command,
            r.tags("Tags", d.tags),
            delay), List.copyOf(w));
    }

    public static boolean validFileTemplate(String t) {
        String s = t.toLowerCase(Locale.ROOT);
        return !t.isBlank() && (s.endsWith(".md") || s.endsWith(".txt"));
    }

    /** The list command's name: the note command plus "s". */
    public String listCommandName() {
        return commandName + "s";
    }

    /** A setting's value by its key in the file (dotted for nested keys), for the settings-change line. */
    public Object get(String key) {
        return switch (key) {
            case "Enabled" -> enabled;
            case "OutputFolder" -> outputFolder;
            case "FileNameTemplate" -> fileNameTemplate;
            case "LineTemplate" -> lineTemplate;
            case "TimeFormat" -> timeFormat;
            case "UseUtc" -> useUtc;
            case "CoordinatePrecision" -> coordinatePrecision;
            case "MaxNoteLength" -> maxNoteLength;
            case "CooldownSeconds" -> cooldownSeconds;
            case "IncludeModList" -> includeModList;
            case "ModListIncludeBuiltIn" -> modListIncludeBuiltIn;
            case "EchoToChat" -> echoToChat;
            case "WhoCanNote" -> whoCanNote;
            case "CommandName" -> commandName;
            case "Tags" -> tags;
            case "Debug.HookDelayMillis" -> hookDelayMillis;
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    /** Reads typed values from the decoded file, falling back to the default with a warning. */
    private record Reader(Map<String, Object> m, List<String> w) {
        boolean bool(String k, boolean def) {
            Object v = m.get(k);
            if (v == null) return def;
            if (v instanceof Boolean b) return b;
            w.add(k + " should be true or false; using " + def + ".");
            return def;
        }

        String string(String k, String def) {
            Object v = m.get(k);
            if (v == null) return def;
            if (v instanceof String s) return s;
            w.add(k + " should be text; using \"" + def + "\".");
            return def;
        }

        String option(String k, String def, List<String> options) {
            String s = string(k, def);
            if (options.contains(s)) return s;
            w.add(k + " \"" + s + "\" is not one of " + options + "; using " + def + ".");
            return def;
        }

        int integer(String k, int def, int min, int max, String prefix) {
            Object v = m.get(k);
            if (v == null) return def;
            if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && n.doubleValue() >= min && n.doubleValue() <= max) {
                return n.intValue();
            }
            w.add(prefix + k + " should be a whole number from " + min + " to " + max + "; using " + def + ".");
            return def;
        }

        double number(String k, double def, double min, double max) {
            Object v = m.get(k);
            if (v == null) return def;
            if (v instanceof Number n && n.doubleValue() >= min && n.doubleValue() <= max) return n.doubleValue();
            w.add(k + " should be a number from " + Json.write(min) + " to " + Json.write(max) + "; using " + Json.write(def) + ".");
            return def;
        }

        List<String> tags(String k, List<String> def) {
            Object v = m.get(k);
            if (v == null) return def;
            if (v instanceof List<?> l) {
                List<String> out = new ArrayList<>();
                for (Object o : l) {
                    if (o instanceof String s && !s.isBlank()) {
                        String t = s.strip();
                        if (t.startsWith("#")) t = t.substring(1);
                        if (!t.isEmpty() && out.stream().noneMatch(t::equalsIgnoreCase)) out.add(t);
                    } else {
                        w.add(k + " should hold only words; ignored " + Json.write(o) + ".");
                    }
                }
                return List.copyOf(out);
            }
            w.add(k + " should be a list of words; using the default tags.");
            return def;
        }
    }
}
