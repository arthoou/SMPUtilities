package com.arthou.smputilities.discord;

import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.discord.DiscordWebhook;
import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.vanish.VanishService;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends server events to Discord webhooks: chat, joins and quits, deaths
 * and server start and stop to the public one; staff actions, reports,
 * x-ray alerts and alts to the staff one. Nothing is sent while a webhook
 * URL is empty.
 */
public final class DiscordBridge {
    private static volatile DiscordWebhook main;
    private static volatile DiscordWebhook staff;
    private static String mainUrl = "";
    private static String staffUrl = "";

    private DiscordBridge() {
    }

    /** Messages follow the server language setting ("auto" means English here). */
    public static String language() {
        return Lang.PT.equals(SuServer.config().language) ? Lang.PT : Lang.EN;
    }

    private static SuConfig.Discord config() {
        return SuServer.config().discord;
    }

    /** (Re)connects after the server starts or the settings change. */
    public static synchronized void reload() {
        SuConfig.Discord config = config();
        if (!config.webhookUrl.trim().equals(mainUrl)) {
            main = replace(main, config.webhookUrl, config.username);
            mainUrl = config.webhookUrl.trim();
        }
        if (!config.staffWebhookUrl.trim().equals(staffUrl)) {
            staff = replace(staff, config.staffWebhookUrl, config.username);
            staffUrl = config.staffWebhookUrl.trim();
        }
    }

    private static DiscordWebhook replace(DiscordWebhook old, String url, String username) {
        if (old != null) {
            old.close();
        }
        if (url == null || url.isBlank()) {
            return null;
        }
        if (!DiscordWebhook.validUrl(url)) {
            SuLog.warn("discord: \"" + url + "\" is not a Discord webhook URL (https://discord.com/api/webhooks/...); nothing is sent");
            return null;
        }
        return new DiscordWebhook(url, username, null);
    }

    public static void onServerStart() {
        reload();
        if (config().serverStatus) {
            send(main, Lang.format(language(), "discord.started"));
        }
    }

    public static synchronized void onServerStop() {
        if (config().serverStatus) {
            send(main, Lang.format(language(), "discord.stopped"));
        }
        if (main != null) {
            main.close();
        }
        if (staff != null) {
            staff.close();
        }
        main = null;
        staff = null;
        mainUrl = "";
        staffUrl = "";
    }

    private static void send(DiscordWebhook webhook, String line) {
        if (webhook != null) {
            webhook.send(line);
        }
    }

    // --------------------------------------------------------------- public

    public static void chat(ServerPlayer player, String text) {
        if (main != null && config().chat) {
            main.send("**" + DiscordWebhook.escape(Names.plainDisplay(player)) + "**: " + DiscordWebhook.escape(text));
        }
    }

    public static void presence(ServerPlayer player, boolean join) {
        if (main != null && config().joinQuit && !VanishService.isVanished(player.getUUID())) {
            main.send(Lang.format(language(), join ? "discord.join" : "discord.quit", DiscordWebhook.escape(Names.plainDisplay(player))));
        }
    }

    public static void death(ServerPlayer player) {
        if (main != null && config().deaths && !VanishService.isVanished(player.getUUID())) {
            main.send("💀 " + DiscordWebhook.escape(player.getCombatTracker().getDeathMessage().getString()));
        }
    }

    // ---------------------------------------------------------------- staff

    /** A line for the staff channel. */
    public static void staff(String line) {
        send(staff, DiscordWebhook.escape(line));
    }

    public static void staffAction(String actor, String action, String target, String details) {
        if (staff != null && config().staffActions) {
            StringBuilder line = new StringBuilder("🛡 **").append(DiscordWebhook.escape(actor)).append("** · ").append(DiscordWebhook.escape(action));
            if (target != null && !target.isEmpty()) {
                line.append(" → ").append(DiscordWebhook.escape(target));
            }
            if (details != null && !details.isEmpty()) {
                line.append(" · ").append(DiscordWebhook.escape(details));
            }
            staff.send(line.toString());
        }
    }

    public static void report(int id, String reporter, String target, String reason) {
        if (staff != null && config().reports) {
            staff.send("📣 " + DiscordWebhook.escape(Lang.format(language(), "report.notice", id, reporter, target, reason)));
        }
    }

    public static void xray(String line) {
        if (staff != null && config().xrayAlerts) {
            staff.send("⛏ " + DiscordWebhook.escape(line));
        }
    }

    public static boolean altAlerts() {
        return config().altAlerts;
    }
}
