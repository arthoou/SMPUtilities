package com.arthou.smputilities.tts;

import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.chat.ChatService;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.player.PlayerRecord;
import com.arthou.smputilities.network.SuNetwork;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.vanish.VanishService;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Text to speech (DuTTS, integrated). A player with a TTS voice selected
 * chats as usual; the server also sends the text to that player's own client,
 * whose local helper speaks it into a virtual microphone, and shows a speech
 * bubble above their head to everyone nearby with the client mod.
 */
public final class TtsService {
    /** When each player's typing bubble was last relayed. Server thread only. */
    private static final Map<UUID, Long> TYPING = new HashMap<>();

    private TtsService() {
    }

    public static boolean isActive(ServerPlayer player) {
        SuServer context = SuServer.get();
        return context != null && SuServer.config().features.tts && context.players().get(player.getUUID()).ttsMode > 0
            && Perms.has(player, Perm.TTS_USE);
    }

    /**
     * Voices a TTS player's chat line: their own client speaks it into the
     * virtual microphone and nearby players see it as a bubble. The line
     * itself still goes to chat like any other message.
     *
     * @return whether the line was spoken (it then has its bubble)
     */
    public static boolean speak(ServerPlayer player, String rawText) {
        if (!isActive(player)) {
            return false;
        }
        typing(player, false);
        SuConfig.Tts config = SuServer.config().tts;
        String text = rawText.trim();
        if (text.isBlank()) {
            return false;
        }
        if (text.length() > config.maxLength) {
            Msg.error(player, "tts.too_long", config.maxLength);
            return false;
        }
        PlayerRecord record = SuServer.get().players().get(player.getUUID());
        TtsPresets.VoiceLanguage language = language(record);
        String voice = TtsPresets.resolveVoiceName(language, record.ttsMode);
        String pitch = TtsPresets.resolvePitch(record.ttsMode);
        if (!SuNetwork.speak(player, text, voice, pitch)) {
            Msg.error(player, "tts.client_missing");
        }
        if (config.bubbles) {
            sendBubble(player, text, true);
        }
        return true;
    }

    /**
     * NEXEL's typing bubble: while a TTS player (or anyone, with bubbles for
     * all chat) has a message in the chat box, nearby players see "...".
     * Clients repeat it while typing; repeats closer than 400 ms are dropped.
     */
    public static void typing(ServerPlayer player, boolean active) {
        SuConfig config = SuServer.config();
        long now = System.currentTimeMillis();
        if (active) {
            boolean shows = config.tts.bubbles && (isActive(player) || config.tts.bubblesForAllChat) && !ChatService.isSilenced(player);
            Long last = TYPING.get(player.getUUID());
            if (!shows || last != null && now - last < 400) {
                return;
            }
            TYPING.put(player.getUUID(), now);
        } else if (TYPING.remove(player.getUUID()) == null) {
            return;
        }
        for (ServerPlayer viewer : nearbyViewers(player)) {
            SuNetwork.bubbleTyping(viewer, player.getUUID(), active);
        }
    }

    public static void forget(ServerPlayer player) {
        TYPING.remove(player.getUUID());
    }

    /** Players close enough to see the speaker's bubbles, who can see them and do not ignore them. */
    private static List<ServerPlayer> nearbyViewers(ServerPlayer speaker) {
        double range = SuServer.config().tts.bubbleRange * SuServer.config().tts.bubbleRange;
        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer viewer : speaker.serverLevel().players()) {
            if (viewer.distanceToSqr(speaker) <= range && VanishService.canSee(viewer, speaker) && !ChatService.isIgnoring(viewer, speaker)) {
                viewers.add(viewer);
            }
        }
        return viewers;
    }

    /** Speech bubble for every nearby player that has the client mod. */
    public static void sendBubble(ServerPlayer speaker, String text, boolean tts) {
        SuConfig.Tts config = SuServer.config().tts;
        String clipped = text.length() > config.maxLength ? text.substring(0, config.maxLength) : text;
        typing(speaker, false);
        for (ServerPlayer viewer : nearbyViewers(speaker)) {
            SuNetwork.bubble(viewer, speaker.getUUID(), clipped, tts);
        }
    }

    public static TtsPresets.VoiceLanguage language(PlayerRecord record) {
        try {
            return record.ttsLanguage == null || record.ttsLanguage.isEmpty()
                ? TtsPresets.VoiceLanguage.ENGLISH : TtsPresets.VoiceLanguage.valueOf(record.ttsLanguage);
        } catch (IllegalArgumentException e) {
            return TtsPresets.VoiceLanguage.ENGLISH;
        }
    }

    /** From the TTS menu or command. Mode 0 turns TTS off. */
    public static void select(ServerPlayer player, int mode, int languageIndex) {
        SuServer context = SuServer.get();
        if (context == null) {
            return;
        }
        if (mode <= 0) {
            context.players().update(player.getUUID(), record -> record.ttsMode = 0);
            Msg.ok(player, "tts.disabled");
            return;
        }
        if (!Perms.has(player, Perm.TTS_USE)) {
            Msg.error(player, "error.no_permission");
            return;
        }
        TtsPresets.VoiceLanguage[] languages = TtsPresets.VoiceLanguage.values();
        // Only the voices the menu offers; the "duck" modes stay out of reach of crafted packets.
        if (mode > TtsPresets.MODE_MASCULINE_2 || languageIndex < 0 || languageIndex >= languages.length) {
            return;
        }
        TtsPresets.VoiceLanguage language = languages[languageIndex];
        context.players().update(player.getUUID(), record -> {
            record.ttsMode = mode;
            record.ttsLanguage = language.name();
        });
        Mc.send(player, Msg.prefix().append(Texts.translatable("tts.enabled_selection", TtsPresets.selectionComponent(mode, language))));
    }

    public static void openMenu(ServerPlayer player) {
        PlayerRecord record = SuServer.get().players().get(player.getUUID());
        if (!SuNetwork.openTtsMenu(player, record.ttsMode, record.ttsMode > 0 ? language(record).ordinal() : -1)) {
            Msg.error(player, "tts.client_missing");
        }
    }
}
