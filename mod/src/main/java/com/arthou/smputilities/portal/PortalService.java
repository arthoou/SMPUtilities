package com.arthou.smputilities.portal;

import com.arthou.smputilities.mc.Ids;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.core.chat.Placeholders;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.portal.PortalRecord;
import com.arthou.smputilities.core.portal.PortalRegistry;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.teleport.Teleports;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Portal limits per dimension. Nether portals (and modded portals that fire
 * NeoForge's portal spawn event) are checked when lit; end portals when the
 * last eye would complete a frame. Registered portals are validated
 * periodically and whenever a portal block breaks, so destroyed portals free
 * their slot again.
 */
public final class PortalService {
    private static final List<Runnable> NEXT_TICK = new ArrayList<>();
    private static long lastValidation;

    private PortalService() {
    }

    private static boolean enabled() {
        return SuServer.config().features.portals && SuServer.get() != null;
    }

    private static PortalRegistry registry() {
        return SuServer.get().portals();
    }

    /** Nether-style portal about to be created. @return true to cancel it */
    public static boolean onPortalSpawn(Level level, BlockPos pos, BlockState state) {
        if (!enabled() || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        SuConfig.Portals config = SuServer.config().portals;
        String block = state.isAir() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) ? "minecraft:nether_portal"
            : Mc.blockId(state.getBlock());
        String key = config.customPortalBlocks.getOrDefault(block, "nether");
        ServerPlayer igniter = nearestPlayer(serverLevel, pos);
        if (!registry().canCreate(config.limits, key) && (igniter == null || !Perms.has(igniter, Perm.PORTAL_BYPASS))) {
            if (igniter != null) {
                sendLimitMessage(igniter, key);
            }
            return true;
        }
        if (registry().near(level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ(), 2).isEmpty()) {
            PortalRecord record = registry().register(key, level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ(),
                block.equals("minecraft:nether_portal") ? "minecraft:nether_portal" : block,
                igniter == null ? null : igniter.getUUID(), igniter == null ? "" : igniter.getGameProfile().getName(), System.currentTimeMillis());
            if (igniter != null) {
                SuServer.get().log(igniter, "portal.create", null, key, "#" + record.id + " " + pos.toShortString());
            }
        }
        return false;
    }

    /** An eye of ender is about to go into a frame. @return true to cancel */
    public static boolean onEyeUse(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
        if (!enabled() || !stack.is(Items.ENDER_EYE)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.END_PORTAL_FRAME) || state.getValue(EndPortalFrameBlock.HAS_EYE)) {
            return false;
        }
        String key = "end";
        String dimension = level.dimension().location().toString();
        boolean known = registry().near(dimension, pos.getX(), pos.getY(), pos.getZ(), 6).isPresent();
        if (!known && !registry().canCreate(SuServer.config().portals.limits, key) && !Perms.has(player, Perm.PORTAL_BYPASS)) {
            sendLimitMessage(player, key);
            return true;
        }
        // Register after vanilla had the chance to complete the frame.
        synchronized (NEXT_TICK) {
            NEXT_TICK.add(() -> registerEndPortalAround(player, level, pos));
        }
        return false;
    }

    private static void registerEndPortalAround(ServerPlayer player, ServerLevel level, BlockPos frame) {
        String dimension = level.dimension().location().toString();
        for (BlockPos pos : BlockPos.betweenClosed(frame.offset(-4, -1, -4), frame.offset(4, 1, 4))) {
            if (level.getBlockState(pos).is(Blocks.END_PORTAL)) {
                if (registry().near(dimension, pos.getX(), pos.getY(), pos.getZ(), 6).isEmpty()) {
                    PortalRecord record = registry().register("end", dimension, pos.getX(), pos.getY(), pos.getZ(), "minecraft:end_portal",
                        player.getUUID(), player.getGameProfile().getName(), System.currentTimeMillis());
                    SuServer.get().log(player, "portal.create", null, "end", "#" + record.id + " " + pos.toShortString());
                }
                return;
            }
        }
    }

    /** A portal block broke: re-check nearby registered portals next tick. */
    public static void onBlockBroken(ServerLevel level, BlockPos pos, BlockState state) {
        if (!enabled() || !(state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.OBSIDIAN)
            || state.is(Blocks.END_PORTAL_FRAME) || isCustomPortalBlock(state.getBlock()))) {
            return;
        }
        synchronized (NEXT_TICK) {
            NEXT_TICK.add(() -> {
                String dimension = level.dimension().location().toString();
                for (PortalRecord record : registry().list(null)) {
                    if (record.isNear(dimension, pos.getX(), pos.getY(), pos.getZ(), 24)) {
                        validate(level.getServer(), record);
                    }
                }
            });
        }
    }

    private static boolean isCustomPortalBlock(Block block) {
        return SuServer.config().portals.customPortalBlocks.containsKey(Mc.blockId(block));
    }

    public static void tick(MinecraftServer server) {
        List<Runnable> tasks;
        synchronized (NEXT_TICK) {
            if (NEXT_TICK.isEmpty()) {
                tasks = List.of();
            } else {
                tasks = new ArrayList<>(NEXT_TICK);
                NEXT_TICK.clear();
            }
        }
        tasks.forEach(Runnable::run);
        if (!enabled()) {
            return;
        }
        int minutes = SuServer.config().portals.validationIntervalMinutes;
        long now = System.currentTimeMillis();
        if (minutes > 0 && now - lastValidation > minutes * 60_000L) {
            lastValidation = now;
            for (PortalRecord record : registry().list(null)) {
                validate(server, record);
            }
        }
    }

    /** Removes the record when its chunk is loaded and no portal block remains near it. */
    private static void validate(MinecraftServer server, PortalRecord record) {
        ServerLevel level = Teleports.level(server, record.dimension);
        if (level == null) {
            return;
        }
        BlockPos center = new BlockPos(record.x, record.y, record.z);
        Block expected = Mc.block(record.block.isEmpty() ? "minecraft:nether_portal" : record.block);
        if (expected == null || !Mc.hasChunk(level, center.getX() >> 4, center.getZ() >> 4)) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -3, -3), center.offset(3, 3, 3))) {
            if (level.getBlockState(pos).is(expected)) {
                return;
            }
        }
        registry().remove(record.id);
    }

    private static ServerPlayer nearestPlayer(ServerLevel level, BlockPos pos) {
        ServerPlayer best = null;
        double bestDistance = 64;
        for (ServerPlayer player : level.players()) {
            double distance = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static void sendLimitMessage(ServerPlayer player, String key) {
        SuConfig.Portals config = SuServer.config().portals;
        Map<String, String> values = new HashMap<>();
        values.put("dimension", key);
        values.put("count", String.valueOf(registry().count(key)));
        values.put("limit", String.valueOf(PortalRegistry.limitFor(config.limits, key)));
        Mc.send(player, Msg.styled(Placeholders.apply(config.limitMessage, values)));
    }
}
