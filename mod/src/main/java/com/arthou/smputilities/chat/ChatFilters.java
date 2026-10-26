package com.arthou.smputilities.chat;

import com.arthou.smputilities.core.chat.ChatFilter;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** The chat filter as configured in chat.filter, rebuilt when the settings change. */
final class ChatFilters {
    private static ChatFilter filter;
    private static int built;

    private ChatFilters() {
    }

    private static ChatFilter filter() {
        SuConfig.Filter config = SuServer.config().chat.filter;
        boolean censor = !"block".equals(config.mode.toLowerCase(Locale.ROOT));
        int key = Objects.hash(config.words, censor, config.blockAdvertising);
        if (filter == null || key != built) {
            filter = new ChatFilter(config.words == null ? List.of() : config.words, censor, config.blockAdvertising);
            built = key;
        }
        return filter;
    }

    /**
     * Checks a chat line: null when it may be sent as it is, otherwise the
     * result (a censored text to send instead, or blocked).
     */
    static ChatFilter.Result check(ServerPlayer player, String text) {
        if (!SuServer.config().features.chatFilter || Perms.has(player, Perm.CHAT_BYPASS_FILTER)) {
            return null;
        }
        ChatFilter current = filter();
        if (current.isEmpty()) {
            return null;
        }
        ChatFilter.Result result = current.check(text);
        if (result.verdict() == ChatFilter.Verdict.CLEAN) {
            return null;
        }
        if (result.verdict() == ChatFilter.Verdict.BLOCKED) {
            Msg.error(player, "chat.filter.blocked");
        }
        if (SuServer.config().chat.filter.notifyStaff) {
            String key = result.verdict() == ChatFilter.Verdict.BLOCKED ? "chat.filter.staff_blocked" : "chat.filter.staff_censored";
            for (ServerPlayer staff : Mc.server(player).getPlayerList().getPlayers()) {
                if (staff != player && Perms.has(staff, Perm.STAFF_MUTE)) {
                    Mc.send(staff, Msg.prefix().append(Texts.literal(Lang.tr(staff, key, Names.realName(player), text))
                        .withStyle(ChatFormatting.GRAY)));
                }
            }
        }
        return result;
    }
}
