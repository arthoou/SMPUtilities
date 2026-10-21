package com.arthou.smputilities.teleport;

import com.arthou.smputilities.mc.Ids;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.teleport.Cooldowns;
import com.arthou.smputilities.core.teleport.Location;
import com.arthou.smputilities.core.util.Durations;
import com.arthou.smputilities.dimension.DimensionService;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Teleport helpers with warmup, cooldown, cancel on move/damage, /back
 * tracking and per-dimension command restrictions (ported from TPA).
 */
public final class Teleports {
    private static final Cooldowns COOLDOWNS = new Cooldowns();
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private record Pending(String key, long cooldownMillis, Supplier<Location> destination, Location start, long fireAt, boolean saveBack, Runnable after) {
    }

    private Teleports() {
    }

    public static Location locationOf(ServerPlayer player) {
        return new Location(player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ(),
            player.getYRot(), player.getXRot());
    }

    public static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = Ids.parse(dimension);
        if (id == null) {
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /** Immediate teleport. Saves the current position for /back when asked. */
    public static boolean teleport(ServerPlayer player, Location location, boolean saveBack) {
        ServerLevel level = level(Mc.server(player), location.dimension());
        if (level == null) {
            Msg.error(player, "teleport.world_unavailable");
            return false;
        }
        if (!level.dimension().equals(player.level().dimension()) && !DimensionService.mayEnter(player, level.dimension())) {
            DimensionService.sendBlockedMessage(player, level.dimension());
            return false;
        }
        Location before = locationOf(player);
        if (player.isPassenger()) {
            player.stopRiding();
        }
        player.teleportTo(level, location.x(), location.y(), location.z(), location.yaw(), location.pitch());
        if (saveBack && SuServer.get() != null) {
            SuServer.get().players().update(player.getUUID(), record -> record.back = before);
        }
        return true;
    }

    /**
     * Teleport after the configured warmup. The destination is resolved when
     * the warmup ends, so /tpa follows a target that keeps moving.
     *
     * @param key cooldown key ("home", "spawn", "tpa", ...)
     */
    public static boolean schedule(ServerPlayer player, String key, int cooldownSeconds, Supplier<Location> destination, boolean saveBack, Runnable after) {
        SuConfig.Teleport config = SuServer.config().teleport;
        long now = System.currentTimeMillis();
        if (!Perms.has(player, Perm.TELEPORT_BYPASS_COOLDOWN)) {
            long remaining = COOLDOWNS.remaining(player.getUUID(), key, now);
            if (remaining > 0) {
                Msg.error(player, "teleport.cooldown", Durations.format(Math.max(1000, remaining)));
                return false;
            }
        }
        if (isBlockedHere(player, key)) {
            Msg.error(player, "teleport.blocked_here");
            return false;
        }
        int warmup = Perms.has(player, Perm.TELEPORT_BYPASS_WARMUP) ? 0 : config.warmupSeconds;
        Pending pending = new Pending(key, cooldownSeconds * 1000L, destination, locationOf(player), now + warmup * 1000L, saveBack, after);
        if (warmup <= 0) {
            return complete(player, pending);
        }
        PENDING.put(player.getUUID(), pending);
        Msg.info(player, config.cancelOnMove ? "teleport.warmup_still" : "teleport.warmup", warmup);
        return true;
    }

    /** Called every tick. */
    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        SuConfig.Teleport config = SuServer.config().teleport;
        for (Map.Entry<UUID, Pending> entry : PENDING.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Pending pending = entry.getValue();
            if (player == null) {
                PENDING.remove(entry.getKey());
                continue;
            }
            if (config.cancelOnMove && locationOf(player).distanceSquared(pending.start()) > 0.25) {
                PENDING.remove(entry.getKey());
                Msg.error(player, "teleport.cancelled_move");
                continue;
            }
            if (now >= pending.fireAt()) {
                PENDING.remove(entry.getKey());
                complete(player, pending);
            } else {
                long secondsLeft = (pending.fireAt() - now + 999) / 1000;
                player.displayClientMessage(Texts.literal(Lang.tr(player, "teleport.countdown", secondsLeft)), true);
            }
        }
    }

    private static boolean complete(ServerPlayer player, Pending pending) {
        Location target = pending.destination().get();
        if (target == null) {
            Msg.error(player, "teleport.target_gone");
            return false;
        }
        if (!teleport(player, target, pending.saveBack())) {
            return false;
        }
        COOLDOWNS.start(player.getUUID(), pending.key(), pending.cooldownMillis(), System.currentTimeMillis());
        if (pending.after() != null) {
            pending.after().run();
        }
        return true;
    }

    public static void onDamage(ServerPlayer player) {
        if (SuServer.config().teleport.cancelOnDamage && PENDING.remove(player.getUUID()) != null) {
            Msg.error(player, "teleport.cancelled_damage");
        }
    }

    public static void forget(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    private static boolean isBlockedHere(ServerPlayer player, String key) {
        if (Perms.has(player, Perm.TELEPORT_BYPASS_DIMENSION)) {
            return false;
        }
        List<String> blocked = SuServer.config().teleport.blockedInDimensions.get(key);
        return blocked != null && blocked.contains(player.level().dimension().location().toString());
    }

    public static ResourceKey<Level> dimensionKey(String dimension) {
        ResourceLocation id = Ids.parse(dimension);
        return id == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, id);
    }
}
