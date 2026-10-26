package com.arthou.smputilities.chat;

import com.arthou.smputilities.mc.Ids;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.core.chat.MentionFinder;
import com.arthou.smputilities.core.chat.Placeholders;
import com.arthou.smputilities.core.chat.SpamGuard;
import com.arthou.smputilities.core.chat.StyledText;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.player.PlayerRecord;
import com.arthou.smputilities.core.punish.Punishment;
import com.arthou.smputilities.core.punish.PunishmentType;
import com.arthou.smputilities.core.util.Durations;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.troll.TrollService;
import com.arthou.smputilities.tts.TtsService;
import com.arthou.smputilities.vanish.VanishService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.ChatVisiblity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Chat moderation and formatting. With the custom format off, accepted
 * messages continue through vanilla untouched (signed, {@code <Name> text});
 * moderation (mute, lock, spam) applies either way.
 */
public final class ChatService {
    private static final SpamGuard SPAM = new SpamGuard();

    private ChatService() {
    }

    // ------------------------------------------------------------------ chat

    /**
     * Runs every check for a chat line.
     *
     * @return true when the event must be cancelled (handled or blocked here)
     */
    public static boolean onChat(ServerPlayer player, String rawText) {
        SuServer context = SuServer.get();
        SuConfig config = SuServer.config();
        if (context == null || !config.features.chat) {
            return false;
        }
        if (!checkCanSpeak(player, rawText, true)) {
            return true;
        }

        String text = SpamGuard.limitCaps(rawText, config.chat.spam.capsRatio);
        if (config.chat.history) {
            context.chatHistory().append(System.currentTimeMillis(), player.getUUID(), Names.realName(player), text);
        }
        // The garbled-chat prank: everyone sees nonsense instead of the line.
        String garbled = TrollService.garble(player, text);
        if (garbled != null) {
            if (config.chat.customFormat) {
                broadcastFormatted(player, garbled, Set.of());
            } else {
                broadcastLine(player, Texts.translatable("chat.type.text", player.getDisplayName(), garbled));
            }
            return true;
        }
        // A TTS player's line is spoken; it reaches the chat only with tts.showInChat.
        boolean tts = config.features.tts && TtsService.isActive(player);
        boolean spoken = tts && TtsService.speak(player, text);
        if (tts && !config.tts.showInChat) {
            return true;
        }
        if (!spoken && config.tts.bubbles && config.tts.bubblesForAllChat) {
            TtsService.sendBubble(player, text, false);
        }

        Set<UUID> mentioned = playMentions(player, text);
        if (!config.chat.customFormat) {
            return false;
        }
        broadcastFormatted(player, text, mentioned);
        return true;
    }

    /** Muted, or the chat is locked for them: such players show no typing bubble. */
    public static boolean isSilenced(ServerPlayer player) {
        SuServer context = SuServer.get();
        if (context == null) {
            return true;
        }
        if (context.punishments().active(PunishmentType.MUTE, player.getUUID(), System.currentTimeMillis()).isPresent()) {
            return true;
        }
        return !SuServer.config().chat.globalChatEnabled && !Perms.has(player, Perm.CHAT_BYPASS_LOCK);
    }

