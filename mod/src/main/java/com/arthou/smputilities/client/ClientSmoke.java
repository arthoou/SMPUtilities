package com.arthou.smputilities.client;

import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.client.tts.TtsMenuScreen;
import com.arthou.smputilities.network.Payloads;
import net.minecraft.client.Minecraft;

/**
 * CI check run with SMPUTILITIES_SMOKE=modclient on a client running the mod
 * (tools/client-e2e.sh): once joined, the server runs /tts as this player and
 * voices a line for it (see SmokeTest). Passes when the TTS menu opened and
 * stayed open, and the line arrived; then the client quits. Never active otherwise.
 */
public final class ClientSmoke {
    public static final boolean ON = "modclient".equals(System.getenv("SMPUTILITIES_SMOKE"));
    /** Ticks to wait for the server before giving up. */
    private static final int TIMEOUT = 20 * 300;
    private static int ticks;
    private static int menuOpenedAt = -1;
    private static boolean menuStayed;
    private static boolean spoke;
    private static boolean done;

    private ClientSmoke() {
    }

    static void received(Payloads.Message message) {
        if (ON) {
            SmpUtilities.LOGGER.info("SMPUTILITIES CLIENT SMOKE received {}", message.getClass().getSimpleName());
        }
    }

    static void menuOpened() {
        if (ON) {
            menuOpenedAt = ticks;
            SmpUtilities.LOGGER.info("SMPUTILITIES CLIENT SMOKE TTS menu opened");
        }
    }

    /** @return true when the line was taken by the test (no voice app is started) */
    static boolean spoke(String text) {
        if (!ON) {
            return false;
        }
        spoke = true;
        SmpUtilities.LOGGER.info("SMPUTILITIES CLIENT SMOKE TTS line arrived: {}", text);
        return true;
    }

    static void tick(Minecraft minecraft) {
        if (!ON || done) {
            return;
        }
        ticks++;
        // A menu closed right after it opened looks the same as one that never opened.
        if (menuOpenedAt >= 0 && ticks == menuOpenedAt + 20) {
            menuStayed = minecraft.screen instanceof TtsMenuScreen;
            SmpUtilities.LOGGER.info("SMPUTILITIES CLIENT SMOKE TTS menu still open a second later: {} (screen: {})", menuStayed,
                minecraft.screen == null ? "none" : minecraft.screen.getClass().getName());
        }
        if (menuStayed && spoke) {
            done = true;
            SmpUtilities.LOGGER.info("SMPUTILITIES CLIENT SMOKE OK");
            minecraft.stop();
        } else if (ticks == TIMEOUT || (menuOpenedAt >= 0 && ticks > menuOpenedAt + 20 && !menuStayed)) {
            done = true;
            SmpUtilities.LOGGER.error("SMPUTILITIES CLIENT SMOKE FAILED: menu opened {}, stayed {}, line arrived {}", menuOpenedAt >= 0, menuStayed, spoke);
            minecraft.stop();
        }
    }
}
