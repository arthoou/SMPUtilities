package com.arthou.smputilities.smp;

import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.restart.RestartSchedule;
import com.arthou.smputilities.core.util.Durations;
import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;

/**
 * /su restart and daily restarts: warns everyone as the time approaches, then
 * stops the server cleanly (the world is saved). Bringing it back up is the
 * job of the host or the start script, as with /stop.
 */
public final class RestartService {
    private static volatile long target;
    private static String reason = "";
    private static boolean daily;
    private static long lastRemaining = Long.MAX_VALUE;
    /** A cancelled daily restart: the next one is looked up after this time. */
    private static long skipUntil;

    private RestartService() {
    }

    private static boolean enabled() {
        return SuServer.config().features.restart;
    }

    public static void onServerStart() {
        target = 0;
        reason = "";
        daily = false;
        lastRemaining = Long.MAX_VALUE;
        skipUntil = 0;
    }

    /** /su restart [<time> [reason] | cancel]. */
    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("restart")
            .requires(source -> enabled() && Perms.has(source, Perm.ADMIN_RESTART))
            .executes(RestartService::status)
            .then(Commands.literal("cancel").executes(RestartService::cancel))
            .then(Commands.argument("time", StringArgumentType.word())
                .executes(context -> start(context, ""))
                .then(Commands.argument("reason", StringArgumentType.greedyString())
                    .executes(context -> start(context, StringArgumentType.getString(context, "reason")))));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        if (target == 0) {
            return Msg.info(context.getSource(), "restart.none");
        }
        return Msg.info(context.getSource(), "restart.status", Durations.format(Math.max(0, target - System.currentTimeMillis())),
            reason.isEmpty() ? "-" : reason);
    }

    private static int start(CommandContext<CommandSourceStack> context, String why) {
        String time = StringArgumentType.getString(context, "time");
        Long millis;
        try {
            millis = "now".equalsIgnoreCase(time) ? Long.valueOf(0) : Durations.parse(time);
        } catch (RuntimeException e) {
            millis = null;
        }
        if (millis == null || millis < 0) {
            return Msg.error(context.getSource(), "restart.bad_time", time);
        }
        long now = System.currentTimeMillis();
        target = now + millis;
        reason = why;
        daily = false;
        // Announce right away, then at every warning still ahead.
        lastRemaining = Long.MAX_VALUE;
        announce(context.getSource().getServer(), seconds(now));
        lastRemaining = seconds(now);
        SuServer.get().log(Mc.player(context.getSource()), "restart", null, "", time + " " + why);
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> context) {
        if (target == 0) {
            return Msg.error(context.getSource(), "restart.none");
        }
        if (daily) {
            skipUntil = target;
        }
        target = 0;
        reason = "";
        SuServer.get().log(Mc.player(context.getSource()), "restart.cancel", null, "", "");
        broadcast(context.getSource().getServer(), "restart.cancelled");
        return 1;
    }

    private static long seconds(long now) {
        return (long) Math.ceil((target - now) / 1000.0);
    }

    /** Once per second. */
    public static void tick(MinecraftServer server) {
        if (!enabled()) {
            return;
        }
        SuConfig.Restart config = SuServer.config().restart;
        long now = System.currentTimeMillis();
        if (target == 0 && config.dailyTimes != null && !config.dailyTimes.isEmpty()) {
            ZoneId zone = ZoneId.systemDefault();
            long next = RestartSchedule.nextDaily(config.dailyTimes, ZonedDateTime.ofInstant(Instant.ofEpochMilli(Math.max(now, skipUntil)), zone));
            if (next > 0) {
                target = next;
                daily = true;
                reason = "";
                lastRemaining = seconds(now);
            }
        }
        if (target == 0) {
            return;
        }
        long remaining = seconds(now);
        for (Integer warning : RestartSchedule.crossed(config.warningSeconds == null ? new ArrayList<>() : config.warningSeconds, lastRemaining, remaining)) {
            announce(server, warning);
        }
        lastRemaining = remaining;
        if (remaining <= 0) {
            shutdown(server);
        }
    }

    private static void announce(MinecraftServer server, long secondsLeft) {
        String left = Durations.format(Math.max(0, secondsLeft) * 1000L);
        SuConfig.Restart config = SuServer.config().restart;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String key = reason.isEmpty() ? "restart.warning" : "restart.warning_reason";
            Component text = Msg.prefix().append(Texts.literal(Lang.tr(player, key, left, reason)).withStyle(style -> Texts.color(style, Msg.WARN)));
            Mc.send(player, text);
            if (secondsLeft <= 10) {
                Mc.actionBar(player, Texts.literal(Lang.tr(player, "restart.actionbar", left)).withStyle(style -> Texts.color(style, Msg.DANGER)));
            }
            if (!config.sound.isEmpty()) {
                Mc.playSound(player, config.sound, SoundSource.MASTER, 1.0F, secondsLeft <= 10 ? 1.5F : 1.0F);
            }
        }
        Mc.console(server, Texts.literal(Lang.format(Lang.EN, reason.isEmpty() ? "restart.warning" : "restart.warning_reason", left, reason)));
    }

    private static void broadcast(MinecraftServer server, String key) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Msg.info(player, key);
        }
        Mc.console(server, Texts.literal(Lang.format(Lang.EN, key)));
    }

    private static void shutdown(MinecraftServer server) {
        target = 0;
        SuLog.info("Scheduled restart: stopping the server");
        String custom = SuServer.config().restart.kickMessage;
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            String text = custom == null || custom.isEmpty() ? Lang.tr(player, "restart.kick") : custom;
            player.connection.disconnect(Msg.styled(text));
        }
        server.halt(false);
    }
}
