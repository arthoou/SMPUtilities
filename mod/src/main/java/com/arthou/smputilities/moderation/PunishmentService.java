package com.arthou.smputilities.moderation;

import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.punish.Punishment;
import com.arthou.smputilities.core.punish.PunishmentType;
import com.arthou.smputilities.core.util.Durations;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.voice.VoiceChatCompat;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Date;
import java.util.Optional;

/**
 * Issues and revokes punishments. Bans are mirrored into the vanilla ban
 * list (with expiry for temporary bans), so the vanilla login check enforces
 * them and /pardon keeps working.
 */
public final class PunishmentService {
    private PunishmentService() {
    }

    public static Punishment ban(MinecraftServer server, ServerPlayer actor, Names.Target target, long durationMillis, String reason) {
        SuServer context = SuServer.get();
        SuConfig.Staff config = SuServer.config().staff;
        String why = reason == null || reason.isBlank() ? config.defaultBanReason : reason;
        long now = System.currentTimeMillis();
        Punishment record = context.punishments().issue(PunishmentType.BAN, target.id(), target.name(),
            actor == null ? null : actor.getUUID(), actorName(actor), why, durationMillis, now);

        Date expires = record.isPermanent() ? null : new Date(record.expiresAt);
        Mc.ban(server, target.id(), target.name(), new Date(now), actorName(actor), expires, why);

        ServerPlayer online = server.getPlayerList().getPlayer(target.id());
        if (online != null) {
            online.connection.disconnect(banScreen(online, record));
        }
        context.log(actor, "ban", target.id(), target.name(), (record.isPermanent() ? "permanent" : Durations.format(durationMillis)) + " - " + why);
        announce(server, record.isPermanent() ? "punish.announce.ban" : "punish.announce.tempban", target, record);
        return record;
    }

    public static boolean unban(MinecraftServer server, ServerPlayer actor, Names.Target target, String reason) {
        SuServer context = SuServer.get();
        Optional<Punishment> revoked = context.punishments().revoke(PunishmentType.BAN, target.id(), actorName(actor), reason, System.currentTimeMillis());
        boolean vanilla = Mc.unban(server, target.id(), target.name());
        if (revoked.isPresent() || vanilla) {
            context.log(actor, "unban", target.id(), target.name(), reason == null ? "" : reason);
            return true;
        }
        return false;
    }

    public static void kick(MinecraftServer server, ServerPlayer actor, ServerPlayer target, String reason) {
        SuServer context = SuServer.get();
        String why = reason == null || reason.isBlank() ? SuServer.config().staff.defaultKickReason : reason;
        Punishment record = context.punishments().issue(PunishmentType.KICK, target.getUUID(), Names.realName(target),
            actor == null ? null : actor.getUUID(), actorName(actor), why, 0, System.currentTimeMillis());
        context.log(actor, "kick", target.getUUID(), Names.realName(target), why);
        target.connection.disconnect(Texts.literal(Lang.tr(target, "punish.kicked_screen", why)));
        announce(server, "punish.announce.kick", new Names.Target(target.getUUID(), Names.realName(target), null), record);
    }

    public static Punishment mute(MinecraftServer server, ServerPlayer actor, Names.Target target, long durationMillis, String reason, boolean voice) {
        SuServer context = SuServer.get();
        String why = reason == null || reason.isBlank() ? SuServer.config().staff.defaultMuteReason : reason;
        PunishmentType type = voice ? PunishmentType.VOICE_MUTE : PunishmentType.MUTE;
        Punishment record = context.punishments().issue(type, target.id(), target.name(),
            actor == null ? null : actor.getUUID(), actorName(actor), why, durationMillis, System.currentTimeMillis());
        context.log(actor, voice ? "voice_mute" : "mute", target.id(), target.name(),
            (record.isPermanent() ? "permanent" : Durations.format(durationMillis)) + " - " + why);
        ServerPlayer online = server.getPlayerList().getPlayer(target.id());
        if (online != null) {
            String duration = record.isPermanent() ? Lang.tr(online, "punish.permanent") : Durations.format(durationMillis);
            Msg.error(online, voice ? "punish.voice_muted_you" : "punish.muted_you", duration, why);
            if (voice) {
                VoiceChatCompat.refresh(online);
            }
        }
        announce(server, voice ? "punish.announce.voice_mute" : "punish.announce.mute", target, record);
        return record;
    }

