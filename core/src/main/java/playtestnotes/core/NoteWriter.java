package playtestnotes.core;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Appends lines to the current notes file. A file starts with the first note of a server session, and again
 * whenever the file name the template gives changes (a new date or world). Each start writes a header: the
 * title (for a new file), the server build, the time and the first noter, and the plugin table. Writes are
 * append-only, UTF-8, {@code \n} line endings, flushed after each line. All methods are synchronized: notes
 * come from command threads and settings-change lines from the world thread.
 */
public final class NoteWriter {
    private final Path dataDir;
    private final Host host;

    private Path current;
    private final java.util.Set<Path> started = new java.util.HashSet<>();
    private String firstNoter;

    public NoteWriter(Path dataDir, Host host) {
        this.dataDir = dataDir;
        this.host = host;
    }

    /** An output folder as a path: absolute as written, or relative to the data folder. */
    public Path folder(String outputFolder) {
        Path p = Path.of(outputFolder.isBlank() ? "." : outputFolder);
        return p.isAbsolute() ? p.normalize() : dataDir.resolve(p).normalize();
    }

    /** The file a note from {@code player} in {@code world} at {@code now} would go to. */
    public synchronized Path target(Settings s, Instant now, String world, String player) {
        String noter = firstNoter != null ? firstNoter : player;
        return folder(s.outputFolder()).resolve(Templates.fileName(s.fileNameTemplate(), Templates.date(now, s.useUtc()), world, host.serverName(), noter));
    }

    /** Appends one line, starting a file first if needed. Returns the file written. */
    public synchronized Path append(Settings s, Instant now, String world, String player, String line) throws IOException {
        Path file = target(s, now, world, player);
        if (!file.equals(current)) {
            if (firstNoter == null) firstNoter = player;
            if (started.add(file)) start(s, file, now, world);
            current = file;
        }
        write(file, line + "\n");
        return file;
    }

    /** Appends a line to the open file, if there is one. Returns whether it wrote. */
    public synchronized boolean appendToCurrent(String line) throws IOException {
        if (current == null) return false;
        write(current, line + "\n");
        return true;
    }

    public synchronized Path current() {
        return current;
    }

    /** The last {@code n} non-blank lines of a file (an empty list if it doesn't exist). */
    public synchronized List<String> tail(Path file, int n) throws IOException {
        if (file == null || !Files.exists(file)) return List.of();
        List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8), out = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && out.size() < n; i--) if (!all.get(i).isBlank()) out.add(0, all.get(i));
        return out;
    }

    /**
     * Checks that a folder can hold notes: creates it, then writes and deletes a test file. Throws with the
     * reason when it can't.
     */
    public static void checkWritable(Path folder) throws IOException {
        Files.createDirectories(folder);
        Path probe = folder.resolve(".playtestnotes-write-test");
        Files.writeString(probe, "ok", StandardCharsets.UTF_8);
        Files.delete(probe);
    }

    private void start(Settings s, Path file, Instant now, String world) throws IOException {
        Files.createDirectories(file.getParent());
        boolean fresh = !Files.exists(file) || Files.size(file) == 0;
        StringBuilder b = new StringBuilder();
        String time = Templates.time(now, "HH:mm:ss", s.useUtc());
        if (fresh) {
            b.append("# Playtest notes: ").append(world).append(", ").append(Templates.date(now, s.useUtc())).append("\n\n");
            b.append("Server ").append(host.serverVersion()).append(" · started ").append(time).append(" · notes by ").append(firstNoter).append("\n\n");
        } else {
            b.append("\n---\n\n");
            b.append("Server ").append(host.serverVersion()).append(" · session started ").append(time).append(" · notes by ").append(firstNoter).append("\n\n");
        }
        if (s.includeModList()) {
            b.append("| Plugin | Version |\n| --- | --- |\n");
            int builtIn = 0;
            for (Host.PluginInfo p : host.plugins()) {
                if (!s.modListIncludeBuiltIn() && p.id() != null && p.id().startsWith("Hytale:")) {
                    builtIn++;
                    continue;
                }
                b.append("| ").append(cell(p.id())).append(" | ").append(cell(p.version())).append(" |\n");
            }
            if (builtIn > 0) b.append("| + ").append(builtIn).append(" built-in Hytale plugins | ").append(cell(host.serverVersion())).append(" |\n");
            b.append("\n");
        }
        write(file, b.toString());
    }

    private static String cell(String v) {
        return v == null || v.isEmpty() ? "?" : v.replace("|", "\\|");
    }

    private static void write(Path file, String text) throws IOException {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
            w.write(text);
            w.flush();
        }
    }
}
