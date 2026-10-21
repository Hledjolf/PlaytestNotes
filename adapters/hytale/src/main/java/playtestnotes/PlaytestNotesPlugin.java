package playtestnotes;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import java.time.Clock;
import java.util.Map;
import playtestnotes.adapter.HytaleCommands;
import playtestnotes.adapter.HytaleHost;
import playtestnotes.core.NoteService;

/**
 * Playtest Notes: {@code /note <text>} appends a timestamped line to a Markdown file. Wires the Hytale adapter
 * to the plain-Java {@link NoteService}, and carries the optional live-apply hook that Mod Settings Manager
 * finds by reflection. Nothing here depends on Mod Settings Manager; without it, the hook is never called.
 */
public final class PlaytestNotesPlugin extends JavaPlugin {
    private static volatile PlaytestNotesPlugin instance;

    private NoteService service;

    public PlaytestNotesPlugin(JavaPluginInit init) {
        super(init);
    }

    /** The running instance (for local test scenarios). */
    public static PlaytestNotesPlugin get() {
        return instance;
    }

    public NoteService service() {
        return service;
    }

    @Override
    protected void setup() {
        instance = this;
        HytaleHost host = new HytaleHost(getLogger());
        service = new NoteService(getDataDirectory(), host, Clock.systemDefaultZone(), System::nanoTime);
        service.boot();
        // CommandName is read once: a command can't be renamed after registration (the sidecar marks it restart: true).
        HytaleCommands.register(getCommandRegistry(), service, host, getLogger(), service.settings().commandName());
    }

    @Override
    protected void shutdown() {
        // Saves state.json only. notes/settings.json is never written after first run, so edits made while the
        // server runs (by hand or through Mod Settings Manager) are never put back.
        if (service != null) service.saveState();
    }

    /**
     * The live-apply hook (Mod Settings Manager's sidecar contract): re-reads notes/settings.json and applies it.
     * It throws on purpose when a new OutputFolder can't be written or FileNameTemplate is invalid; the old
     * settings stay in force. Don't turn the throw into {@code return false}: the throw puts the reason and stack
     * trace in the server log.
     */
    public boolean onSettingsChanged(Map<String, Object> changed) {
        return service.settingsChanged(changed);
    }
}
