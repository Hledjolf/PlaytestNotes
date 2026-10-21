package playtestnotes.core;

import java.util.UUID;

/**
 * Who ran a command, and where. The console has no id and no position.
 *
 * @param world the sender's world, or null for the console (the default world is used)
 * @param x position, or NaN when there is none (y and z the same)
 */
public record Sender(String name, UUID id, String world, double x, double y, double z, boolean operator, boolean hasWritePermission) {
    public static final Sender CONSOLE = new Sender("console", null, null, Double.NaN, Double.NaN, Double.NaN, true, true);

    public boolean isConsole() {
        return id == null;
    }

    public boolean hasPosition() {
        return !Double.isNaN(x);
    }

    /** The key the cooldown is kept under. */
    String cooldownKey() {
        return id == null ? "console" : id.toString();
    }
}
