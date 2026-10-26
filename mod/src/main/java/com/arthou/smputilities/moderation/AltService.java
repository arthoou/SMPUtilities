package com.arthou.smputilities.moderation;

import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.player.PlayerRecord;
import com.arthou.smputilities.core.punish.PunishmentType;
import com.arthou.smputilities.discord.DiscordBridge;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Alt detection: every player's recent IPs are kept in their record; a
 * player who joins from an IP another account used is reported to staff
 * with smputilities.staff.alts, and can be refused when one of those
 * accounts is banned.
 */
public final class AltService {
    private AltService() {
    }

    private static boolean enabled() {
        return SuServer.config().features.alts;
    }

    /** At login: remembers the IP and reports the accounts that share it. Returns false when the player was refused. */
    public static boolean onLogin(ServerPlayer player) {
        SuServer context = SuServer.get();
        if (context == null || !enabled()) {
            return true;
        }
        String ip = player.getIpAddress();
        if (ip == null || ip.isEmpty() || ip.equals("local") || ip.startsWith("127.")) {
            return true;
        }
        SuConfig.Alts config = SuServer.config().alts;
        context.players().update(player.getUUID(), record -> {
            record.ips.remove(ip);
            record.ips.add(0, ip);
            while (record.ips.size() > Math.max(1, config.maxIps)) {
                record.ips.remove(record.ips.size() - 1);
            }
        });
        Map<UUID, String> alts = sharing(player.getUUID(), Set.of(ip));
        if (alts.isEmpty()) {
            return true;
        }
        long now = System.currentTimeMillis();
        List<String> names = new ArrayList<>();
        boolean bannedAlt = false;
        for (Map.Entry<UUID, String> alt : alts.entrySet()) {
            boolean banned = context.punishments().active(PunishmentType.BAN, alt.getKey(), now).isPresent();
            bannedAlt |= banned;
            names.add(alt.getValue() + (banned ? " (ban)" : ""));
        }
        String list = String.join(", ", names);
        if (config.notifyStaff) {
            for (ServerPlayer staff : Mc.server(player).getPlayerList().getPlayers()) {
                if (staff != player && Perms.has(staff, Perm.STAFF_ALTS)) {
                    Mc.send(staff, Msg.prefix().append(Texts.literal(Lang.tr(staff, "alts.notice", Names.realName(player), list))
                        .withStyle(bannedAlt ? ChatFormatting.RED : ChatFormatting.GOLD)));
                }
            }
            Mc.console(Mc.server(player), Texts.literal(Lang.format(Lang.EN, "alts.notice", Names.realName(player), list)));
            if (DiscordBridge.altAlerts()) {
                DiscordBridge.staff("👥 " + Lang.format(DiscordBridge.language(), "alts.notice", Names.realName(player), list));
            }
        }
        if (bannedAlt && config.blockBannedAlts && !Perms.has(player, Perm.STAFF_ALTS)) {
            player.connection.disconnect(Texts.literal(Lang.tr(player, "alts.refused")));
            return false;
        }
        return true;
    }

    /** Accounts other than {@code self} that used any of these IPs, with their last names. */
    private static Map<UUID, String> sharing(UUID self, Set<String> ips) {
        Map<UUID, String> found = new LinkedHashMap<>();
        SuServer context = SuServer.get();
        if (context == null) {
            return found;
        }
        for (PlayerRecord record : context.players().all()) {
            if (record.uuid == null || record.uuid.equals(self) || record.ips == null) {
                continue;
            }
            for (String ip : record.ips) {
                if (ips.contains(ip)) {
                    found.put(record.uuid, record.lastName.isEmpty() ? record.uuid.toString() : record.lastName);
                    break;
                }
            }
        }
        return found;
    }

    /** Names of the accounts sharing an IP with the player, for the panel ("" when none). */
    public static String altsOf(UUID id) {
        SuServer context = SuServer.get();
        if (context == null || !enabled()) {
            return "";
        }
        PlayerRecord record = context.players().get(id);
        if (record == null || record.ips == null || record.ips.isEmpty()) {
            return "";
        }
        return String.join(", ", sharing(id, new LinkedHashSet<>(record.ips)).values());
    }

    /** /su alts <player>. */
    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("alts")
            .requires(source -> enabled() && Perms.has(source, Perm.STAFF_ALTS))
            .then(Commands.argument("player", StringArgumentType.string()).suggests(Names.knownSuggestions()).executes(AltService::list));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String input = StringArgumentType.getString(context, "player");
        Optional<Names.Target> target = Names.resolve(source.getServer(), input, Mc.player(source));
        SuServer server = SuServer.get();
        if (target.isEmpty() || server == null) {
            return Msg.error(source, "player.not_found", input);
        }
        PlayerRecord record = server.players().get(target.get().id());
        List<String> ips = record == null || record.ips == null ? List.of() : record.ips;
        Msg.info(source, "alts.header", target.get().name(), ips.isEmpty() ? "-" : String.join(", ", ips));
        Map<UUID, String> alts = sharing(target.get().id(), new LinkedHashSet<>(ips));
        if (alts.isEmpty()) {
            return Msg.info(source, "alts.none");
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, String> alt : alts.entrySet()) {
            boolean banned = server.punishments().active(PunishmentType.BAN, alt.getKey(), now).isPresent();
            Mc.reply(source, Texts.literal(" - " + alt.getValue() + (banned ? " (ban)" : "")).withStyle(banned ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
        return 1;
    }
}
