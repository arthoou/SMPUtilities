package com.arthou.smputilities.blocklog;

import com.arthou.smputilities.core.blocklog.BlockLog;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.mc.Ids;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Feeds the block log. A block change is logged when it happens while the
 * server handles a player's action (breaking, placing, using an item on a
 * block: buckets, flint and steel, hoes, shovels and axes all count) or an
 * explosion; changes of a block's state that keep the block (doors, levers,
 * crops) are left out. Containers are compared when a player closes one
 * they opened by clicking it.
 */
public final class BlockLogService {
    /** Who is changing blocks right now: a player (with the block they clicked), or "#explosion". */
    private record Actor(String id, String name, ServerPlayer player, BlockPos clicked) {
    }

    private record Before(BlockPos pos, BlockState state, String data) {
    }

    private record Session(String dimension, BlockPos pos, Map<String, Integer> before, AbstractContainerMenu menu) {
    }

    public static final String EXPLOSION = "#explosion";

    // Server thread only.
    private static final ArrayDeque<Actor> ACTORS = new ArrayDeque<>();
    private static final ArrayDeque<Before> CHANGES = new ArrayDeque<>();
    private static final Map<UUID, Session> OPEN = new HashMap<>();
    private static final Set<UUID> INSPECTING = new HashSet<>();
    /** The game asks once per hand; one click shows the history once. */
    private static final Map<UUID, Long> LAST_INSPECT = new HashMap<>();
    private static final long INSPECT_REPEAT_MILLIS = 250;

    private BlockLogService() {
    }

    static BlockLog log() {
        SuServer context = SuServer.get();
        return context == null || !SuServer.config().features.blockLog ? null : context.blockLog();
    }

    private static boolean onServerThread(MinecraftServer server) {
        return server != null && server.isSameThread();
    }

    // -------------------------------------------------------------- actors

    /** The server starts handling a player's action; {@code clicked} is the block they clicked, if any. */
    public static void beginPlayer(ServerPlayer player, BlockPos clicked) {
        if (log() == null || !onServerThread(Mc.server(player))) {
            return;
        }
        ACTORS.push(new Actor(player.getUUID().toString(), Names.realName(player), player, clicked == null ? null : clicked.immutable()));
    }

    public static void endPlayer(ServerPlayer player) {
        if (log() == null || !onServerThread(Mc.server(player))) {
            return;
        }
        end();
    }

    public static void beginExplosion(Level level) {
        if (level instanceof ServerLevel serverLevel && log() != null && SuServer.config().blockLog.explosions
            && onServerThread(serverLevel.getServer())) {
            ACTORS.push(new Actor(EXPLOSION, EXPLOSION, null, null));
        }
    }

    public static void endExplosion(Level level) {
        if (level instanceof ServerLevel serverLevel && log() != null && SuServer.config().blockLog.explosions
            && onServerThread(serverLevel.getServer())) {
            end();
        }
    }

    private static void end() {
        ACTORS.poll();
        if (ACTORS.isEmpty()) {
            CHANGES.clear();
        }
    }

    /** Start of a tick: nothing can still be running, even if a handler threw. */
    public static void tick() {
        ACTORS.clear();
        CHANGES.clear();
    }

    // ---------------------------------------------------------- set block

    public static void beforeSetBlock(ServerLevel level, BlockPos pos) {
        if (ACTORS.isEmpty() || !onServerThread(level.getServer())) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        String data = state.hasBlockEntity() ? BlockTexts.blockEntity(level.getBlockEntity(pos), level.registryAccess()) : "";
        CHANGES.push(new Before(pos.immutable(), state, data));
    }

