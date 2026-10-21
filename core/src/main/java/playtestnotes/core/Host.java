package playtestnotes.core;

import java.util.List;

/** What the notes need from the server. The adapter implements it on Hytale; unit tests use a fake. */
public interface Host {
    /** One loaded plugin, for the table at the top of each notes file. */
    record PluginInfo(String id, String version) {}

    List<PluginInfo> plugins();

    /** The server build, for example {@code 0.7.2}, or {@code ?}. */
    String serverVersion();

    /** The server's configured name, for the {@code {server}} token. */
    String serverName();

    /** The default world's name, used for console notes. */
    String defaultWorld();

    /** Sends a chat line to every online operator except the given player (null: all of them). */
    void tellOperators(String text, java.util.UUID except);

    /** Sends a chat line to every online player except the given one (null: all of them). */
    void tellEveryone(String text, java.util.UUID except);

    void info(String message);

    void warn(String message);
}
