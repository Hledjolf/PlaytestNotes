package playtestnotes.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import playtestnotes.json.Json;

/**
 * {@code state.json} at the data-folder root: persisted data, not a setting. Versioned from day one. A file
 * from a newer version is read as far as it can be and rewritten in this version's shape.
 */
public record State(int version, long notesWritten, String lastFile) {
    public static final int VERSION = 1;
    public static final String FILE = "state.json";

    public static State empty() {
        return new State(VERSION, 0, null);
    }

    public State noted(String file) {
        return new State(VERSION, notesWritten + 1, file);
    }

    /** Reads the file; a missing or unreadable file gives an empty state (the problem is returned in the log line). */
    public static State load(Path file, java.util.function.Consumer<String> warn) {
        if (!Files.exists(file)) return empty();
        try {
            Map<String, Object> m = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
            long n = m.get("NotesWritten") instanceof Number x ? x.longValue() : 0;
            String last = m.get("LastFile") instanceof String s ? s : null;
            if (m.get("Version") instanceof Number v && v.intValue() > VERSION) {
                warn.accept("state.json is version " + v + ", newer than this mod's " + VERSION + "; reading what it can.");
            }
            return new State(VERSION, Math.max(0, n), last);
        } catch (IOException | RuntimeException e) {
            warn.accept("state.json can't be read (" + e.getMessage() + "); starting a new one.");
            return empty();
        }
    }

    public void save(Path file) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Version", (long) VERSION);
        m.put("NotesWritten", notesWritten);
        m.put("LastFile", lastFile);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, Json.pretty(m), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