    public static void afterSetBlock(ServerLevel level, boolean changed) {
        if (ACTORS.isEmpty() || !onServerThread(level.getServer())) {
            return;
        }
        Before before = CHANGES.poll();
        BlockLog log = log();
        if (before == null || !changed || log == null) {
            return;
        }
        BlockState after = level.getBlockState(before.pos());
        if (after.getBlock() == before.state().getBlock()) {
            return;
        }
        Actor actor = ACTORS.peek();
        BlockLog.Action action = actor.player() == null ? BlockLog.Action.EXPLODE : after.isAir() ? BlockLog.Action.BREAK : BlockLog.Action.PLACE;
        log.add(new BlockLog.Entry(System.currentTimeMillis(), action, actor.id(), actor.name(), Ids.key(level.dimension()),
            before.pos().getX(), before.pos().getY(), before.pos().getZ(), BlockTexts.state(before.state()), BlockTexts.state(after),
            before.data(), 1));
    }

    // ---------------------------------------------------------- containers

    /** A menu opened: when the player clicked a container block for it, remember what it held. */
    public static void openedMenu(ServerPlayer player) {
        if (log() == null || !SuServer.config().blockLog.containers) {
            return;
        }
        Actor actor = ACTORS.peek();
        if (actor == null || actor.player() != player || actor.clicked() == null) {
            return;
        }
        ServerLevel level = Mc.level(player);
        if (!(level.getBlockEntity(actor.clicked()) instanceof Container)) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        Map<String, Integer> contents = contents(menu, player);
        if (contents != null) {
            OPEN.put(player.getUUID(), new Session(Ids.key(level.dimension()), actor.clicked(), contents, menu));
        }
    }

    /** A menu is closing: log what went in and out of the container. */
    public static void closingMenu(ServerPlayer player) {
        Session session = OPEN.remove(player.getUUID());
        BlockLog log = log();
        if (session == null || log == null || player.containerMenu != session.menu()) {
            return;
        }
        Map<String, Integer> after = contents(session.menu(), player);
        if (after == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Set<String> keys = new HashSet<>(session.before().keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            int difference = after.getOrDefault(key, 0) - session.before().getOrDefault(key, 0);
            if (difference != 0) {
                log.add(new BlockLog.Entry(now, difference > 0 ? BlockLog.Action.ADD : BlockLog.Action.TAKE, player.getUUID().toString(),
                    Names.realName(player), session.dimension(), session.pos().getX(), session.pos().getY(), session.pos().getZ(),
                    key, "", "", Math.abs(difference)));
            }
        }
    }

    /** Item -> count over the menu's container slots, or null when it has none. */
    private static Map<String, Integer> contents(AbstractContainerMenu menu, ServerPlayer player) {
        RegistryAccess registries = Mc.server(player).registryAccess();
        Map<String, Integer> counts = new LinkedHashMap<>();
        boolean any = false;
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory() || slot.container instanceof PlayerEnderChestContainer) {
                continue;
            }
            any = true;
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                counts.merge(BlockTexts.item(stack, registries), stack.getCount(), Integer::sum);
            }
        }
        return any ? counts : null;
    }

    public static void forget(ServerPlayer player) {
        closingMenu(player);
        INSPECTING.remove(player.getUUID());
        LAST_INSPECT.remove(player.getUUID());
    }

    public static void onServerStop() {
        ACTORS.clear();
        CHANGES.clear();
        OPEN.clear();
        INSPECTING.clear();
        LAST_INSPECT.clear();
        BlockLogCommands.onServerStop();
    }

    // ------------------------------------------------------------- inspect

    public static boolean toggleInspect(ServerPlayer player) {
        if (!INSPECTING.remove(player.getUUID())) {
            INSPECTING.add(player.getUUID());
            return true;
        }
        return false;
    }

    /** A click on a block: when the player inspects, shows its history and returns true (the click does nothing else). */
    public static boolean inspect(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!INSPECTING.contains(player.getUUID()) || log() == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long key = pos.asLong() ^ now / INSPECT_REPEAT_MILLIS;
        Long last = LAST_INSPECT.put(player.getUUID(), key);
        if (last == null || last != key) {
            BlockLogCommands.showHistory(player, Ids.key(level.dimension()), pos.immutable());
        }
        return true;
    }

    static SuConfig.BlockLog config() {
        return SuServer.config().blockLog;
    }
}
