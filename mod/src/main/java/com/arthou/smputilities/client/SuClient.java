package com.arthou.smputilities.client;

import com.arthou.smputilities.client.bubble.BubbleManager;
import com.arthou.smputilities.client.gui.Theme;
import com.arthou.smputilities.client.modsync.ClientModSync;
import com.arthou.smputilities.client.tts.BridgeLauncher;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.server.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

/**
 * Loader-independent client hooks: the panel key, the HUD indicator, nametags,
 * speech bubbles and TTS. Each loader's client glue calls these.
 */
public final class SuClient {
    public static final KeyMapping PANEL_KEY = ClientCompat.keyMapping("key.smputilities.panel", "key.categories.smputilities");
    private static int ticks;

    private SuClient() {
    }

    /** End of every client tick. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        while (PANEL_KEY.consumeClick()) {
            if (minecraft.player != null && minecraft.getConnection() != null) {
                ClientCompat.sendCommand("su staff");
            }
        }
        BubbleManager.tick();
        ClientModSync.tick();
        if (++ticks % 20 == 0) {
            VoiceMuteBridge.tick();
        }
    }

    /** Vanish reminder in the top-left corner of the HUD. */
    public static void renderHud(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !ClientState.isVanished(minecraft.player.getUUID())) {
            return;
        }
        String text = Texts.translatable("hud.smputilities.vanished").getString();
        int width = minecraft.font.width(text) + 14;
        Theme.roundRect(graphics, 6, 6, 6 + width, 22, Theme.PANEL_BG);
        graphics.fill(10, 12, 14, 16, Theme.ACCENT);
        graphics.drawString(minecraft.font, text, 18, 10, Theme.ACCENT, false);
    }

    /** RP nickname above heads; staff with real names on see both. Returns the original when there is none. */
    public static Component nametag(Player player, Component original) {
        String nick = ClientState.nickname(player.getUUID());
        if (nick == null || nick.isEmpty()) {
            return original;
        }
        MutableComponent name = Msg.styled(nick);
        String real = ClientState.realName(player.getUUID());
        if (ClientState.realNames() && real != null) {
            name = Texts.empty().append(name).append(Texts.literal(" (" + real + ")").withStyle(ChatFormatting.GRAY));
        }
        return name;
    }

    public static void loggedOut() {
        ClientState.reset();
        BubbleManager.clear();
        ClientModSync.reset();
        BridgeLauncher.requestShutdownAsync();
    }
}
