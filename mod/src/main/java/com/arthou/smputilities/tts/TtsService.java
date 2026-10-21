package com.arthou.smputilities.tts;

import com.arthou.smputilities.chat.ChatService;
import com.arthou.smputilities.core.chat.Placeholders;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.player.PlayerRecord;
import com.arthou.smputilities.network.SuNetwork;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.vanish.VanishService;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;

/**
 * Text to speech (DuTTS, integrated). A player with a TTS voice selected
 * types in chat; the server sends the text to that player's own client, whose
 * local helper speaks it into a virtual microphone, and shows a speech bubble
 * above their head to everyone nearby with the client mod.
 */
public final class TtsService {
    private TtsService() {
    }

    public static boolean isActive(ServerPlayer player) {
        SuServer context = SuServer.get();
        return context != null && SuServer.config().features.tts && context.players().get(player.getUUID()).ttsMode > 0
            && Perms.has(player, Perm.TTS_USE);
    }

    /** @return true when the chat line was consumed as speech */
    public static boolean handleChat(ServerPlayer player, String rawText) {
        if (!isActive(player)) {
            return false;
        }
        SuConfig.Tts config = SuServer.config().tts;
        String text = rawText.trim();
        if (text.isBlank()) {
            return true;
        }
        if (text.length() > config.maxLength) {
            Msg.error(player, "tts.too_long", config.maxLength);
            return true;
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
        if (config.echoToChat) {
            echo(player, text);
        }
        return true;
    }

    private static void echo(ServerPlayer speaker, String text) {
        MutableComponent line = Msg.styled(SuServer.config().tts.chatMarker)
            .append(Names.display(speaker))
            .append(Msg.styled("&8 » &7" + Placeholders.escapeCodes(text)));
        speaker.server.sendSystemMessage(line);
        for (ServerPlayer recipient : speaker.server.getPlayerList().getPlayers()) {
            if (recipient.getChatVisibility() == ChatVisiblity.FULL && !ChatService.isIgnoring(recipient, speaker)) {
                recipient.sendSystemMessage(line);
            }
        }
    }

    /** Speech bubble for every nearby player that has the client mod. */
    public static void sendBubble(ServerPlayer speaker, String text, boolean tts) {
        SuConfig.Tts config = SuServer.config().tts;
        double range = config.bubbleRange * config.bubbleRange;
        String clipped = text.length() > config.maxLength ? text.substring(0, config.maxLength) : text;
        for (ServerPlayer viewer : speaker.serverLevel().players()) {
            if (viewer.distanceToSqr(speaker) <= range && VanishService.canSee(viewer, speaker) && !ChatService.isIgnoring(viewer, speaker)) {
                SuNetwork.bubble(viewer, speaker.getUUID(), clipped, tts);
            }
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
        player.sendSystemMessage(Msg.prefix().append(Component.translatable("tts.enabled_selection", TtsPresets.selectionComponent(mode, language))));
    }

    public static void openMenu(ServerPlayer player) {
        PlayerRecord record = SuServer.get().players().get(player.getUUID());
        if (!SuNetwork.openTtsMenu(player, record.ttsMode, record.ttsMode > 0 ? language(record).ordinal() : -1)) {
            Msg.error(player, "tts.client_missing");
        }
    }
}
