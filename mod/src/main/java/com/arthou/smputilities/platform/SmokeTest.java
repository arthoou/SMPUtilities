package com.arthou.smputilities.platform;

import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.antixray.XraySelfTest;
import com.arthou.smputilities.core.teleport.Location;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.teleport.Teleports;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * CI check run with SMPUTILITIES_SMOKE=1 on a dedicated server: once the
 * world ticks, it loads every class SMPutilities' mixins change (a mixin that
 * no longer fits its target fails right there), runs a few commands, prints
 * the outcome and stops the server. Never active otherwise.
 *
 * <p>With SMPUTILITIES_SMOKE=client it then waits for tools/client-smoke's
 * bot (a vanilla client, "SmokeBot"): on its "smoke:nether" chat line it
 * builds a netherrack box in the Nether with ores buried inside and on the
 * surface and sends the bot there, so the bot can check what the anti-xray
 * sent; the server stops when the bot leaves.
 */
public final class SmokeTest {
    private static final String MODE = System.getenv("SMPUTILITIES_SMOKE");
    private static final boolean ENABLED = "1".equals(MODE) || "client".equals(MODE);
    private static final boolean CLIENT = "client".equals(MODE);
    private static final String BOT = "SmokeBot";
    /** Ticks to wait for the bot before giving up. */
    private static final int BOT_TIMEOUT = 20 * 240;
    /** Classes changed by mixins on some loader and version (missing ones are skipped). */
    private static final List<String> TARGETS = List.of(
        "net.minecraft.server.level.ServerPlayer",
        "net.minecraft.world.entity.player.Player",
        "net.minecraft.world.entity.Entity",
        "net.minecraft.world.entity.Mob",
        "net.minecraft.world.entity.item.ItemEntity",
        "net.minecraft.world.level.portal.PortalShape",
        "net.minecraft.server.level.ChunkMap",
        "net.minecraft.server.level.ChunkMap$TrackedEntity",
        "net.minecraft.server.network.ServerGamePacketListenerImpl",
        "net.minecraft.server.network.ServerCommonPacketListenerImpl",
        "net.minecraft.network.Connection",
        "net.minecraft.server.players.SleepStatus",
        "net.minecraft.server.players.PlayerList",
        "net.minecraft.server.PlayerAdvancements",
        "net.minecraft.server.MinecraftServer",
        "net.minecraft.world.entity.monster.Creeper",
        "net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData",
        "net.minecraft.world.level.Level",
        "net.minecraft.server.level.ServerPlayerGameMode",
        "net.minecraft.server.level.ServerChunkCache",
        "net.minecraft.server.network.PlayerChunkSender",
        "net.minecraft.world.level.Explosion",
        "net.minecraft.world.level.ServerExplosion");
    /** Run as the console; command errors are logged by the game, which the CI step looks for. */
    private static final List<String> COMMANDS = List.of("su", "su reload", "help", "su reports", "su restart", "su xray", "su log lookup t:1h r:#world", "su maintenance", "su jail list", "rules", "list");
    private static int ticks;

    private SmokeTest() {
    }

