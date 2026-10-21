package com.arthou.smputilities.client;

import com.arthou.smputilities.client.bubble.BubbleManager;
import com.arthou.smputilities.client.modsync.ClientModSync;
import com.arthou.smputilities.client.staff.StaffPanelScreen;
import com.arthou.smputilities.client.tts.TtsMenuScreen;
import com.arthou.smputilities.client.tts.VoiceClient;
import com.arthou.smputilities.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Client-side payload handlers. Only ever called on the client. */
public final class ClientPayloads {
    private ClientPayloads() {
    }

    public static void state(Payloads.State payload) {
        ClientState.apply(payload);
        refreshNames();
    }

    public static void nicknames(Payloads.Nicknames payload) {
        ClientState.apply(payload);
        refreshNames();
    }

    /** Nametags cache the display name, so it is recomputed after every change. */
    static void refreshNames() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            for (Player player : minecraft.level.players()) {
                player.refreshDisplayName();
            }
        }
    }

    public static void openPanel(Payloads.OpenPanel payload) {
        Minecraft.getInstance().setScreen(new StaffPanelScreen(payload.tab(), payload.target()));
    }

    public static void dashboard(Payloads.DashboardData payload) {
        ClientState.dashboard(payload.data());
    }

    public static void detail(Payloads.DetailData payload) {
        ClientState.detail(payload.data());
    }

    public static void table(Payloads.TableData payload) {
        ClientState.table(payload.data());
    }

    public static void settings(Payloads.SettingsData payload) {
        ClientState.settings(payload.settings());
    }

    public static void feedback(Payloads.Feedback payload) {
        if (Minecraft.getInstance().screen instanceof StaffPanelScreen panel) {
            panel.toast(payload.message(), payload.error());
        } else if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.literal(payload.message())
                .withStyle(payload.error() ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.GREEN), false);
        }
    }

    public static void ttsSpeak(Payloads.TtsSpeak payload) {
        VoiceClient.speak(payload.text(), payload.voice(), payload.pitch());
    }

    public static void ttsMenu(Payloads.TtsMenu payload) {
        TtsMenuScreen.open(payload.mode(), payload.language());
    }

    public static void bubble(Payloads.Bubble payload) {
        BubbleManager.add(payload);
    }

    public static void voiceRestriction(Payloads.VoiceRestriction payload) {
        VoiceMuteBridge.setForced(payload.forceMute());
    }

    public static void manifest(Payloads.Manifest payload) {
        ClientModSync.received(payload);
    }
}
