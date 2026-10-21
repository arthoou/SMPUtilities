package com.arthou.smputilities.voice;

import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.server.SuServer;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Simple Voice Chat plugin (from DUSMP): voice mutes drop the player's
 * microphone packets on the server and deafened players receive no audio,
 * so it works even if the client ignores the cosmetic mute.
 */
@ForgeVoicechatPlugin
public final class SvcPlugin implements VoicechatPlugin {
    private static volatile VoicechatServerApi serverApi;

    @Override
    public String getPluginId() {
        return SmpUtilities.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> serverApi = event.getVoicechat());
        registration.registerEvent(MicrophonePacketEvent.class, SvcPlugin::onMicrophone);
        registration.registerEvent(EntitySoundPacketEvent.class, SvcPlugin::onSound);
        registration.registerEvent(LocationalSoundPacketEvent.class, SvcPlugin::onSound);
        registration.registerEvent(StaticSoundPacketEvent.class, SvcPlugin::onSound);
    }

    private static void onMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection connection = event.getSenderConnection();
        if (connection == null || connection.getPlayer() == null || SuServer.get() == null) {
            return;
        }
        if (connection.getPlayer().getPlayer() instanceof ServerPlayer player && VoiceChatCompat.isAvailable() && VoiceChatCompat.isMuted(player)) {
            event.cancel();
        }
    }

    private static void onSound(SoundPacketEvent<?> event) {
        VoicechatConnection receiver = event.getReceiverConnection();
        if (receiver == null || receiver.getPlayer() == null || SuServer.get() == null) {
            return;
        }
        if (receiver.getPlayer().getPlayer() instanceof ServerPlayer player && VoiceChatCompat.isAvailable() && VoiceChatCompat.isDeafened(player)) {
            event.cancel();
        }
    }

    static String status(UUID player) {
        VoicechatServerApi api = serverApi;
        if (api == null) {
            return "unavailable";
        }
        VoicechatConnection connection = api.getConnectionOf(player);
        if (connection == null || !connection.isInstalled()) {
            return "not_installed";
        }
        if (!connection.isConnected()) {
            return "disconnected";
        }
        return connection.isDisabled() ? "disabled" : "connected";
    }
}