    static void tick(MinecraftServer server) {
        if (!ENABLED) {
            return;
        }
        ticks++;
        if (CLIENT && ticks == 40 + BOT_TIMEOUT) {
            SmpUtilities.LOGGER.error("SMPUTILITIES SMOKE FAILED: the client bot did not finish in time");
            Runtime.getRuntime().halt(1);
        }
        if (ticks != 40) {
            return;
        }
        List<String> failures = new ArrayList<>();
        for (String name : TARGETS) {
            try {
                Class.forName(name, true, SmokeTest.class.getClassLoader());
            } catch (ClassNotFoundException missing) {
                // Not on this version.
            } catch (Throwable error) {
                failures.add(name + ": " + error);
                SmpUtilities.LOGGER.error("Smoke test: loading {} failed", name, error);
            }
        }
        for (String command : COMMANDS) {
            try {
                //#if MC>=1.19
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                //#else
                //$$ server.getCommands().performCommand(server.createCommandSourceStack(), command);
                //#endif
            } catch (Throwable error) {
                failures.add("/" + command + ": " + error);
                SmpUtilities.LOGGER.error("Smoke test: /{} failed", command, error);
            }
        }
        try {
            String xray = XraySelfTest.run(server);
            if (xray != null) {
                failures.add("anti-xray: " + xray);
            }
        } catch (Throwable error) {
            failures.add("anti-xray: " + error);
            SmpUtilities.LOGGER.error("Smoke test: anti-xray self test failed", error);
        }
        if (failures.isEmpty() && CLIENT) {
            server.setDifficulty(Difficulty.PEACEFUL, true);
            SmpUtilities.LOGGER.info("SMPUTILITIES SMOKE READY");
        } else if (failures.isEmpty()) {
            SmpUtilities.LOGGER.info("SMPUTILITIES SMOKE OK");
            server.halt(false);
        } else {
            SmpUtilities.LOGGER.error("SMPUTILITIES SMOKE FAILED: {}", failures);
            Runtime.getRuntime().halt(1);
        }
    }

    private static boolean isBot(ServerPlayer player) {
        return CLIENT && BOT.equals(Mc.name(player));
    }

    static void onLogin(ServerPlayer player) {
        if (isBot(player)) {
            player.setGameMode(GameType.CREATIVE);
            SmpUtilities.LOGGER.info("SMPUTILITIES SMOKE client joined");
        }
    }

    static void onLogout(ServerPlayer player) {
        if (!isBot(player)) {
            return;
        }
        MinecraftServer server = Mc.server(player);
        if (!server.isSameThread()) {
            SmpUtilities.LOGGER.error("SMPUTILITIES SMOKE FAILED: logout ran on {}, not the server thread", Thread.currentThread().getName());
            Runtime.getRuntime().halt(1);
        }
        SmpUtilities.LOGGER.info("SMPUTILITIES SMOKE OK (client left)");
        server.halt(false);
    }

    /** @return true for the bot's own requests, which are not chat */
    static boolean chat(ServerPlayer player, String text) {
        if (!isBot(player) || !text.equals("smoke:nether")) {
            return false;
        }
        ServerLevel nether = Mc.server(player).getLevel(Level.NETHER);
        if (nether == null) {
            SmpUtilities.LOGGER.error("SMPUTILITIES SMOKE FAILED: no Nether");
            Runtime.getRuntime().halt(1);
            return true;
        }
        // A netherrack box with a room on top: x and z -3..7, y 96..108, the room at y 103..107.
        BlockState netherrack = Blocks.NETHERRACK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -3; x <= 7; x++) {
            for (int z = -3; z <= 7; z++) {
                for (int y = 96; y <= 108; y++) {
                    boolean room = x >= -2 && x <= 6 && z >= -2 && z <= 6 && y >= 103 && y <= 107;
                    nether.setBlock(new BlockPos(x, y, z), room ? air : netherrack, Block.UPDATE_CLIENTS);
                }
            }
        }
        // Buried: the bot must see netherrack there. On the floor of the room: the real ores.
        nether.setBlock(new BlockPos(2, 100, 2), Blocks.ANCIENT_DEBRIS.defaultBlockState(), Block.UPDATE_CLIENTS);
        nether.setBlock(new BlockPos(4, 99, 4), Blocks.NETHER_QUARTZ_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
        nether.setBlock(new BlockPos(1, 102, 1), Blocks.NETHER_GOLD_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
        nether.setBlock(new BlockPos(4, 102, 4), Blocks.ANCIENT_DEBRIS.defaultBlockState(), Block.UPDATE_CLIENTS);
        Teleports.teleport(player, new Location("minecraft:the_nether", 2.5, 103, 2.5, 0, 0), false);
        SmpUtilities.LOGGER.info("SMPUTILITIES SMOKE client sent to the Nether");
        return true;
    }
}
