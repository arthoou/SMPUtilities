package com.arthou.smputilities.antixray;

import com.arthou.smputilities.core.antixray.Obfuscator;
import com.arthou.smputilities.core.antixray.XrayRules;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.mc.Ids;
import com.arthou.smputilities.mixin.ServerChunkCacheAccessor;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.HashMap;
import java.util.Map;

/**
 * The anti-xray, after DrexHD's AntiXray (MIT): chunk packets show hidden
 * blocks that nothing transparent touches as something else, and the real
 * blocks are sent again around every block that is broken or uncovered.
 *
 * <p>AntiXray adds its preset blocks to the world's own palettes and holds
 * chunk packets back in the connection until a background thread has
 * changed them. Here a packet only remembers its chunk and its loaded
 * neighbours when it is made (on the server thread), and its blocks are
 * hidden when it is written, on the network thread that encodes it: the
 * world's data and the order of packets stay exactly as they are.
 */
public final class AntiXray {
    // Before 1.21.5 paletted containers send the length of their long array.
    //#if MC>=1.21.5
    private static final boolean LENGTH_PREFIX = false;
    //#else
    //$$ private static final boolean LENGTH_PREFIX = true;
    //#endif
    private static final long ERROR_LOG_MILLIS = 60_000;

    private static volatile Map<ResourceKey<Level>, XrayRules> rules = Map.of();
    private static volatile boolean rebuild;
    private static volatile long lastError;
    private static int globalBits = -1;
    /** The player the chunk packet being made is for, when the game says so. */
    private static final ThreadLocal<ServerPlayer> RECIPIENT = new ThreadLocal<>();

    private AntiXray() {
    }

    // ------------------------------------------------------------ settings

    public static void onServerStart(MinecraftServer server) {
        rebuild(server);
        // Tags are bound by now, but build once more on the first tick in case a loader binds them late.
        rebuild = true;
    }

    public static void onServerStop() {
        rules = Map.of();
        XrayAlerts.onServerStop();
    }

    /** Rebuilds the rules on the next tick (after a settings change or a reload). */
    public static void requestRebuild() {
        rebuild = true;
    }

    public static void tick(MinecraftServer server) {
        if (rebuild) {
            rebuild = false;
            rebuild(server);
        }
    }

    private static void rebuild(MinecraftServer server) {
        SuConfig config = SuServer.config();
        XrayAlerts.rebuild(config);
        if (!config.features.antiXray) {
            rules = Map.of();
            return;
        }
        Map<ResourceKey<Level>, XrayRules> built = new HashMap<>();
        for (Map.Entry<String, SuConfig.AntiXray.World> entry : config.antiXray.worlds.entrySet()) {
            SuConfig.AntiXray.World world = entry.getValue();
            ResourceKey<Level> dimension = Ids.dimension(entry.getKey());
            if (world == null || !world.enabled || dimension == null) {
                continue;
            }
            try {
                built.put(dimension, XrayRuleBuilder.build(dimension, world));
            } catch (RuntimeException e) {
                SuLog.warn("Anti-xray settings for " + entry.getKey() + " could not be used: " + e.getMessage());
            }
        }
        rules = Map.copyOf(built);
    }

    public static XrayRules rules(Level level) {
        return level instanceof ServerLevel ? rules.get(level.dimension()) : null;
    }

    public static boolean active(Level level) {
        return rules(level) != null;
    }

    // ------------------------------------------------------- chunk packets

    /** Around making the chunk packets for one player (set by the chunk sending mixins). */
    public static void beginSend(ServerPlayer player) {
        RECIPIENT.set(player);
    }

    public static void endSend() {
        RECIPIENT.remove();
    }

    /** True when the player the packet is being made for may see the real blocks. */
    public static boolean recipientBypasses() {
        ServerPlayer player = RECIPIENT.get();
        return player != null && bypasses(player);
    }

    public static boolean bypasses(ServerPlayer player) {
        return SuServer.config().antiXray.usePermission && Perms.has(player, Perm.ANTIXRAY_BYPASS);
    }

