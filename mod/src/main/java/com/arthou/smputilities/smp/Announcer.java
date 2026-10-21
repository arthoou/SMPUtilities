package com.arthou.smputilities.smp;

import com.arthou.smputilities.core.chat.Placeholders;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Sends the configured announcements to everyone, one every few minutes. */
public final class Announcer {
    private static long nextAt;
    private static int index;
    private static int lastPicked = -1;

    private Announcer() {
    }

    public static void onServerStart() {
        nextAt = 0;
        index = 0;
        lastPicked = -1;
    }

    /** Once per second. */
    public static void tick(MinecraftServer server) {
        SuConfig config = SuServer.config();
        List<String> messages = config.announcements.messages;
        long interval = Math.max(0, config.announcements.intervalMinutes) * 60_000L;
        if (!config.features.announcements || messages == null || messages.isEmpty() || interval == 0) {
            nextAt = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (nextAt == 0) {
            nextAt = now + interval;
            return;
        }
        if (now < nextAt) {
            return;
        }
        nextAt = now + interval;
        int online = WelcomeService.visibleOnline(server);
        if (online < Math.max(1, config.announcements.minPlayers)) {
            return;
        }
        String line = messages.get(pick(messages.size(), config.announcements.random));
        String text = Placeholders.apply(config.announcements.prefix + line, Map.of("online", String.valueOf(online)));
        Component component = Msg.linked(text);
        Mc.broadcast(server, component);
    }

    private static int pick(int size, boolean random) {
        if (!random || size == 1) {
            return index++ % size;
        }
        int picked;
        if (lastPicked < 0 || lastPicked >= size) {
            picked = ThreadLocalRandom.current().nextInt(size);
        } else {
            // Never the same line twice in a row.
            picked = ThreadLocalRandom.current().nextInt(size - 1);
            if (picked >= lastPicked) {
                picked++;
            }
        }
        lastPicked = picked;
        return picked;
    }
}