    /**
     * Mute, global lock and spam checks shared by chat and private messages.
     *
     * @param publicChat false for private messages, which have their own lock rule
     */
    public static boolean checkCanSpeak(ServerPlayer player, String text, boolean publicChat) {
        SuServer context = SuServer.get();
        SuConfig config = SuServer.config();
        if (context == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        Optional<Punishment> mute = context.punishments().active(PunishmentType.MUTE, player.getUUID(), now);
        if (mute.isPresent()) {
            Punishment record = mute.get();
            Msg.error(player, record.isPermanent() ? "chat.muted.permanent" : "chat.muted.temporary",
                record.reason.isEmpty() ? "-" : record.reason, Durations.format(record.remaining(now)));
            return false;
        }
        boolean locked = !config.chat.globalChatEnabled && !Perms.has(player, Perm.CHAT_BYPASS_LOCK);
        if (publicChat && locked) {
            Msg.error(player, "chat.locked");
            return false;
        }
        if (!publicChat) {
            if (!config.chat.privateMessagesEnabled && !Perms.has(player, Perm.CHAT_BYPASS_LOCK)) {
                Msg.error(player, "msg.disabled");
                return false;
            }
            if (locked && !config.chat.allowPrivateMessagesWhileLocked) {
                Msg.error(player, "chat.locked");
                return false;
            }
        }
        if (!Perms.has(player, Perm.CHAT_BYPASS_COOLDOWN)) {
            SuConfig.Spam spam = config.chat.spam;
            SpamGuard.Result result = SPAM.check(player.getUUID(), text, new SpamGuard.Settings(
                (long) (config.chat.cooldownSeconds * 1000), spam.enabled, spam.maxMessages,
                (long) (spam.windowSeconds * 1000), (long) (spam.duplicateSeconds * 1000)), now);
            if (!result.allowed()) {
                String key = switch (result.verdict()) {
                    case COOLDOWN -> "chat.cooldown";
                    case DUPLICATE -> "chat.duplicate";
                    default -> "chat.spam";
                };
                Msg.error(player, key, Durations.format(Math.max(1000, result.waitMillis())));
                return false;
            }
        }
        return true;
    }

    private static void broadcastFormatted(ServerPlayer sender, String text, Set<UUID> mentioned) {
        MinecraftServer server = Mc.server(sender);
        SuConfig.Chat config = SuServer.config().chat;
        boolean colors = config.playerColors && Perms.has(sender, Perm.CHAT_COLOR);
        String role = role(sender);
        String messageText = colors ? text : Placeholders.escapeCodes(text);
        if (config.mentions.enabled && config.mentions.highlight && !mentioned.isEmpty()) {
            messageText = highlightMentions(messageText, server, mentioned, config.mentions);
        }

        Map<String, String> values = new HashMap<>();
        values.put("prefix", role);
        values.put("role", role);
        values.put("message", messageText);
        String nameMarker = "\u0000NAME\u0000";
        values.put("name", nameMarker);
        values.put("nick", nameMarker);
        values.put("realname", Placeholders.escapeCodes(Names.realName(sender)));
        String line = Placeholders.apply(config.messageFormat, values);
        if (config.roleColors && !role.isEmpty() && !config.messageFormat.contains("{prefix}") && !config.messageFormat.contains("{role}")) {
            line = role + line;
        }

        MutableComponent publicLine = assemble(line, nameMarker, nameComponent(sender, false));
        MutableComponent staffLine = assemble(line, nameMarker, nameComponent(sender, true));
        Mc.console(server, staffLine);
        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            if (recipient.getChatVisibility() != ChatVisiblity.FULL || isIgnoring(recipient, sender)) {
                continue;
            }
            Mc.send(recipient, showsRealNames(recipient) ? staffLine : publicLine);
        }
    }

