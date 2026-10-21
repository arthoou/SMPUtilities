package com.arthou.smputilities.voice;

import com.arthou.smputilities.moderation.PunishmentService;
import com.arthou.smputilities.network.SuNetwork;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.server.level.ServerPlayer;
import com.arthou.smputilities.platform.Platform;

import java.util.UUID;

/**
 * Optional Simple Voice Chat integration. Nothing in this class touches the
 * voice chat API; {@link SvcPlugin} does, and voice chat only loads it when
 * it is installed.
 */
public final class VoiceChatCompat {
    private static Boolean available;

    private VoiceChatCompat() {
    }

    public static boolean isAvailable() {
        if (available == null) {
            available = Platform.get().isModLoaded("voicechat");
        }
        return available && SuServer.config().features.voiceChat;
    }

    /** "unavailable", "disconnected", "connected" or "disabled" (connected with voice turned off). */
    public static String status(UUID player) {
        if (!isAvailable()) {
            return "unavailable";
        }
        try {
            return SvcPlugin.status(player);
        } catch (LinkageError | RuntimeException e) {
            return "unavailable";
        }
    }

    public static boolean isMuted(ServerPlayer player) {
        return PunishmentService.isVoiceMuted(player);
    }

    public static boolean isDeafened(ServerPlayer player) {
        SuServer context = SuServer.get();
        return context != null && context.players().get(player.getUUID()).voiceDeafened;
    }

    /** Tells a client with SMPutilities to show its own voice chat as muted (purely cosmetic; the server enforces it). */
    public static void refresh(ServerPlayer player) {
        SuNetwork.sendVoiceRestriction(player, isMuted(player));
    }
}
