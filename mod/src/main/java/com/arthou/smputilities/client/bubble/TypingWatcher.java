package com.arthou.smputilities.client.bubble;

import com.arthou.smputilities.client.ClientNetwork;
import com.arthou.smputilities.mixin.client.ChatScreenAccessor;
import com.arthou.smputilities.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * Tells the server while the chat box holds a message (NEXEL's typing
 * bubble), repeating every 650 ms; commands never count. The server decides
 * who sees it.
 */
public final class TypingWatcher {
    private static final long HEARTBEAT_MILLIS = 650;
    private static boolean typing;
    private static long lastSent;

    private TypingWatcher() {
    }

    public static void tick(Minecraft minecraft) {
        boolean now = minecraft.player != null && minecraft.screen instanceof ChatScreen screen && isMessage(screen);
        long time = System.currentTimeMillis();
        if (now && (!typing || time - lastSent >= HEARTBEAT_MILLIS)) {
            send(true, time);
        } else if (!now && typing) {
            send(false, time);
        }
    }

    public static void reset() {
        typing = false;
    }

    private static boolean isMessage(ChatScreen screen) {
        EditBox input = ((ChatScreenAccessor) screen).smputilities$input();
        String value = input == null ? "" : input.getValue().trim();
        return !value.isEmpty() && !value.startsWith("/");
    }

    private static void send(boolean active, long time) {
        typing = active;
        lastSent = time;
        if (ClientNetwork.available()) {
            ClientNetwork.send(new Payloads.Typing(active));
        }
    }
}
