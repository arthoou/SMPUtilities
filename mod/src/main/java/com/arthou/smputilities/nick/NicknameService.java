package com.arthou.smputilities.nick;

import com.arthou.smputilities.core.chat.Placeholders;
import com.arthou.smputilities.core.chat.StyledText;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.nick.NicknameRules;
import com.arthou.smputilities.core.player.PlayerRecord;
import com.arthou.smputilities.network.SuNetwork;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RP nicknames. The server sets the display name used by chat, death
 * messages and the tab list; clients with SMPutilities also draw it above
 * the player's head. The account name stays the key for everything stored.
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
        SuServer context = SuServer.get();
        SuConfig.Nicknames config = SuServer.config().nicknames;
        String value = nickname.trim();
        if (!allowColors || !config.allowColors) {
            value = StyledText.strip(value);
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
        return nick.isEmpty() ? original : decorate(player, Msg.styled(nick), original);
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
        Map<String, String> values = new HashMap<>();
        values.put("nick", nick + "&r");
        values.put("name", config.nicknames.hideRealNames ? nick + "&r" : Placeholders.escapeCodes(Names.realName(player)));
        return Msg.styled(Placeholders.apply(config.nicknames.tabFormat, values));
    }

    /** Keeps team colors and prefixes, which vanilla applies around the plain name. */
    private static Component decorate(ServerPlayer player, Component nick, Component original) {
        if (player.getTeam() == null) {
            return nick;
        }
        return net.minecraft.world.scores.PlayerTeam.formatNameForTeam(player.getTeam(), nick);
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
