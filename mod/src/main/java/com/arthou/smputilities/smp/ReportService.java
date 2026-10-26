package com.arthou.smputilities.smp;

import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.report.Report;
import com.arthou.smputilities.core.teleport.Cooldowns;
import com.arthou.smputilities.core.util.Durations;
import com.arthou.smputilities.discord.DiscordBridge;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.teleport.Teleports;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.Optional;

/** /report for players, /su reports for staff. */
public final class ReportService {
    private static final Cooldowns COOLDOWNS = new Cooldowns();
    private static final int LIST_LIMIT = 15;

    private ReportService() {
    }

    private static boolean enabled() {
        return SuServer.config().features.reports;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("report")
            .requires(source -> enabled() && Perms.has(source, Perm.REPORT))
            .then(Commands.argument("player", StringArgumentType.string()).suggests(Names.onlineSuggestions())
                .then(Commands.argument("reason", StringArgumentType.greedyString()).executes(ReportService::report))));
    }

    /** /su reports [close|tp <id>]. */
    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("reports")
            .requires(source -> enabled() && Perms.has(source, Perm.STAFF_REPORTS))
            .executes(ReportService::list)
            .then(Commands.literal("close").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ReportService::close)))
            .then(Commands.literal("tp").requires(Perms.require(Perm.STAFF_TELEPORT))
                .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ReportService::teleport)));
    }

    private static int report(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer reporter = source.getPlayerOrException();
        SuConfig.Reports config = SuServer.config().reports;
        String input = StringArgumentType.getString(context, "player");
        Optional<Names.Target> found = Names.resolve(source.getServer(), input, reporter);
        if (found.isEmpty()) {
            return Msg.error(source, "player.not_found", input);
        }
        Names.Target target = found.get();
        if (target.id().equals(reporter.getUUID())) {
            return Msg.error(source, "report.self");
        }
        long now = System.currentTimeMillis();
        long wait = COOLDOWNS.remaining(reporter.getUUID(), "report", now);
        if (wait > 0) {
            return Msg.error(source, "report.cooldown", Durations.format(Math.max(1000, wait)));
        }
        String reason = StringArgumentType.getString(context, "reason").trim();
        if (reason.length() > config.maxReasonLength) {
            reason = reason.substring(0, Math.max(1, config.maxReasonLength));
        }
        SuServer server = SuServer.get();
        Report report = server.reports().create(reporter.getUUID(), Names.realName(reporter), target.id(), target.name(), reason,
            Teleports.locationOf(reporter), now);
        COOLDOWNS.start(reporter.getUUID(), "report", config.cooldownSeconds * 1000L, now);
        server.log(reporter, "report", target.id(), target.name(), "#" + report.id + " " + reason);
        Msg.ok(source, "report.sent", report.id);
        for (ServerPlayer staff : source.getServer().getPlayerList().getPlayers()) {
            if (Perms.has(staff, Perm.STAFF_REPORTS)) {
                Mc.send(staff, notice(staff, report));
                Mc.playSound(staff, config.notifySound, SoundSource.MASTER, 0.8F, 1.2F);
            }
        }
        Mc.console(source.getServer(), Texts.literal(Lang.format(Lang.EN, "report.notice", report.id, report.reporterName, report.targetName, report.reason)));
        DiscordBridge.report(report.id, report.reporterName, report.targetName, report.reason);
        return 1;
    }

    /** "[Report #3] Alice → Bob: reason  [Teleport] [Close]" */
    private static MutableComponent notice(ServerPlayer viewer, Report report) {
        MutableComponent line = Msg.prefix()
            .append(Texts.literal(Lang.tr(viewer, "report.notice", report.id, report.reporterName, report.targetName, report.reason))
                .withStyle(style -> Texts.color(style, Msg.WARN)));
        return line.append(buttons(viewer, report));
    }

    private static MutableComponent buttons(ServerPlayer viewer, Report report) {
        MutableComponent row = Texts.empty();
        if (report.where != null && Perms.has(viewer, Perm.STAFF_TELEPORT)) {
            row.append(Texts.literal(" "))
                .append(button(viewer, "report.tp_button", "/su reports tp " + report.id, ChatFormatting.AQUA));
        }
        return row.append(Texts.literal(" "))
            .append(button(viewer, "report.close_button", "/su reports close " + report.id, ChatFormatting.GREEN));
    }

    private static MutableComponent button(ServerPlayer viewer, String key, String command, ChatFormatting color) {
        MutableComponent button = Texts.literal("[" + Lang.tr(viewer, key) + "]").withStyle(color);
        return Texts.hover(Texts.clickRun(button, command), Texts.literal(command));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer viewer = Mc.player(source);
        List<Report> open = SuServer.get().reports().open();
        if (open.isEmpty()) {
            return Msg.info(source, "report.none");
        }
        Msg.info(source, "report.header", open.size());
        long now = System.currentTimeMillis();
        int from = Math.max(0, open.size() - LIST_LIMIT);
        for (Report report : open.subList(from, open.size())) {
            MutableComponent line = Texts.literal("#" + report.id + " ").withStyle(ChatFormatting.GOLD)
                .append(Texts.literal(Lang.tr(viewer, "report.entry", report.reporterName, report.targetName,
                    Durations.format(now - report.createdAt), report.reason)).withStyle(ChatFormatting.GRAY));
            if (viewer != null) {
                line.append(buttons(viewer, report));
            }
            Msg.raw(source, line);
        }
        return open.size();
    }

    private static int close(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int id = IntegerArgumentType.getInteger(context, "id");
        return close(source.getServer(), Mc.player(source), id) ? Msg.ok(source, "report.closed", id) : Msg.error(source, "report.not_found", id);
    }

    /** Closes an open report and tells the reporter. Shared by the command and the staff panel. */
    public static boolean close(MinecraftServer server, ServerPlayer actor, int id) {
        SuServer context = SuServer.get();
        Optional<Report> report = context.reports().get(id);
        String actorName = actor == null ? "Console" : Names.realName(actor);
        if (report.isEmpty() || !context.reports().close(id, actorName, System.currentTimeMillis(), SuServer.config().reports.keepClosed)) {
            return false;
        }
        context.log(actor, "report.close", report.get().target, report.get().targetName, "#" + id);
        ServerPlayer reporter = report.get().reporter == null ? null : server.getPlayerList().getPlayer(report.get().reporter);
        if (reporter != null) {
            Msg.info(reporter, "report.closed_notice", id);
        }
        return true;
    }

    private static int teleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int id = IntegerArgumentType.getInteger(context, "id");
        return teleport(context.getSource().getPlayerOrException(), id) ? 1 : Msg.error(context.getSource(), "report.not_found", id);
    }

    /** Takes staff to where the report was made. */
    public static boolean teleport(ServerPlayer staff, int id) {
        Optional<Report> report = SuServer.get().reports().get(id);
        return report.isPresent() && report.get().where != null && Teleports.teleport(staff, report.get().where, true);
    }

    /** Tells staff who join how many reports are waiting. */
    public static void onLogin(ServerPlayer player) {
        if (!enabled() || SuServer.get() == null || !Perms.has(player, Perm.STAFF_REPORTS)) {
            return;
        }
        int open = SuServer.get().reports().open().size();
        if (open > 0) {
            Mc.send(player, Msg.prefix().append(Texts.clickRun(
                Texts.literal(Lang.tr(player, "report.pending", open)).withStyle(style -> Texts.color(style, Msg.WARN)), "/su reports")));
        }
    }
}
