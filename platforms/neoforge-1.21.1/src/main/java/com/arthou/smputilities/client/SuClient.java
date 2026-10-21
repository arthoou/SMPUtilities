package com.arthou.smputilities.client;

import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.client.bubble.BubbleManager;
import com.arthou.smputilities.client.bubble.BubbleRenderer;
import com.arthou.smputilities.client.gui.Theme;
import com.arthou.smputilities.client.modsync.ClientModSync;
import com.arthou.smputilities.client.tts.BridgeLauncher;
import com.arthou.smputilities.server.Msg;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Client setup: key binding, HUD indicator, nametags, speech bubbles and TTS. */
public final class SuClient {
    public static final KeyMapping PANEL_KEY = new KeyMapping("key.smputilities.panel", InputConstants.UNKNOWN.getValue(), "key.categories.smputilities");
    private static int ticks;

    private SuClient() {
    }

    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(SuClient::onRegisterKeys);
        modBus.addListener(SuClient::onRegisterLayers);
        NeoForge.EVENT_BUS.addListener(SuClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(SuClient::onNameFormat);
        NeoForge.EVENT_BUS.addListener(SuClient::onRenderPlayer);
        NeoForge.EVENT_BUS.addListener(SuClient::onLoggingOut);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(PANEL_KEY);
    }

    private static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(SmpUtilities.id("vanish_indicator"), (graphics, delta) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.options.hideGui || !ClientState.isVanished(minecraft.player.getUUID())) {
                return;
            }
            String text = Component.translatable("hud.smputilities.vanished").getString();
            int width = minecraft.font.width(text) + 14;
            Theme.roundRect(graphics, 6, 6, 6 + width, 22, Theme.PANEL_BG);
            graphics.fill(10, 12, 14, 16, Theme.ACCENT);
            graphics.drawString(minecraft.font, text, 18, 10, Theme.ACCENT, false);
        });
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (PANEL_KEY.consumeClick()) {
            if (minecraft.player != null && minecraft.getConnection() != null) {
                minecraft.getConnection().sendCommand("su staff");
            }
        }
        BubbleManager.tick();
        ClientModSync.tick();
        if (++ticks % 20 == 0) {
            VoiceMuteBridge.tick();
        }
    }

    /** Draws RP nicknames above heads; staff with real names on see both. */
    private static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!event.getEntity().level().isClientSide()) {
            return;
        }
        String nick = ClientState.nickname(event.getEntity().getUUID());
        if (nick == null || nick.isEmpty()) {
            return;
        }
        MutableComponent name = Msg.styled(nick);
        String real = ClientState.realName(event.getEntity().getUUID());
        if (ClientState.realNames() && real != null) {
            name = Component.empty().append(name).append(Component.literal(" (" + real + ")").withStyle(ChatFormatting.GRAY));
        }
        event.setDisplayname(name);
    }

    private static void onRenderPlayer(RenderPlayerEvent.Post event) {
        BubbleRenderer.render(event.getEntity(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.reset();
        BubbleManager.clear();
        ClientModSync.reset();
        BridgeLauncher.requestShutdownAsync();
    }
}