    private static void broadcastLine(ServerPlayer sender, Component line) {
        MinecraftServer server = Mc.server(sender);
        Mc.console(server, line);
        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            if (recipient.getChatVisibility() == ChatVisiblity.FULL && !isIgnoring(recipient, sender)) {
                Mc.send(recipient, line);
            }
        }
    }

    /** Splits the formatted line at the name marker so the name keeps its own colors and hover. */
    private static MutableComponent assemble(String line, String marker, Component name) {
        MutableComponent result = Texts.empty();
        int index = line.indexOf(marker);
        if (index < 0) {
            return result.append(Msg.styled(line));
        }
        String before = line.substring(0, index);
        String after = line.substring(index + marker.length());
        StyledText beforeText = StyledText.parse(before);
        result.append(Msg.styled(beforeText));
        MutableComponent named = name.copy();
        if (name.getStyle().getColor() == null && !beforeText.runs().isEmpty()) {
            StyledText.Run last = beforeText.runs().get(beforeText.runs().size() - 1);
            if (last.color() >= 0) {
                named = Texts.empty().withStyle(Style.EMPTY.withColor(last.color())).append(name);
            }
        }
        result.append(named);
        // Codes after the name start from the last color before it, like legacy chat.
        String carry = lastCodes(before);
        result.append(Msg.styled(carry + after));
        return result;
    }

    private static String lastCodes(String text) {
        int index = Math.max(text.lastIndexOf('&'), -1);
        while (index >= 0) {
            if (index + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(index + 1));
                if (Character.digit(code, 16) >= 0) {
                    return "&" + code;
                }
            }
            index = text.lastIndexOf('&', index - 1);
        }
        return "";
    }

    private static Component nameComponent(ServerPlayer player, boolean withRealName) {
        MutableComponent name = Names.display(player);
        String real = Names.realName(player);
        boolean hasNick = !Names.nickname(player.getUUID()).isEmpty();
        if (withRealName && hasNick) {
            name = Texts.empty().append(name).append(Texts.literal(" (" + real + ")").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (withRealName || !hasNick || !SuServer.config().nicknames.hideRealNames) {
            MutableComponent hover = Texts.literal(real).withStyle(ChatFormatting.GRAY);
            name = name.withStyle(style -> style.withHoverEvent(Texts.showText(hover)));
        }
        return name;
    }

    private static boolean showsRealNames(ServerPlayer viewer) {
        SuServer context = SuServer.get();
        return context != null && context.players().get(viewer.getUUID()).realNames && Perms.has(viewer, Perm.STAFF_REALNAMES);
    }

    private static String role(ServerPlayer player) {
        SuConfig.Chat config = SuServer.config().chat;
        if (!config.roleColors) {
            return "";
        }
        if (Perms.has(player, Perm.ADMIN_SETTINGS)) {
            return config.adminPrefix;
        }
        if (Perms.has(player, Perm.STAFF_PANEL)) {
            return config.staffPrefix;
        }
        return "";
    }

    private static String highlightMentions(String text, MinecraftServer server, Set<UUID> mentioned, SuConfig.Mentions config) {
        String result = text;
        for (UUID id : mentioned) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                continue;
            }
            for (String name : mentionNames(player)) {
                List<MentionFinder.Match> matches = MentionFinder.matches(result, name, config.requireAtSign);
                for (int i = matches.size() - 1; i >= 0; i--) {
                    MentionFinder.Match match = matches.get(i);
                    result = result.substring(0, match.start()) + config.highlightColor + result.substring(match.start(), match.end()) + "&r" + result.substring(match.end());
                }
            }
        }
        return result;
    }

    private static List<String> mentionNames(ServerPlayer player) {
        List<String> names = new ArrayList<>();
        names.add(Names.realName(player));
        String nick = Names.nickname(player.getUUID());
        if (!nick.isEmpty()) {
            names.add(StyledText.strip(nick));
        }
        return names;
    }

    /** Plays the mention sound for each mentioned player and returns who was mentioned. */
    private static Set<UUID> playMentions(ServerPlayer sender, String text) {
        SuConfig.Mentions config = SuServer.config().chat.mentions;
        if (!config.enabled) {
            return Set.of();
        }
        List<MentionFinder.Candidate<UUID>> candidates = new ArrayList<>();
        for (ServerPlayer player : Mc.server(sender).getPlayerList().getPlayers()) {
            if (player == sender || !VanishService.canSee(sender, player)) {
                continue;
            }
            List<String> names = new ArrayList<>(mentionNames(player));
            if (!Names.seesRealNames(sender) && names.size() > 1) {
                // Do not let players probe real names through mention sounds.
                names.remove(0);
            }
            candidates.add(new MentionFinder.Candidate<>(player.getUUID(), names));
        }
        Set<UUID> found = MentionFinder.find(text, candidates, config.requireAtSign);
        SuServer context = SuServer.get();
        for (UUID id : found) {
            ServerPlayer target = Mc.server(sender).getPlayerList().getPlayer(id);
            if (target == null || context == null || isIgnoring(target, sender)) {
                continue;
            }
            PlayerRecord record = context.players().get(id);
            if (!record.mentionSound) {
                continue;
            }
            String soundId = record.mentionSoundId == null || record.mentionSoundId.isEmpty() ? config.sound : record.mentionSoundId;
            float volume = record.mentionVolume >= 0 ? record.mentionVolume : config.volume;
            playSound(target, soundId, volume, config.pitch);
        }
        return found;
    }

    public static void playSound(ServerPlayer player, String soundId, float volume, float pitch) {
        Mc.playSound(player, soundId, SoundSource.PLAYERS, volume, pitch);
    }

    public static boolean isIgnoring(ServerPlayer recipient, ServerPlayer sender) {
        SuServer context = SuServer.get();
        if (context == null || recipient == sender || Perms.has(sender, Perm.STAFF_MUTE)) {
            return false;
        }
        return context.players().get(recipient.getUUID()).ignored.contains(sender.getUUID());
    }

    public static void forget(ServerPlayer player) {
        SPAM.forget(player.getUUID());
    }

    // -------------------------------------------------------------- presence

    /** Sends SMPutilities' join or quit line (vanilla style or custom), respecting every toggle. */
    public static void broadcastPresence(ServerPlayer player, boolean join, boolean fake) {
        SuServer context = SuServer.get();
        SuConfig config = SuServer.config();
        if (context == null || !PresenceFilter.managed()) {
            return;
        }
        PlayerRecord record = context.players().get(player.getUUID());
        boolean enabled = join ? config.chat.joinMessages && !record.silentJoin : config.chat.quitMessages && !record.silentQuit;
        boolean hidden = !fake && VanishService.isVanished(player.getUUID()) && config.vanish.silentJoinQuit;
        if (!enabled && !fake) {
            return;
        }

        Component publicLine = presenceLine(player, join, false);
        Component staffLine = presenceLine(player, join, true);
        Mc.console(context.server(), staffLine);
        for (ServerPlayer recipient : context.server().getPlayerList().getPlayers()) {
            if (recipient == player && !join) {
                continue;
            }
            if (hidden) {
                if (recipient != player && Perms.has(recipient, Perm.STAFF_VANISH_SEE)) {
                    Mc.send(recipient, Texts.empty()
                        .append(Texts.literal(Lang.tr(recipient, "vanish.presence_tag") + " ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(staffLine));
                }
                continue;
            }
            Mc.send(recipient, showsRealNames(recipient) ? staffLine : publicLine);
        }
    }

    private static Component presenceLine(ServerPlayer player, boolean join, boolean withRealName) {
        SuConfig.Chat config = SuServer.config().chat;
        Component name = nameComponent(player, withRealName);
        if (!config.customFormat) {
            // Wrapped so PresenceFilter lets it through.
            return Texts.empty().append(Texts.translatable(join ? "multiplayer.player.joined" : "multiplayer.player.left", name)
                .withStyle(ChatFormatting.YELLOW));
        }
        String marker = "\u0000NAME\u0000";
        Map<String, String> values = new HashMap<>();
        values.put("name", marker);
        values.put("nick", marker);
        values.put("realname", Placeholders.escapeCodes(Names.realName(player)));
        String line = Placeholders.apply(join ? config.joinFormat : config.quitFormat, values);
        return assemble(line, marker, name);
    }

    /** "/su say": a server announcement using the server prefix. */
    public static void serverAnnouncement(MinecraftServer server, String text) {
        SuConfig.Chat config = SuServer.config().chat;
        Component line = Msg.styled(config.serverPrefix + text);
        Mc.broadcast(server, line);
    }
}
