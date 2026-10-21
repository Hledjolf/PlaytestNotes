package playtestnotes.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import playtestnotes.core.Host;
import playtestnotes.core.NoteService;
import playtestnotes.core.Sender;

/**
 * Registers {@code /note} and {@code /notes} (or the names from {@code CommandName}). Both need no Hytale
 * permission; who may write is decided by the {@code WhoCanNote} setting. The sender's name, world, position
 * and operator status are read on its world thread; the note itself is written off it.
 */
public final class HytaleCommands {
    public static final String PERMISSION = "playtestnotes.write";

    private HytaleCommands() {}

    public static void register(CommandRegistry registry, NoteService service, Host host, HytaleLogger logger, String noteName) {
        PermissionsModule.registerPermission(PERMISSION);
        registry.registerCommand(new Cmd(noteName, "Writes a playtest note: /" + noteName + " <text>, with #tags", service, host, logger, false));
        registry.registerCommand(new Cmd(noteName + "s", "Shows recent playtest notes: /" + noteName + "s [n] | mark <label> | file", service, host, logger, true));
    }

    /** The text after the command name, exactly as typed (not re-joined from words). */
    static String rest(String input) {
        String s = input == null ? "" : input.strip();
        if (s.startsWith("/")) s = s.substring(1);
        int sp = indexOfSpace(s);
        return sp < 0 ? "" : s.substring(sp + 1).strip();
    }

    private static int indexOfSpace(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isWhitespace(s.charAt(i))) return i;
        return -1;
    }

    private static final class Cmd extends AbstractAsyncCommand {
        private final String name;
        private final NoteService service;
        private final Host host;
        private final HytaleLogger logger;
        private final boolean list;

        Cmd(String name, String description, NoteService service, Host host, HytaleLogger logger, boolean list) {
            super(name, description);
            this.name = name;
            this.service = service;
            this.host = host;
            this.logger = logger;
            this.list = list;
            setAllowsExtraArguments(true);
            requireNoPermission();
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            String text = rest(context.getInputString());
            CompletableFuture<Sender> who;
            if (!context.isPlayer()) {
                who = CompletableFuture.completedFuture(Sender.CONSOLE);
            } else {
                Ref<EntityStore> ref = context.senderAsPlayerRef();
                if (ref == null || !ref.isValid()) {
                    context.sendMessage(Message.raw("You are not in a world."));
                    return CompletableFuture.completedFuture(null);
                }
                World w = ref.getStore().getExternalData().getWorld();
                UUID id = context.sender().getUuid();
                String user = context.sender().getUsername();
                boolean perm = context.sender().hasPermission(PERMISSION);
                who = CompletableFuture.supplyAsync(() -> {
                    Store<EntityStore> store = ref.getStore();
                    TransformComponent t = ref.isValid() ? store.getComponent(ref, TransformComponent.getComponentType()) : null;
                    Vector3d p = t == null ? null : t.getPosition();
                    return new Sender(user, id, w.getName(), p == null ? Double.NaN : p.x, p == null ? Double.NaN : p.y, p == null ? Double.NaN : p.z,
                        HytaleHost.isOperator(id), perm);
                }, w);
            }
            return who.thenAcceptAsync(s -> {
                NoteService.Reply r = list ? service.notes(s, words(text)) : service.note(s, text);
                for (String l : r.lines()) context.sendMessage(Message.raw(l));
                if (r.echo() != null) {
                    if (r.audience().equals("operators")) host.tellOperators(r.echo(), s.id());
                    else if (r.audience().equals("everyone")) host.tellEveryone(r.echo(), s.id());
                }
            }).exceptionally(t -> {
                logger.atSevere().withCause(t).log("[PlaytestNotes] /%s failed", name);
                context.sendMessage(Message.raw("/" + name + " failed: " + t.getMessage()));
                return null;
            });
        }

        private static List<String> words(String text) {
            List<String> out = new ArrayList<>();
            for (String w : text.split("\\s+")) if (!w.isEmpty()) out.add(w);
            return out;
        }
    }
}
