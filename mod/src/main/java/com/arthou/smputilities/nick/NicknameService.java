package com.arthou.smputilities.nick;

import com.arthou.smputilities.core.chat.Placeholders;
import com.arthou.smputilities.core.chat.StyledText;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.nick.NameAnimation;
import com.arthou.smputilities.core.nick.NicknameRules;
import com.arthou.smputilities.core.player.PlayerRecord;
import com.arthou.smputilities.network.SuNetwork;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RP nicknames, built on the HiddenNames model: a styled name plus a
 * nameplate animation (none, hidden, breathe, rainbow, cycle, random). The
 * server sets the display name used by chat, death messages and the tab
 * list; clients with SMPutilities draw the name and its animation above the
 * player's head, or hide the nameplate. The account name stays the key for
 * everything stored.
 */
public final class NicknameService {
    private NicknameService() {
    }

    /**
     * Validates and stores a nickname.
     *
     * @return null on success, otherwise a translation key describing the problem
     */
    public static String set(MinecraftServer server, UUID target, String nickname, boolean allowColors, ServerPlayer actor) {
        return set(server, target, nickname, allowColors, actor, allowColors);
    }

    /**
     * @param keepCodes whether codes already in {@code nickname} may stay
     * @param allowColors whether the player may add colors of their own (checked on the text after the leading codes)
     */
    private static String set(MinecraftServer server, UUID target, String nickname, boolean keepCodes, ServerPlayer actor, boolean allowColors) {
        SuServer context = SuServer.get();
        SuConfig.Nicknames config = SuServer.config().nicknames;
        String value = nickname.trim();
        if (!config.allowColors) {
            value = StyledText.strip(value);
        } else if (!keepCodes) {
            value = StyledText.strip(value);
        } else if (!allowColors) {
            String leading = NicknameRules.leadingCodes(value);
            value = leading + StyledText.strip(value.substring(leading.length()));
        }
        String problem = NicknameRules.validate(config, value, true);
        if (problem != null) {
            return problem;
        }
        String key = NicknameRules.key(value);
        if (config.uniqueNicknames) {
            for (PlayerRecord record : context.players().all()) {
                if (!target.equals(record.uuid) && record.hasNickname() && NicknameRules.key(record.nickname).equals(key)) {
                    return "nick.taken";
                }
            }
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                if (!online.getUUID().equals(target) && Names.realName(online).equalsIgnoreCase(key)) {
                    return "nick.taken";
                }
            }
        }
        String stored = value;
        String previous = context.players().get(target).nickname;
        context.players().update(target, record -> record.nickname = stored);
        context.log(actor, "nick.set", target, Names.offlineName(server, target).orElse(target.toString()), previous + " -> " + stored);
        apply(server, target);
        return null;
    }

    /** The name a player shows now: the nickname, or the account name (as HiddenNames starts from). */
    private static String currentName(MinecraftServer server, UUID target) {
        String nick = SuServer.get().players().get(target).nickname;
        if (nick != null && !nick.isEmpty()) {
            return nick;
        }
        return Names.offlineName(server, target).orElse("");
    }

    /** HiddenNames' "name": new text, keeping the color and formats the name starts with. */
    public static String setText(MinecraftServer server, UUID target, String text, boolean allowColors, ServerPlayer actor) {
        String leading = NicknameRules.leadingCodes(currentName(server, target));
        return set(server, target, leading + text, true, actor, allowColors);
    }

    /** HiddenNames' "color": the whole name in one color (reset = no color). */
    public static String setColor(MinecraftServer server, UUID target, ChatFormatting color, ServerPlayer actor) {
        String plain = Placeholders.escapeCodes(StyledText.strip(currentName(server, target)));
        String code = color == null || color == ChatFormatting.RESET || !color.isColor() ? "" : "&" + color.getChar();
        return set(server, target, code + plain, true, actor);
    }

    /** The animation in effect: the player's own, or the server default (shown or hidden). */
    public static NameAnimation animation(UUID target) {
        SuServer context = SuServer.get();
        if (context == null) {
            return NameAnimation.NONE;
        }
        NameAnimation own = NameAnimation.parse(context.players().get(target).nameAnimation);
        if (own != null && !context.players().get(target).nameAnimation.isEmpty()) {
            return own;
        }
        return SuServer.config().nicknames.defaultNameplateVisible ? NameAnimation.NONE : NameAnimation.HIDDEN;
    }

    public static void setAnimation(MinecraftServer server, UUID target, NameAnimation animation, ServerPlayer actor) {
        SuServer context = SuServer.get();
        String previous = context.players().get(target).nameAnimation;
        context.players().update(target, record -> record.nameAnimation = animation.id());
        context.log(actor, "nick.animation", target, Names.offlineName(server, target).orElse(target.toString()), previous + " -> " + animation.id());
        apply(server, target);
    }

    /** HiddenNames' "reset": back to the account name and the default nameplate. */
    public static void reset(MinecraftServer server, UUID target, ServerPlayer actor) {
        SuServer context = SuServer.get();
        String previous = context.players().get(target).nickname;
        context.players().update(target, record -> {
            record.nickname = "";
            record.nameAnimation = "";
        });
        context.log(actor, "nick.reset", target, Names.offlineName(server, target).orElse(target.toString()), previous);
        apply(server, target);
    }

    public static void clear(MinecraftServer server, UUID target, ServerPlayer actor) {
        SuServer context = SuServer.get();
        String previous = context.players().get(target).nickname;
        context.players().update(target, record -> record.nickname = "");
        context.log(actor, "nick.clear", target, Names.offlineName(server, target).orElse(target.toString()), previous);
        apply(server, target);
    }

    /** Refreshes everywhere the name is shown. */
    public static void apply(MinecraftServer server, UUID target) {
        ServerPlayer player = server.getPlayerList().getPlayer(target);
        if (player != null) {
            com.arthou.smputilities.platform.Platform.get().refreshNames(player);
        }
        SuNetwork.syncNicknamesToAll(server);
    }

    /** Display name used by chat, death messages and commands. */
    public static Component chatDisplayName(ServerPlayer player, Component original) {
        SuConfig config = SuServer.config();
        if (SuServer.get() == null || !config.features.nicknames || !config.nicknames.showInChat) {
            return original;
        }
        String nick = Names.nickname(player.getUUID());
        if (nick.isEmpty()) {
            return original;
        }
        // Only the name: the game adds team colors and prefixes around it.
        return teamColored(player.getTeam()) ? Texts.literal(StyledText.strip(nick)) : Msg.styled(nick);
    }

    /** Whether the team's color replaces the nickname's own (HiddenNames' team_override). */
    private static boolean teamColored(Team team) {
        return SuServer.config().nicknames.teamColorOverride && team != null && team.getColor() != null && team.getColor().isColor();
    }

    /** Tab list entry. Null keeps the vanilla behaviour. */
    public static Component tabListName(ServerPlayer player) {
        SuConfig config = SuServer.config();
        if (SuServer.get() == null || !config.features.nicknames || !config.nicknames.showInTabList) {
            return null;
        }
        String nick = Names.nickname(player.getUUID());
        if (nick.isEmpty()) {
            return null;
        }
        String shown = teamColored(player.getTeam()) ? Placeholders.escapeCodes(StyledText.strip(nick)) : nick;
        Map<String, String> values = new HashMap<>();
        values.put("nick", shown + "&r");
        values.put("name", config.nicknames.hideRealNames ? shown + "&r" : Placeholders.escapeCodes(Names.realName(player)));
        // With the team's prefix, suffix and color, as the vanilla tab list shows players without a nickname.
        return PlayerTeam.formatNameForTeam(player.getTeam(), Msg.styled(Placeholders.apply(config.nicknames.tabFormat, values)));
    }

    /** Every nickname, for the clients that draw them above heads. */
    public static Map<UUID, String> snapshot() {
        Map<UUID, String> result = new LinkedHashMap<>();
        SuServer context = SuServer.get();
        if (context == null || !SuServer.config().features.nicknames) {
            return result;
        }
        for (PlayerRecord record : context.players().all()) {
            if (record.hasNickname() && record.uuid != null) {
                result.put(record.uuid, record.nickname);
            }
        }
        return result;
    }
}
