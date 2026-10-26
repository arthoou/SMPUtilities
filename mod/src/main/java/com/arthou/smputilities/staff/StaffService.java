package com.arthou.smputilities.staff;

import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.antixray.AntiXray;
import com.arthou.smputilities.antixray.XrayAlerts;
import com.arthou.smputilities.backup.BackupMenus;
import com.arthou.smputilities.backup.BackupService;
import com.arthou.smputilities.backup.PlayerSnapshot;
import com.arthou.smputilities.backup.SnapshotType;
import com.arthou.smputilities.chat.ChatService;
import com.arthou.smputilities.core.audit.AuditEntry;
import com.arthou.smputilities.core.chat.ChatHistory;
import com.arthou.smputilities.core.chat.StyledText;
import com.arthou.smputilities.core.config.ConfigPaths;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.dimension.DimensionRules;
import com.arthou.smputilities.core.nick.NameAnimation;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.player.PlayerRecord;
import com.arthou.smputilities.core.portal.PortalRecord;
import com.arthou.smputilities.core.portal.PortalRegistry;
import com.arthou.smputilities.core.punish.Punishment;
import com.arthou.smputilities.core.punish.PunishmentType;
import com.arthou.smputilities.core.report.Report;
import com.arthou.smputilities.core.util.Durations;
import com.arthou.smputilities.inventory.InventoryViews;
import com.arthou.smputilities.inventory.PlayerState;
import com.arthou.smputilities.moderation.PunishmentService;
import com.arthou.smputilities.modsync.ModSyncServer;
import com.arthou.smputilities.network.Dto;
import com.arthou.smputilities.network.Payloads;
import com.arthou.smputilities.network.SuNetwork;
import com.arthou.smputilities.nick.NicknameService;
import com.arthou.smputilities.profile.ProfileMenus;
import com.arthou.smputilities.profile.ProfileService;
import com.arthou.smputilities.profile.ProfileStore;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.smp.AfkService;
import com.arthou.smputilities.smp.ReportService;
import com.arthou.smputilities.stats.PlayerStats;
import com.arthou.smputilities.teleport.Teleports;
import com.arthou.smputilities.troll.TrollService;
import com.arthou.smputilities.vanish.VanishService;
import com.arthou.smputilities.voice.VoiceChatCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of the staff panel. The client only asks; every answer and
 * every action is checked against the acting player's permissions here.
 */