    /** Called when a chunk packet is made: what is needed later to hide its blocks, or null to send it as it is. */
    public static Pending capture(LevelChunk chunk) {
        Level level = chunk.getLevel();
        XrayRules dimensionRules = rules(level);
        if (dimensionRules == null || recipientBypasses()) {
            return null;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        ChunkAccess[] around = new ChunkAccess[4];
        if (serverLevel.getServer().isSameThread()) {
            ChunkPos pos = chunk.getPos();
            around[Obfuscator.WEST] = neighbour(serverLevel, pos.x - 1, pos.z);
            around[Obfuscator.EAST] = neighbour(serverLevel, pos.x + 1, pos.z);
            around[Obfuscator.NORTH] = neighbour(serverLevel, pos.x, pos.z - 1);
            around[Obfuscator.SOUTH] = neighbour(serverLevel, pos.x, pos.z + 1);
        }
        //#if MC>=1.21.3
        int minSection = chunk.getMinSectionY();
        //#else
        //$$ int minSection = chunk.getMinSection();
        //#endif
        return new Pending(dimensionRules, minSection, chunk.getSectionsCount(), around);
    }

    /** The newest state of a loaded chunk, whatever its generation status, as AntiXray takes it. */
    private static ChunkAccess neighbour(ServerLevel level, int x, int z) {
        ChunkHolder holder = ((ServerChunkCacheAccessor) level.getChunkSource()).smputilities$visibleChunk(ChunkPos.asLong(x, z));
        if (holder == null) {
            return null;
        }
        //#if MC>=1.21
        return holder.getLatestChunk();
        //#else
        //$$ return holder.getLastAvailable();
        //#endif
    }

    /** Called when the packet is written: its block data with the hidden blocks replaced. */
    public static byte[] hide(Pending pending, byte[] buffer) {
        try {
            return Obfuscator.rewrite(buffer, pending.minSection, pending.sections, LENGTH_PREFIX, globalBits(), pending.rules, pending);
        } catch (RuntimeException e) {
            long now = System.currentTimeMillis();
            if (now - lastError > ERROR_LOG_MILLIS) {
                lastError = now;
                SuLog.warn("Anti-xray could not change a chunk packet; it was sent unchanged", e);
            }
            return buffer;
        }
    }

    public static boolean lengthPrefix() {
        return LENGTH_PREFIX;
    }

    private static int globalBits() {
        int bits = globalBits;
        if (bits < 0) {
            bits = Mth.ceillog2(Block.BLOCK_STATE_REGISTRY.size());
            globalBits = bits;
        }
        return bits;
    }

    static int id(BlockState state) {
        return Block.BLOCK_STATE_REGISTRY.getId(state);
    }

    /** A chunk packet's chunk layout and its four neighbours, read later on the network thread. */
    public static final class Pending implements Obfuscator.Neighbors {
        final XrayRules rules;
        final int minSection;
        final int sections;
        private final ChunkAccess[] around;

        Pending(XrayRules rules, int minSection, int sections, ChunkAccess[] around) {
            this.rules = rules;
            this.minSection = minSection;
            this.sections = sections;
            this.around = around;
        }

        @Override
        public boolean transparent(int side, int sectionIndex, int x, int y, int z) {
            ChunkAccess chunk = around[side];
            if (chunk == null) {
                return true;
            }
            try {
                LevelChunkSection[] list = chunk.getSections();
                if (sectionIndex >= list.length || list[sectionIndex] == null || list[sectionIndex].hasOnlyAir()) {
                    return true;
                }
                return !rules.solid(id(list[sectionIndex].getBlockState(x, y, z)));
            } catch (RuntimeException e) {
                // The chunk changes on the server thread meanwhile; as AntiXray, treat the block as uncovered.
                return true;
            }
        }
    }

    // --------------------------------------------------------- block changes

    /** Before a block changes: when a solid block gives way, the real blocks around it are sent again. */
    public static void beforeBlockChange(ServerLevel level, BlockPos pos, BlockState newState) {
        XrayRules dimensionRules = rules.get(level.dimension());
        if (dimensionRules == null || pos.getY() > dimensionRules.maxUpdateY()) {
            return;
        }
        BlockState oldState = level.getBlockState(pos);
        if (oldState != newState && dimensionRules.solid(id(oldState)) && !dimensionRules.solid(id(newState))) {
            uncover(level, pos, dimensionRules);
        }
    }

    /** A player started, stopped or finished digging at a block. */
    public static void digging(ServerLevel level, BlockPos pos) {
        XrayRules dimensionRules = rules.get(level.dimension());
        if (dimensionRules != null && pos.getY() <= dimensionRules.maxUpdateY()) {
            uncover(level, pos, dimensionRules);
        }
    }

    private static void uncover(ServerLevel level, BlockPos center, XrayRules dimensionRules) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int[] offset : dimensionRules.updateOffsets()) {
            pos.set(center.getX() + offset[0], center.getY() + offset[1], center.getZ() + offset[2]);
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null && dimensionRules.obfuscates(id(chunk.getBlockState(pos)))) {
                level.getChunkSource().blockChanged(pos.immutable());
            }
        }
    }
}