    public static boolean unmute(MinecraftServer server, ServerPlayer actor, Names.Target target, boolean voice) {
        SuServer context = SuServer.get();
        PunishmentType type = voice ? PunishmentType.VOICE_MUTE : PunishmentType.MUTE;
        Optional<Punishment> revoked = context.punishments().revoke(type, target.id(), actorName(actor), "", System.currentTimeMillis());
        if (revoked.isEmpty()) {
            return false;
        }
        context.log(actor, voice ? "voice_unmute" : "unmute", target.id(), target.name(), "");
        ServerPlayer online = server.getPlayerList().getPlayer(target.id());
        if (online != null) {
            Msg.ok(online, voice ? "punish.voice_unmuted_you" : "punish.unmuted_you");
            if (voice) {
                VoiceChatCompat.refresh(online);
            }
        }
        return true;
    }

    public static Punishment warn(MinecraftServer server, ServerPlayer actor, Names.Target target, String reason) {
        SuServer context = SuServer.get();
        Punishment record = context.punishments().issue(PunishmentType.WARN, target.id(), target.name(),
            actor == null ? null : actor.getUUID(), actorName(actor), reason == null ? "" : reason, 0, System.currentTimeMillis());
        context.log(actor, "warn", target.id(), target.name(), reason);
        ServerPlayer online = server.getPlayerList().getPlayer(target.id());
        if (online != null) {
            Mc.send(online, Msg.prefix().append(Texts.literal(Lang.tr(online, "punish.warned_you", reason))
                .withStyle(ChatFormatting.GOLD)));
        }
        return record;
    }

    public static boolean isVoiceMuted(ServerPlayer player) {
        SuServer context = SuServer.get();
        return context != null && context.punishments().active(PunishmentType.VOICE_MUTE, player.getUUID(), System.currentTimeMillis()).isPresent();
    }

    /**
     * On login: a ban lifted outside SMPutilities (vanilla /pardon, ban list
     * edited by hand) also ends SMPutilities' record, so both stay in sync.
     */
    public static void onLogin(ServerPlayer player) {
        SuServer context = SuServer.get();
        if (context == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (context.punishments().active(PunishmentType.BAN, player.getUUID(), now).isPresent()) {
            context.punishments().revoke(PunishmentType.BAN, player.getUUID(), "Server", "lifted outside SMPutilities", now);
        }
    }

    private static Component banScreen(ServerPlayer player, Punishment record) {
        String duration = record.isPermanent() ? Lang.tr(player, "punish.permanent") : Durations.format(record.remaining(System.currentTimeMillis()));
        return Texts.literal(Lang.tr(player, "punish.banned_screen", record.reason, duration));
    }

    private static void announce(MinecraftServer server, String key, Names.Target target, Punishment record) {
        if (!SuServer.config().staff.broadcastPunishments) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean staff = Perms.has(player, Perm.STAFF_HISTORY);
            if (!staff && record.type != com.arthou.smputilities.core.punish.PunishmentType.BAN) {
                continue;
            }
            String name = Names.seesRealNames(player) ? target.name() : Names.plainDisplay(target.id(), target.name());
            String duration = record.isPermanent() ? Lang.tr(player, "punish.permanent") : Durations.format(record.remaining(now));
            // Only staff learn who issued it.
            Msg.info(player, staff ? key : key + ".public", name, record.actorName, duration, record.reason);
        }
    }

    public static String actorName(ServerPlayer actor) {
        return actor == null ? "Console" : actor.getGameProfile().getName();
    }
}