public final class StaffService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int CHAT_PAGE = 100;
    private static final Map<UUID, GameType> SPECTATE_RETURN = new ConcurrentHashMap<>();
    private static long startedAt = System.currentTimeMillis();

    private StaffService() {
    }

    public static void onServerStart() {
        startedAt = System.currentTimeMillis();
        SPECTATE_RETURN.clear();
    }

    /** /su staff: opens the panel on clients with the mod. */
    public static boolean openPanel(ServerPlayer player, String tab, String target) {
        if (!SuServer.config().features.staffPanel) {
            return false;
        }
        SuNetwork.syncState(player);
        SuNetwork.syncNicknames(player);
        return SuNetwork.send(player, new Payloads.OpenPanel(tab, target == null ? "" : target));
    }

    // =========================================================== requests

    public static void handleRequest(ServerPlayer player, Payloads.Request request) {
        if (!SuServer.config().features.staffPanel || !Perms.has(player, Perm.STAFF_PANEL) || SuServer.get() == null) {
            return;
        }
        switch (request.kind()) {
            case "dashboard" -> SuNetwork.send(player, new Payloads.DashboardData(dashboard(player)));
            case "player" -> parseUuid(request.argument()).ifPresent(id -> sendDetail(player, id));
            case "table" -> {
                String[] parts = request.argument().split("\\|", 2);
                if (parts[0].equals("chatlog")) {
                    chatLog(player, parts.length > 1 ? parts[1] : "");
                    return;
                }
                Dto.Table table = table(player, parts[0], parts.length > 1 ? parts[1] : "");
                if (table != null) {
                    SuNetwork.send(player, new Payloads.TableData(table));
                }
            }
            case "settings" -> {
                if (Perms.has(player, Perm.ADMIN_SETTINGS)) {
                    SuNetwork.send(player, new Payloads.SettingsData(settings()));
                }
            }
            case "state" -> {
                SuNetwork.syncState(player);
                SuNetwork.syncNicknames(player);
            }
            default -> {
            }
        }
    }

    public static Dto.Dashboard dashboard(ServerPlayer viewer) {
        SuServer context = SuServer.get();
        MinecraftServer server = context.server();
        double mspt = Mc.millisPerTick(server);
        double tps = Math.min(Mc.targetTickRate(server), mspt <= 0 ? 20.0 : 1000.0 / mspt);
        Runtime runtime = Runtime.getRuntime();
        long now = System.currentTimeMillis();
        List<Dto.PlayerRow> rows = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!VanishService.canSee(viewer, player)) {
                continue;
            }
            rows.add(new Dto.PlayerRow(player.getUUID(), Names.realName(player), Names.nickname(player.getUUID()),
                Mc.latency(player), player.level().dimension().location().toString(),
                player.gameMode.getGameModeForPlayer().getName(), player.getHealth(), VanishService.isVanished(player.getUUID()),
                context.punishments().active(PunishmentType.MUTE, player.getUUID(), now).isPresent(),
                Perms.has(player, Perm.STAFF_PANEL)));
        }
        SuConfig config = SuServer.config();
        Map<String, String> flags = new LinkedHashMap<>();
        if (Perms.has(viewer, Perm.CHAT_MANAGE)) {
            // Chat managers edit these without access to the full settings list.
            for (ConfigPaths.Entry entry : ConfigPaths.list(config)) {
                if (entry.path().startsWith("chat.")) {
                    flags.put(entry.path(), String.valueOf(entry.value()));
                }
            }
        } else {
            flags.put("chat.globalChatEnabled", String.valueOf(config.chat.globalChatEnabled));
        }
        flags.put("voice", String.valueOf(VoiceChatCompat.isAvailable()));
        flags.put("modsync.hosted", String.valueOf(ModSyncServer.hostedCount()));
        flags.put("realNames", String.valueOf(context.players().get(viewer.getUUID()).realNames));
        flags.put("vanished", String.valueOf(VanishService.isVanished(viewer.getUUID())));
        return new Dto.Dashboard(server.getPlayerCount(), server.getPlayerList().getMaxPlayers(), tps, mspt,
            runtime.totalMemory() - runtime.freeMemory(), runtime.maxMemory(), now - startedAt, server.getServerVersion(),
            context.punishments().allActive(PunishmentType.BAN, now).size(), context.punishments().allActive(PunishmentType.MUTE, now).size(),
            flags, rows);
    }

    public static void sendDetail(ServerPlayer viewer, UUID id) {
        Dto.PlayerDetail detail = detail(viewer, id);
        if (detail != null) {
            SuNetwork.send(viewer, new Payloads.DetailData(detail));
        }
    }

    public static Dto.PlayerDetail detail(ServerPlayer viewer, UUID id) {
        SuServer context = SuServer.get();
        MinecraftServer server = context.server();
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null && !VanishService.canSee(viewer, online)) {
            online = null;
        }
        PlayerRecord record = context.players().get(id);
        String name = online != null ? Names.realName(online) : Names.offlineName(server, id).orElse(record.lastName);
        long now = System.currentTimeMillis();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("nick", record.nickname);
        values.put("nameAnimation", NicknameService.animation(id).id());
        values.put("online", String.valueOf(online != null));
        if (online != null) {
            values.put("dimension", online.level().dimension().location().toString());
            values.put("x", String.valueOf(online.getBlockX()));
            values.put("y", String.valueOf(online.getBlockY()));
            values.put("z", String.valueOf(online.getBlockZ()));
            values.put("health", String.format(Locale.ROOT, "%.1f", online.getHealth()));
            values.put("maxHealth", String.format(Locale.ROOT, "%.1f", online.getMaxHealth()));
            values.put("food", String.valueOf(online.getFoodData().getFoodLevel()));
            values.put("saturation", String.format(Locale.ROOT, "%.1f", online.getFoodData().getSaturationLevel()));
            values.put("gamemode", online.gameMode.getGameModeForPlayer().getName());
            values.put("ping", String.valueOf(Mc.latency(online)));
            values.put("xpLevel", String.valueOf(online.experienceLevel));
            values.put("op", String.valueOf(Perms.opLevel(online)));
        } else if (record.logout != null) {
            values.put("dimension", record.logout.dimension());
            values.put("x", String.valueOf((int) Math.floor(record.logout.x())));
            values.put("y", String.valueOf((int) Math.floor(record.logout.y())));
            values.put("z", String.valueOf((int) Math.floor(record.logout.z())));
        }
        PlayerStats.Values stats = PlayerStats.read(server, id);
        values.put("deaths", String.valueOf(stats.deaths()));
        values.put("playerKills", String.valueOf(stats.playerKills()));
        values.put("mobKills", String.valueOf(stats.mobKills()));
        values.put("playTime", Durations.formatTicks(stats.playTicks()));
        values.put("firstJoin", record.firstJoin > 0 ? TIME.format(Instant.ofEpochMilli(record.firstJoin)) : "-");
        values.put("lastSeen", online != null ? "online" : record.lastSeen > 0 ? TIME.format(Instant.ofEpochMilli(record.lastSeen)) : "-");
        values.put("joins", String.valueOf(record.joinCount));
        values.put("ban", punishmentText(context.punishments().active(PunishmentType.BAN, id, now), now));
        values.put("mute", punishmentText(context.punishments().active(PunishmentType.MUTE, id, now), now));
        values.put("voiceMute", punishmentText(context.punishments().active(PunishmentType.VOICE_MUTE, id, now), now));
        values.put("voiceStatus", VoiceChatCompat.status(id));
        values.put("voiceDeafened", String.valueOf(record.voiceDeafened));
        values.put("vanished", String.valueOf(VanishService.isVanished(id)));
        values.put("afk", String.valueOf(online != null && AfkService.isAfk(online)));
        values.put("reports", String.valueOf(context.reports().openAgainst(id)));
        values.put("silentJoin", String.valueOf(record.silentJoin));
        values.put("silentQuit", String.valueOf(record.silentQuit));
        values.put("warnings", String.valueOf(context.punishments().history(id).stream().filter(p -> p.type == PunishmentType.WARN).count()));
        values.put("records", String.valueOf(context.punishments().history(id).size()));
        values.put("backups", String.valueOf(BackupService.list(id).size()));
        ProfileStore.PlayerProfiles profiles = ProfileService.of(id);
        values.put("activeProfile", profiles.active);
        List<String> notes = new ArrayList<>();
        for (PlayerRecord.StaffNote note : record.notes) {
            notes.add(TIME.format(Instant.ofEpochMilli(note.time)) + " · " + note.author + ": " + note.text);
        }
        return new Dto.PlayerDetail(id, name, online != null, values, notes, new LinkedHashMap<>(record.customStats), profiles.names());
    }

    private static String punishmentText(Optional<Punishment> punishment, long now) {
        if (punishment.isEmpty()) {
            return "";
        }
        Punishment record = punishment.get();
        return (record.isPermanent() ? "perm" : Durations.format(record.remaining(now))) + "|" + record.actorName + "|" + record.reason;
    }

    // ============================================================== tables

    /**
     * A page of a day's chat ({@code context} "day|before"), newest first.
     * It is read from disk on the history's own thread and sent from the
     * server thread, so the tick never waits on the file.
     */
    private static void chatLog(ServerPlayer viewer, String context) {
        SuServer server = SuServer.get();
        if (!Perms.has(viewer, Perm.CHAT_HISTORY)) {
            return;
        }
        String[] parts = context.split("\\|", 2);
        String day = parts[0].isEmpty() ? server.chatHistory().today() : parts[0];
        long before = -1;
        try {
            before = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : -1;
        } catch (NumberFormatException ignored) {
            // newest page
        }
        MinecraftServer minecraft = Mc.server(viewer);
        server.chatHistory().readAsync(day, before, CHAT_PAGE, lines -> minecraft.execute(() -> {
            List<Dto.Row> rows = new ArrayList<>();
            for (ChatHistory.Line line : lines) {
                rows.add(new Dto.Row(String.valueOf(line.offset()), List.of(CLOCK.format(Instant.ofEpochMilli(line.time())), line.name(),
                    line.text(), line.sender())));
            }
            if (!viewer.hasDisconnected()) {
                SuNetwork.send(viewer, new Payloads.TableData(new Dto.Table("chatlog", context, List.of("time", "player", "message", "uuid"), rows)));
            }
        }));
    }

    public static Dto.Table table(ServerPlayer viewer, String kind, String context) {
        SuServer server = SuServer.get();
        long now = System.currentTimeMillis();
        List<Dto.Row> rows = new ArrayList<>();
        switch (kind) {
            case "history" -> {
                if (!Perms.has(viewer, Perm.STAFF_HISTORY)) {
                    return null;
                }
                Optional<UUID> id = parseUuid(context);
                List<Punishment> records = id.isPresent() ? server.punishments().history(id.get()) : server.punishments().recent(300);
                for (Punishment record : records) {
                    String status = record.revokedAt > 0 ? "revoked:" + record.revokedBy : record.isActive(now)
                        ? (record.isPermanent() ? "active" : "active:" + Durations.format(record.remaining(now))) : record.type.lasting() ? "expired" : "-";
                    rows.add(new Dto.Row(record.id, List.of(record.id, record.type.name(), record.targetName, TIME.format(Instant.ofEpochMilli(record.createdAt)),
                        record.actorName, record.reason, status)));
                }
                return new Dto.Table(kind, context, List.of("id", "type", "player", "date", "by", "reason", "status"), rows);
            }
            case "bans", "mutes" -> {
                if (!Perms.has(viewer, Perm.STAFF_HISTORY)) {
                    return null;
                }
                List<Punishment> active = new ArrayList<>();
                if (kind.equals("bans")) {
                    active.addAll(server.punishments().allActive(PunishmentType.BAN, now));
                } else {
                    active.addAll(server.punishments().allActive(PunishmentType.MUTE, now));
                    active.addAll(server.punishments().allActive(PunishmentType.VOICE_MUTE, now));
                }
                for (Punishment record : active) {
                    rows.add(new Dto.Row(record.target.toString(), List.of(record.targetName, record.type.name(),
                        TIME.format(Instant.ofEpochMilli(record.createdAt)), record.isPermanent() ? "∞" : Durations.format(record.remaining(now)),
                        record.actorName, record.reason)));
                }
                return new Dto.Table(kind, context, List.of("player", "type", "since", "remaining", "by", "reason"), rows);
            }
            case "reports" -> {
                if (!Perms.has(viewer, Perm.STAFF_REPORTS)) {
                    return null;
                }
                for (Report report : server.reports().open()) {
                    rows.add(new Dto.Row(String.valueOf(report.id), List.of("#" + report.id, TIME.format(Instant.ofEpochMilli(report.createdAt)),
                        report.reporterName, report.targetName, report.reason, report.where == null ? "" : report.where.shortText())));
                }
                return new Dto.Table(kind, context, List.of("id", "date", "reporter", "player", "reason", "where"), rows);
            }
            case "xray" -> {
                if (!Perms.has(viewer, Perm.XRAY_ALERTS)) {
                    return null;
                }
                for (XrayAlerts.Alert alert : XrayAlerts.recent()) {
                    rows.add(new Dto.Row(String.valueOf(alert.id()), List.of("#" + alert.id(), TIME.format(Instant.ofEpochMilli(alert.time())),
                        alert.name(), alert.ore(), (alert.suspicious() ? "⚠ " : "") + alert.veins(),
                        alert.pos().getX() + " " + alert.pos().getY() + " " + alert.pos().getZ() + " · " + alert.dimension())));
                }
                return new Dto.Table(kind, context, List.of("id", "date", "player", "ore", "veins", "where"), rows);
            }
            case "chatdays" -> {
                if (!Perms.has(viewer, Perm.CHAT_HISTORY)) {
                    return null;
                }
                String today = server.chatHistory().today();
                List<String> days = server.chatHistory().days(90);
                if (!days.contains(today)) {
                    days.add(0, today);
                }
                for (String day : days) {
                    rows.add(new Dto.Row(day, List.of(day)));
                }
                return new Dto.Table(kind, context, List.of("date"), rows);
            }
            case "logs" -> {
                if (!Perms.has(viewer, Perm.STAFF_LOGS)) {
                    return null;
                }
                for (AuditEntry entry : server.audit().recent(300, parseUuid(context).orElse(null))) {
                    rows.add(new Dto.Row(String.valueOf(entry.time), List.of(TIME.format(Instant.ofEpochMilli(entry.time)), entry.actorName,
                        entry.action, entry.targetName, entry.details)));
                }
                return new Dto.Table(kind, context, List.of("date", "by", "action", "target", "details"), rows);
            }
            case "backups" -> {
                if (!Perms.has(viewer, Perm.STAFF_INVENTORY)) {
                    return null;
                }
                Optional<UUID> id = parseUuid(context);
                if (id.isEmpty()) {
                    return null;
                }
                int index = 1;
                for (PlayerSnapshot snapshot : BackupService.list(id.get())) {
                    rows.add(new Dto.Row(snapshot.id(), List.of("#" + index++, snapshot.id(), Lang.tr(viewer, snapshot.type().langKey()),
                        TIME.format(Instant.ofEpochMilli(snapshot.timestamp())), snapshot.detail())));
                }
                return new Dto.Table(kind, context, List.of("index", "id", "type", "date", "detail"), rows);
            }
            case "profiles" -> {
                if (!Perms.has(viewer, Perm.STAFF_PROFILES)) {
                    return null;
                }
                Optional<UUID> id = parseUuid(context);
                if (id.isEmpty()) {
                    return null;
                }
                ProfileStore.PlayerProfiles profiles = ProfileService.of(id.get());
                for (String name : profiles.names()) {
                    ProfileStore.Profile profile = profiles.profiles.get(name);
                    rows.add(new Dto.Row(name, List.of(name, String.valueOf(profiles.active.equals(name)),
                        profile == null || profile.updated == 0 ? "-" : TIME.format(Instant.ofEpochMilli(profile.updated)),
                        profile == null ? "" : profile.nick)));
                }
                return new Dto.Table(kind, context, List.of("name", "active", "updated", "nick"), rows);
            }
            case "portals" -> {
                SuConfig.Portals config = SuServer.config().portals;
                for (PortalRecord portal : server.portals().list(null)) {
                    rows.add(new Dto.Row(portal.id, List.of("#" + portal.id, portal.key, portal.dimension,
                        portal.x + " " + portal.y + " " + portal.z, portal.creatorName)));
                }
                List<String> limits = new ArrayList<>();
                for (String key : List.of("nether", "end", "aether")) {
                    limits.add(key + "=" + server.portals().count(key) + "/" + limitText(PortalRegistry.limitFor(config.limits, key)));
                }
                config.limits.forEach((key, value) -> {
                    if (!List.of("nether", "end", "aether").contains(key)) {
                        limits.add(key + "=" + server.portals().count(key) + "/" + limitText(value));
                    }
                });
                return new Dto.Table(kind, String.join(",", limits), List.of("id", "type", "dimension", "position", "creator"), rows);
            }
            case "dimensions" -> {
                SuConfig.Dimensions config = SuServer.config().dimensions;
                for (ServerLevel level : server.server().getAllLevels()) {
                    String id = level.dimension().location().toString();
                    rows.add(new Dto.Row(id, List.of(DimensionRules.shortName(config, id), id,
                        String.valueOf(!DimensionRules.isBlocked(config, id)), String.valueOf(level.players().size()))));
                }
                return new Dto.Table(kind, context, List.of("name", "id", "open", "players"), rows);
            }
            case "warps" -> {
                server.teleports().warps().forEach((name, location) -> rows.add(new Dto.Row(name, List.of(name, location.shortText()))));
                return new Dto.Table(kind, context, List.of("name", "location"), rows);
            }
            case "known" -> {
                for (PlayerRecord record : server.players().all()) {
                    if (record.uuid == null || record.lastName.isEmpty()) {
                        continue;
                    }
                    boolean online = server.server().getPlayerList().getPlayer(record.uuid) != null;
                    rows.add(new Dto.Row(record.uuid.toString(), List.of(record.lastName, StyledText.strip(record.nickname),
                        online ? "online" : record.lastSeen > 0 ? TIME.format(Instant.ofEpochMilli(record.lastSeen)) : "-")));
                }
                return new Dto.Table(kind, context, List.of("name", "nick", "lastSeen"), rows);
            }
            default -> {
                return null;
            }
        }
    }

    private static String limitText(int limit) {
        return limit < 0 ? "∞" : String.valueOf(limit);
    }

    public static List<Dto.Setting> settings() {
        List<Dto.Setting> result = new ArrayList<>();
        for (ConfigPaths.Entry entry : ConfigPaths.list(SuServer.config())) {
            if (entry.path().equals("configVersion")) {
                continue;
            }
            result.add(new Dto.Setting(entry.path(), entry.kind().name(), String.valueOf(entry.value())));
        }
        return result;
    }

    // ============================================================= actions

    public static void handleAction(ServerPlayer actor, Payloads.Action action) {
        SuServer context = SuServer.get();
        if (context == null || !SuServer.config().features.staffPanel || !Perms.has(actor, Perm.STAFF_PANEL)) {
            return;
        }
        try {
            String result = perform(actor, action);
            if (result != null) {
                boolean error = result.startsWith("!");
                SuNetwork.feedback(actor, error ? result.substring(1) : result, error);
            }
        } catch (RuntimeException e) {
            com.arthou.smputilities.core.util.SuLog.error("Staff action " + action.action() + " failed", e);
            SuNetwork.feedback(actor, Lang.tr(actor, "staff.action_failed"), true);
        }
        parseUuid(action.target()).ifPresent(id -> sendDetail(actor, id));
        SuNetwork.send(actor, new Payloads.DashboardData(dashboard(actor)));
    }

    private static String arg(Payloads.Action action, int index) {
        return index < action.arguments().size() ? action.arguments().get(index) : "";
    }

    private static String denied(ServerPlayer actor) {
        return "!" + Lang.tr(actor, "error.no_permission");
    }

    /** @return a feedback line (prefixed with ! for errors) or null for none */
    private static String perform(ServerPlayer actor, Payloads.Action action) {
        MinecraftServer server = Mc.server(actor);
        SuServer context = SuServer.get();
        String name = action.action();

        // ---- actions that do not target a player
        switch (name) {
            case "setting", "chat_setting" -> {
                String path = arg(action, 0);
                // Chat managers may change chat settings without full admin rights.
                boolean chatOnly = name.equals("chat_setting");
                if (chatOnly ? !Perms.has(actor, Perm.CHAT_MANAGE) || !path.startsWith("chat.") : !Perms.has(actor, Perm.ADMIN_SETTINGS)) {
                    return denied(actor);
                }
                String[] error = new String[1];
                SuServer.configManager().edit(config -> error[0] = ConfigPaths.set(config, path, arg(action, 1)));
                if (error[0] != null) {
                    return "!" + Lang.tr(actor, "settings." + error[0], path);
                }
                context.log(actor, "setting", null, path, arg(action, 1));
                afterSettingChange(server, path);
                if (Perms.has(actor, Perm.ADMIN_SETTINGS)) {
                    SuNetwork.send(actor, new Payloads.SettingsData(settings()));
                }
                return Lang.tr(actor, "settings.changed", path, arg(action, 1));
            }
            case "chat_lock" -> {
                if (!Perms.has(actor, Perm.CHAT_MANAGE)) {
                    return denied(actor);
                }
                boolean lock = Boolean.parseBoolean(arg(action, 0));
                SuServer.configManager().edit(config -> config.chat.globalChatEnabled = !lock);
                context.log(actor, lock ? "chat.lock" : "chat.unlock", null, "", "");
                com.arthou.smputilities.server.Msg.broadcastStaffAware(lock ? "chat.lock.on" : "chat.lock.off", lock ? "chat.lock.on.public" : "chat.lock.off.public",
                    actor.getGameProfile().getName());
                return null;
            }
            case "chat_clear" -> {
                if (!Perms.has(actor, Perm.CHAT_MANAGE)) {
                    return denied(actor);
                }
                StaffCommands.clearChat(server, actor);
                return null;
            }
            case "realnames" -> {
                if (!Perms.has(actor, Perm.STAFF_REALNAMES)) {
                    return denied(actor);
                }
                boolean on = Boolean.parseBoolean(arg(action, 0));
                context.players().update(actor.getUUID(), record -> record.realNames = on);
                SuNetwork.syncState(actor);
                SuNetwork.syncNicknames(actor);
                return Lang.tr(actor, on ? "realnames.on" : "realnames.off");
            }
            case "vanish_self" -> {
                if (!Perms.has(actor, Perm.STAFF_VANISH)) {
                    return denied(actor);
                }
                VanishService.set(actor, !VanishService.isVanished(actor.getUUID()), actor);
                return null;
            }
            case "spectate_stop" -> {
                stopSpectating(actor);
                return null;
            }
            case "reload" -> {
                if (!Perms.has(actor, Perm.ADMIN_RELOAD)) {
                    return denied(actor);
                }
                StaffCommands.reload(server, actor);
                return Lang.tr(actor, "staff.reloaded");
            }
            case "dimension" -> {
                if (!Perms.has(actor, Perm.DIMENSION_MANAGE)) {
                    return denied(actor);
                }
                return StaffCommands.setDimension(actor, arg(action, 0), Boolean.parseBoolean(arg(action, 1))) ? null : "!" + Lang.tr(actor, "dimension.unknown", arg(action, 0));
            }
            case "portal_limit" -> {
                if (!Perms.has(actor, Perm.PORTAL_MANAGE)) {
                    return denied(actor);
                }
                Integer limit = parseLimit(arg(action, 1));
                if (limit == null) {
                    return "!" + Lang.tr(actor, "settings.invalid_value", arg(action, 1));
                }
                StaffCommands.setPortalLimit(actor, arg(action, 0), limit);
                return Lang.tr(actor, "portal.limit_set", arg(action, 0), limit < 0 ? Lang.tr(actor, "portal.unlimited") : limit);
            }
            case "portal_remove" -> {
                if (!Perms.has(actor, Perm.PORTAL_MANAGE)) {
                    return denied(actor);
                }
                boolean removed = context.portals().remove(arg(action, 0));
                context.log(actor, "portal.remove", null, "", "#" + arg(action, 0));
                return removed ? Lang.tr(actor, "portal.removed", arg(action, 0)) : "!" + Lang.tr(actor, "portal.not_found", arg(action, 0));
            }
            case "say" -> {
                if (!Perms.has(actor, Perm.STAFF_ANNOUNCE) || arg(action, 0).isBlank()) {
                    return denied(actor);
                }
                ChatService.serverAnnouncement(server, arg(action, 0));
                context.log(actor, "say", null, "", arg(action, 0));
                return null;
            }
            case "announce_title" -> {
                if (!Perms.has(actor, Perm.STAFF_ANNOUNCE)) {
                    return denied(actor);
                }
                TrollService.Action kind = TrollService.Action.parse(arg(action, 0));
                if (kind != TrollService.Action.TITLE_ACTION && kind != TrollService.Action.TITLE_DEATH) {
                    return "!" + Lang.tr(actor, "troll.unknown");
                }
                TrollService.broadcastTitle(server, kind, arg(action, 1));
                context.log(actor, "announce." + kind.id(), null, "", arg(action, 1));
                return null;
            }
            case "report_close", "report_tp" -> {
                boolean tp = name.equals("report_tp");
                if (!Perms.has(actor, Perm.STAFF_REPORTS) || tp && !Perms.has(actor, Perm.STAFF_TELEPORT)) {
                    return denied(actor);
                }
                int reportId;
                try {
                    reportId = Integer.parseInt(arg(action, 0));
                } catch (NumberFormatException e) {
                    return "!" + Lang.tr(actor, "report.not_found", arg(action, 0));
                }
                boolean done = tp ? ReportService.teleport(actor, reportId) : ReportService.close(server, actor, reportId);
                if (!done) {
                    return "!" + Lang.tr(actor, "report.not_found", reportId);
                }
                return tp ? null : Lang.tr(actor, "report.closed", reportId);
            }
            case "xray_tp" -> {
                if (!Perms.has(actor, Perm.XRAY_ALERTS) || !Perms.has(actor, Perm.STAFF_TELEPORT)) {
                    return denied(actor);
                }
                int alertId;
                try {
                    alertId = Integer.parseInt(arg(action, 0));
                } catch (NumberFormatException e) {
                    return "!" + Lang.tr(actor, "xray.not_found", arg(action, 0));
                }
                return XrayAlerts.teleport(actor, alertId) ? null : "!" + Lang.tr(actor, "xray.not_found", alertId);
            }
            case "modsync_rescan" -> {
                if (!Perms.has(actor, Perm.MODSYNC_MANAGE)) {
                    return denied(actor);
                }
                Thread thread = new Thread(ModSyncServer::rescan, "SMPutilities-modsync-scan");
                thread.setDaemon(true);
                thread.start();
                return Lang.tr(actor, "modsync.rescanning");
            }
            default -> {
            }
        }

        // ---- actions on a player
        Optional<UUID> targetId = parseUuid(action.target());
        if (targetId.isEmpty()) {
            return "!" + Lang.tr(actor, "player.not_found", action.target());
        }
        UUID id = targetId.get();
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        String targetName = online != null ? Names.realName(online) : Names.offlineName(server, id).orElse(id.toString());
        Names.Target target = new Names.Target(id, targetName, online);
        String shown = Names.seesRealNames(actor) ? targetName : Names.plainDisplay(id, targetName);

        switch (name) {
            case "tp" -> {
                if (!Perms.has(actor, Perm.STAFF_TELEPORT)) {
                    return denied(actor);
                }
                if (online == null) {
                    PlayerRecord record = context.players().get(id);
                    if (record.logout == null) {
                        return "!" + Lang.tr(actor, "tplogout.none");
                    }
                    Teleports.teleport(actor, record.logout, true);
                } else {
                    Teleports.teleport(actor, Teleports.locationOf(online), true);
                }
                context.log(actor, "teleport.to", id, targetName, "");
                return null;
            }
            case "tphere" -> {
                if (!Perms.has(actor, Perm.STAFF_TELEPORT) || online == null) {
                    return online == null ? "!" + Lang.tr(actor, "inventory.online_only", shown) : denied(actor);
                }
                Teleports.teleport(online, Teleports.locationOf(actor), true);
                context.log(actor, "teleport.here", id, targetName, "");
                return Lang.tr(actor, "staff.pulled", shown);
            }
            case "spectate" -> {
                if (!Perms.has(actor, Perm.STAFF_SPECTATE) || online == null) {
                    return online == null ? "!" + Lang.tr(actor, "inventory.online_only", shown) : denied(actor);
                }
                SPECTATE_RETURN.putIfAbsent(actor.getUUID(), actor.gameMode.getGameModeForPlayer());
                Teleports.teleport(actor, Teleports.locationOf(online), true);
                actor.setGameMode(GameType.SPECTATOR);
                actor.setCamera(online);
                context.log(actor, "spectate", id, targetName, "");
                return Lang.tr(actor, "staff.spectating", shown);
            }
            case "inventory", "enderchest" -> {
                if (!Perms.has(actor, Perm.STAFF_INVENTORY)) {
                    return denied(actor);
                }
                if (name.equals("inventory")) {
                    InventoryViews.openInventory(actor, target);
                } else {
                    InventoryViews.openEnderChest(actor, target);
                }
                return null;
            }
            case "curios", "cosmetic" -> {
                if (!Perms.has(actor, Perm.STAFF_INVENTORY) || online == null) {
                    return online == null ? "!" + Lang.tr(actor, "inventory.online_only", shown) : denied(actor);
                }
                if (name.equals("curios")) {
                    InventoryViews.openCurios(actor, online);
                } else {
                    InventoryViews.openCosmetic(actor, online);
                }
                return null;
            }
            case "ban" -> {
                if (!Perms.has(actor, Perm.STAFF_BAN)) {
                    return denied(actor);
                }
                if (!canPunish(actor, online)) {
                    return "!" + Lang.tr(actor, "punish.cannot_target");
                }
                Long duration = Durations.parse(arg(action, 0).isEmpty() ? "perm" : arg(action, 0));
                if (duration == null) {
                    return "!" + Lang.tr(actor, "punish.invalid_duration", arg(action, 0));
                }
                PunishmentService.ban(server, actor, target, duration, arg(action, 1));
                return Lang.tr(actor, "punish.done.ban", shown, duration < 0 ? Lang.tr(actor, "punish.permanent") : Durations.format(duration));
            }
            case "unban" -> {
                if (!Perms.has(actor, Perm.STAFF_BAN)) {
                    return denied(actor);
                }
                return PunishmentService.unban(server, actor, target, arg(action, 0))
                    ? Lang.tr(actor, "punish.done.unban", shown) : "!" + Lang.tr(actor, "punish.not_banned", shown);
            }
            case "kick" -> {
                if (!Perms.has(actor, Perm.STAFF_KICK) || online == null) {
                    return online == null ? "!" + Lang.tr(actor, "inventory.online_only", shown) : denied(actor);
                }
                if (!canPunish(actor, online)) {
                    return "!" + Lang.tr(actor, "punish.cannot_target");
                }
                PunishmentService.kick(server, actor, online, arg(action, 0));
                return Lang.tr(actor, "punish.done.kick", shown);
            }
            case "mute", "voice_mute" -> {
                boolean voice = name.equals("voice_mute");
                if (!Perms.has(actor, voice ? Perm.STAFF_VOICE_MUTE : Perm.STAFF_MUTE)) {
                    return denied(actor);
                }
                if (!canPunish(actor, online)) {
                    return "!" + Lang.tr(actor, "punish.cannot_target");
                }
                Long duration = Durations.parse(arg(action, 0).isEmpty() ? "perm" : arg(action, 0));
                if (duration == null) {
                    return "!" + Lang.tr(actor, "punish.invalid_duration", arg(action, 0));
                }
                PunishmentService.mute(server, actor, target, duration, arg(action, 1), voice);
                return Lang.tr(actor, voice ? "punish.done.voice_mute" : "punish.done.mute", shown,
                    duration < 0 ? Lang.tr(actor, "punish.permanent") : Durations.format(duration));
            }
            case "unmute", "voice_unmute" -> {
                boolean voice = name.equals("voice_unmute");
                if (!Perms.has(actor, voice ? Perm.STAFF_VOICE_MUTE : Perm.STAFF_MUTE)) {
                    return denied(actor);
                }
                return PunishmentService.unmute(server, actor, target, voice)
                    ? Lang.tr(actor, voice ? "punish.done.voice_unmute" : "punish.done.unmute", shown) : "!" + Lang.tr(actor, "punish.not_muted", shown);
            }
            case "voice_deafen" -> {
                if (!Perms.has(actor, Perm.STAFF_VOICE_MUTE)) {
                    return denied(actor);
                }
                boolean[] now = new boolean[1];
                context.players().update(id, record -> now[0] = record.voiceDeafened = !record.voiceDeafened);
                context.log(actor, now[0] ? "voice_deafen" : "voice_undeafen", id, targetName, "");
                return Lang.tr(actor, now[0] ? "voice.deafened" : "voice.undeafened", shown);
            }
            case "warn" -> {
                if (!Perms.has(actor, Perm.STAFF_WARN)) {
                    return denied(actor);
                }
                PunishmentService.warn(server, actor, target, arg(action, 0));
                return Lang.tr(actor, "punish.done.warn", shown);
            }
            case "revoke" -> {
                if (!Perms.has(actor, Perm.STAFF_BAN)) {
                    return denied(actor);
                }
                PunishmentType type;
                try {
                    type = PunishmentType.valueOf(arg(action, 0));
                } catch (IllegalArgumentException e) {
                    return "!" + Lang.tr(actor, "staff.action_failed");
                }
                if (type == PunishmentType.BAN) {
                    PunishmentService.unban(server, actor, target, "revoked from panel");
                } else if (type == PunishmentType.MUTE || type == PunishmentType.VOICE_MUTE) {
                    PunishmentService.unmute(server, actor, target, type == PunishmentType.VOICE_MUTE);
                }
                return null;
            }
            case "vanish" -> {
                if (!Perms.has(actor, online == actor ? Perm.STAFF_VANISH : Perm.STAFF_VANISH_OTHERS) || online == null) {
                    return online == null ? "!" + Lang.tr(actor, "inventory.online_only", shown) : denied(actor);
                }
                boolean now = !VanishService.isVanished(id);
                VanishService.set(online, now, actor);
                return online == actor ? null : Lang.tr(actor, now ? "vanish.other_on" : "vanish.other_off", shown);
            }
            case "nick_set" -> {
                if (!Perms.has(actor, online == actor ? Perm.NICKNAME_CHANGE : Perm.NICKNAME_OTHERS)) {
                    return denied(actor);
                }
                String error = NicknameService.set(server, id, arg(action, 0), true, actor);
                return error == null ? Lang.tr(actor, "nick.set_other", targetName, StyledText.strip(arg(action, 0))) : "!" + Lang.tr(actor, error);
            }
            case "nick_animation" -> {
                if (!Perms.has(actor, online == actor ? Perm.NICKNAME_ANIMATION : Perm.NICKNAME_OTHERS)) {
                    return denied(actor);
                }
                NameAnimation animation = NameAnimation.parse(arg(action, 0));
                if (animation == null) {
                    return "!" + Lang.tr(actor, "nick.unknown_animation");
                }
                NicknameService.setAnimation(server, id, animation, actor);
                return Lang.tr(actor, "nick.animation_set", targetName, animation.id());
            }
            case "nick_clear" -> {
                if (!Perms.has(actor, online == actor ? Perm.NICKNAME_CHANGE : Perm.NICKNAME_OTHERS)) {
                    return denied(actor);
                }
                NicknameService.clear(server, id, actor);
                return Lang.tr(actor, "nick.cleared_other", targetName);
            }
            case "gamemode" -> {
                if (!Perms.has(actor, Perm.STAFF_GAMEMODE) || online == null) {
                    return online == null ? "!" + Lang.tr(actor, "inventory.online_only", shown) : denied(actor);
                }
                GameType mode = GameType.byName(arg(action, 0), null);
                if (mode == null) {
                    return "!" + Lang.tr(actor, "settings.invalid_value", arg(action, 0));
                }
                online.setGameMode(mode);
                context.log(actor, "gamemode", id, targetName, mode.getName());
                return Lang.tr(actor, "staff.gamemode_set", shown, mode.getName());
            }
            case "heal", "feed" -> {
                if (!Perms.has(actor, Perm.STAFF_STATS_EDIT) || online == null) {
                    return online == null ? "!" + Lang.tr(actor, "inventory.online_only", shown) : denied(actor);
                }
                if (name.equals("heal")) {
                    online.setHealth(online.getMaxHealth());
                    online.clearFire();
                }
                online.getFoodData().setFoodLevel(20);
                online.getFoodData().setSaturation(5.0F);
                context.log(actor, name, id, targetName, "");
                return Lang.tr(actor, name.equals("heal") ? "staff.healed" : "staff.fed", shown);
            }
            case "stat_set" -> {
                if (!Perms.has(actor, Perm.STAFF_STATS_EDIT)) {
                    return denied(actor);
                }
                PlayerStats.Kind kind;
                int value;
                try {
                    kind = PlayerStats.Kind.valueOf(arg(action, 0).toUpperCase(Locale.ROOT));
                    value = Integer.parseInt(arg(action, 1).trim());
                } catch (IllegalArgumentException e) {
                    return "!" + Lang.tr(actor, "settings.invalid_value", arg(action, 1));
                }
                if (value < 0 || !PlayerStats.set(server, id, kind, value)) {
                    return "!" + Lang.tr(actor, "staff.action_failed");
                }
                context.log(actor, "stat.set", id, targetName, kind.name() + "=" + value);
                return Lang.tr(actor, "staff.stat_set", kind.name().toLowerCase(Locale.ROOT), value);
            }
            case "custom_stat_set", "custom_stat_remove" -> {
                if (!Perms.has(actor, Perm.STAFF_STATS_EDIT)) {
                    return denied(actor);
                }
                String key = arg(action, 0).trim();
                if (key.isEmpty() || key.length() > 32) {
                    return "!" + Lang.tr(actor, "settings.invalid_value", key);
                }
                String value = arg(action, 1);
                context.players().update(id, record -> {
                    if (name.equals("custom_stat_set")) {
                        record.customStats.put(key, value);
                    } else {
                        record.customStats.remove(key);
                    }
                });
                context.log(actor, name, id, targetName, key + "=" + value);
                return null;
            }
            case "note_add" -> {
                if (!Perms.has(actor, Perm.STAFF_HISTORY) || arg(action, 0).isBlank()) {
                    return denied(actor);
                }
                String text = arg(action, 0).length() > 300 ? arg(action, 0).substring(0, 300) : arg(action, 0);
                context.players().update(id, record -> record.notes.add(new PlayerRecord.StaffNote(System.currentTimeMillis(), actor.getGameProfile().getName(), text)));
                context.log(actor, "note.add", id, targetName, text);
                return null;
            }
            case "silent_join", "silent_quit" -> {
                if (!Perms.has(actor, Perm.CHAT_MANAGE)) {
                    return denied(actor);
                }
                boolean[] now = new boolean[1];
                context.players().update(id, record -> {
                    if (name.equals("silent_join")) {
                        now[0] = record.silentJoin = !record.silentJoin;
                    } else {
                        now[0] = record.silentQuit = !record.silentQuit;
                    }
                });
                context.log(actor, name, id, targetName, String.valueOf(now[0]));
                return null;
            }
            case "backup_create" -> {
                if (!Perms.has(actor, Perm.STAFF_INVENTORY)) {
                    return denied(actor);
                }
                PlayerSnapshot snapshot = online != null
                    ? BackupService.snapshot(online, SnapshotType.MANUAL, "panel", actor.getGameProfile().getName())
                    : BackupService.snapshotOffline(server, id, targetName, SnapshotType.MANUAL, "panel by " + actor.getGameProfile().getName());
                if (snapshot == null) {
                    return "!" + Lang.tr(actor, "inventory.no_data", targetName);
                }
                context.log(actor, "backup.create", id, targetName, snapshot.id());
                return Lang.tr(actor, "backup.created", snapshot.id(), shown);
            }
            case "backups_open" -> {
                if (!Perms.has(actor, Perm.STAFF_INVENTORY)) {
                    return denied(actor);
                }
                BackupMenus.Mode mode = switch (arg(action, 0)) {
                    case "curios" -> BackupMenus.Mode.CURIOS;
                    case "cosmetic" -> BackupMenus.Mode.COSMETIC;
                    default -> BackupMenus.Mode.VANILLA;
                };
                BackupMenus.openCategories(actor, new BackupMenus.Target(id, targetName), mode);
                return null;
            }
            case "backup_restore" -> {
                if (!Perms.has(actor, Perm.STAFF_INVENTORY_RESTORE)) {
                    return denied(actor);
                }
                Optional<PlayerSnapshot> snapshot = BackupService.store() == null ? Optional.empty() : BackupService.store().find(id, arg(action, 0));
                if (snapshot.isEmpty()) {
                    return "!" + Lang.tr(actor, "backup.not_found", arg(action, 0));
                }
                Set<PlayerState.Part> parts = switch (arg(action, 1)) {
                    case "inventory" -> BackupService.INVENTORY_ONLY;
                    case "ender" -> BackupService.ENDER_ONLY;
                    default -> BackupService.FULL;
                };
                boolean ok = BackupService.restore(server, actor, id, targetName, snapshot.get(), parts, parts == BackupService.FULL);
                return ok ? Lang.tr(actor, "backup.restored", snapshot.get().id(), shown) : "!" + Lang.tr(actor, "backup.restore_failed");
            }
            case "backup_delete" -> {
                if (!Perms.has(actor, Perm.STAFF_INVENTORY_RESTORE) || BackupService.store() == null) {
                    return denied(actor);
                }
                boolean removed = BackupService.store().delete(id, arg(action, 0));
                context.log(actor, "backup.delete", id, targetName, arg(action, 0));
                return removed ? Lang.tr(actor, "backup.deleted", arg(action, 0)) : "!" + Lang.tr(actor, "backup.not_found", arg(action, 0));
            }
            case "profiles_open" -> {
                if (!Perms.has(actor, Perm.STAFF_PROFILES)) {
                    return denied(actor);
                }
                ProfileMenus.openList(actor, id, targetName);
                return null;
            }
            case "profile_switch" -> {
                if (!Perms.has(actor, Perm.STAFF_PROFILES) || online == null) {
                    return online == null ? "!" + Lang.tr(actor, "inventory.online_only", shown) : denied(actor);
                }
                String error = ProfileService.switchTo(online, arg(action, 0), actor, true);
                return error == null ? Lang.tr(actor, "profile.switched_other", shown, arg(action, 0)) : "!" + Lang.tr(actor, error, arg(action, 0));
            }
            case "profile_delete" -> {
                if (!Perms.has(actor, Perm.STAFF_PROFILES)) {
                    return denied(actor);
                }
                String error = ProfileService.delete(id, targetName, arg(action, 0), actor);
                return error == null ? Lang.tr(actor, "profile.deleted", arg(action, 0)) : "!" + Lang.tr(actor, error, arg(action, 0));
            }
            case "troll" -> {
                if (!Perms.has(actor, Perm.STAFF_TROLL) || online == null) {
                    return online == null ? "!" + Lang.tr(actor, "inventory.online_only", shown) : denied(actor);
                }
                TrollService.Action troll = TrollService.Action.parse(arg(action, 0));
                if (troll == null) {
                    return "!" + Lang.tr(actor, "troll.unknown");
                }
                if (!TrollService.allowed(troll)) {
                    return "!" + Lang.tr(actor, "error.feature_disabled");
                }
                String error = TrollService.apply(troll, online, arg(action, 1), actor);
                if (error != null) {
                    return "!" + Lang.tr(actor, error, shown);
                }
                context.log(actor, "troll." + troll.id(), id, targetName, arg(action, 1));
                return Lang.tr(actor, "troll.done", troll.id(), shown);
            }
            default -> {
                return "!" + Lang.tr(actor, "staff.unknown_action", name);
            }
        }
    }

    private static boolean canPunish(ServerPlayer actor, ServerPlayer target) {
        // Staff cannot punish someone with a higher op level than their own.
        return target == null || target == actor || Perms.opLevel(target) <= Perms.opLevel(actor);
    }

    public static void stopSpectating(ServerPlayer actor) {
        GameType previous = SPECTATE_RETURN.remove(actor.getUUID());
        actor.setCamera(actor);
        if (previous != null) {
            actor.setGameMode(previous);
        }
    }

    static Integer parseLimit(String text) {
        String value = text.trim().toLowerCase(Locale.ROOT);
        if (value.equals("unlimited") || value.equals("ilimitado") || value.equals("-1") || value.equals("off")) {
            return -1;
        }
        try {
            int limit = Integer.parseInt(value);
            return limit < 0 ? null : limit;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Some settings need to be pushed out right away. */
    public static void afterSettingChange(MinecraftServer server, String path) {
        if (path.startsWith("antiXray.") || path.startsWith("xrayAlerts.") || path.equals("features.antiXray")
            || path.equals("features.xrayAlerts") || path.equals("features.reload")) {
            AntiXray.requestRebuild();
        }
        if (path.startsWith("nicknames.") || path.equals("features.nicknames")) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                com.arthou.smputilities.platform.Platform.get().refreshNames(player);
            }
            SuNetwork.syncNicknamesToAll(server);
        }
        if (path.startsWith("features.") || path.startsWith("qol.")) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                server.getCommands().sendCommands(player);
            }
        }
        if (path.startsWith("vanish.") || path.equals("features.vanish")) {
            for (UUID id : VanishService.vanished()) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    VanishService.refresh(player);
                }
            }
        }
        SuNetwork.syncStateToStaff(server);
    }

    static Optional<UUID> parseUuid(String value) {
        try {
            return value == null || value.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
