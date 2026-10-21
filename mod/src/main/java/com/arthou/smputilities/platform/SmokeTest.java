package com.arthou.smputilities.platform;

import com.arthou.smputilities.SmpUtilities;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * CI check run with SMPUTILITIES_SMOKE=1 on a dedicated server: once the
 * world ticks, it loads every class SMPutilities' mixins change (a mixin that
 * no longer fits its target fails right there), runs a few commands, prints
 * the outcome and stops the server. Never active otherwise.
 */
public final class SmokeTest {
    private static final boolean ENABLED = "1".equals(System.getenv("SMPUTILITIES_SMOKE"));
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
        "net.minecraft.server.players.SleepStatus");
    /** Run as the console; command errors are logged by the game, which the CI step looks for. */
    private static final List<String> COMMANDS = List.of("su", "su reload", "help", "su reports", "su restart", "rules");
    private static int ticks;

    private SmokeTest() {
    }

    static void tick(MinecraftServer server) {
        if (!ENABLED || ++ticks != 40) {
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
        if (failures.isEmpty()) {
            SmpUtilities.LOGGER.info("SMPUTILITIES SMOKE OK");
            server.halt(false);
        } else {
            SmpUtilities.LOGGER.error("SMPUTILITIES SMOKE FAILED: {}", failures);
            Runtime.getRuntime().halt(1);
        }
    }
}
