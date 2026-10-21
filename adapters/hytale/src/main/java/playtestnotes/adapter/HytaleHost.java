package playtestnotes.adapter;

import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.util.java.ManifestUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import playtestnotes.core.Host;

/** {@link Host} on the Hytale server: the plugin list, the server's name and build, chat and the log. */
public final class HytaleHost implements Host {
    /** The group {@code /op add} puts players in. */
    static final String OP_GROUP = "hytale:Admin";

    private final HytaleLogger logger;

    public HytaleHost(HytaleLogger logger) {
        this.logger = logger;
    }

    @Override
    public List<PluginInfo> plugins() {
        List<PluginInfo> out = new ArrayList<>();
        for (PluginBase p : PluginManager.get().getPlugins()) {
            PluginManifest m = p.getManifest();
            if (m == null) continue;
            out.add(new PluginInfo(m.getGroup() + ":" + m.getName(), m.getVersion() == null ? null : m.getVersion().toString()));
        }
        out.sort(Comparator.comparing(PluginInfo::id, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Override
    public String serverVersion() {
        String v = ManifestUtil.getImplementationVersion();
        if (v == null || v.isBlank()) v = ManifestUtil.getVersion();
        return v == null || v.isBlank() ? "?" : v;
    }

    @Override
    public String serverName() {
        try {
            String n = HytaleServer.get().getConfig().getServerName();
            return n == null || n.isBlank() ? "server" : n;
        } catch (RuntimeException e) {
            return "server";
        }
    }

    @Override
    public String defaultWorld() {
        Universe u = Universe.get();
        World w = u == null ? null : u.getDefaultWorld();
        return w == null ? "world" : w.getName();
    }

    /** Operator: in the {@code /op} group, or holding every permission. */
    static boolean isOperator(UUID player) {
        PermissionsModule perms = PermissionsModule.get();
        if (perms == null || player == null) return false;
        Set<String> groups = perms.getGroupsForUser(player);
        return groups != null && groups.contains(OP_GROUP) || perms.hasPermission(player, PermissionsModule.ROOT);
    }

    @Override
    public void tellOperators(String text, UUID except) {
        Universe u = Universe.get();
        if (u == null) return;
        Message m = Message.raw(text);
        for (PlayerRef pr : u.getPlayers()) if (!pr.getUuid().equals(except) && isOperator(pr.getUuid())) pr.sendMessage(m);
    }

    @Override
    public void tellEveryone(String text, UUID except) {
        Universe u = Universe.get();
        if (u == null) return;
        Message m = Message.raw(text);
        for (PlayerRef pr : u.getPlayers()) if (!pr.getUuid().equals(except)) pr.sendMessage(m);
    }

    @Override
    public void info(String message) {
        logger.atInfo().log("%s", message);
    }

    @Override
    public void warn(String message) {
        logger.atWarning().log("%s", message);
    }
}
