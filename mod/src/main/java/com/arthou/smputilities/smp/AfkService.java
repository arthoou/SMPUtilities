package com.arthou.smputilities.smp;

import com.arthou.smputilities.core.afk.AfkTracker;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.platform.Platform;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.vanish.VanishService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.List;

/**
 * /afk and automatic AFK detection. AFK players get a tag in the tab list,
 * do not hold back the night (see mixin.SleepStatusMixin) and can be kicked
 * after a configurable idle time.
 */
public final class AfkService {
    private static final AfkTracker TRACKER = new AfkTracker();
    private static final int MAX_REASON = 100;

    private AfkService() {
    }

    private static boolean enabled() {
        return SuServer.config().features.afk;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("afk")
            .requires(source -> enabled() && Perms.has(source, Perm.AFK))
            .executes(context -> toggle(context, ""))
            .then(Commands.argument("reason", StringArgumentType.greedyString())
                .executes(context -> toggle(context, StringArgumentType.getString(context, "reason")))));
    }

    private static int toggle(CommandContext<CommandSourceStack> context, String reason) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean afk = !TRACKER.isAfk(player.getUUID());
        String clean = reason.length() > MAX_REASON ? reason.substring(0, MAX_REASON) : reason;
        TRACKER.set(player.getUUID(), afk, clean, System.currentTimeMillis());
        changed(player, afk, clean);
        return 1;
    }

    public static void onLogin(ServerPlayer player) {
        TRACKER.join(player.getUUID(), System.currentTimeMillis());
    }

    public static void onLogout(ServerPlayer player) {
        TRACKER.forget(player.getUUID());
    }

    /** Chat and private messages; safe from any thread. */
    public static void activity(ServerPlayer player) {
        TRACKER.activity(player.getUUID(), System.currentTimeMillis());
    }

    public static boolean isAfk(ServerPlayer player) {
        return enabled() && TRACKER.isAfk(player.getUUID());
    }

    /** Called by the sleep status: AFK players who are not in bed are left out of the count. */
    public static boolean ignoredForSleep(ServerPlayer player) {
        return SuServer.get() != null && SuServer.config().afk.ignoreForSleep && isAfk(player) && !player.isSleeping();
    }

    /** Once per second. */
    public static void tick(MinecraftServer server) {
        if (!enabled()) {
            return;
        }
        SuConfig.Afk config = SuServer.config().afk;
        AfkTracker.Settings settings = new AfkTracker.Settings(Math.max(0, config.autoMinutes) * 60_000L, Math.max(0, config.kickMinutes) * 60_000L);
        long now = System.currentTimeMillis();
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            boolean walking = Mc.onGround(player) && !player.isPassenger();
            AfkTracker.Change change = TRACKER.sample(player.getUUID(), player.getYRot(), player.getXRot(), player.getX(), player.getZ(),
                walking, now, settings);
            switch (change) {
                case BECAME_AFK -> changed(player, true, "");
                case RETURNED -> changed(player, false, "");
                case KICK -> {
                    if (!Perms.has(player, Perm.AFK_BYPASS_KICK)) {
                        SuLog.info("Kicking " + Mc.name(player) + " for being AFK");
                        player.connection.disconnect(Texts.literal(Lang.tr(player, "afk.kicked", config.kickMinutes)));
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void changed(ServerPlayer player, boolean afk, String reason) {
        SuConfig.Afk config = SuServer.config().afk;
        if (config.showInTabList) {
            Platform.get().refreshNames(player);
        }
        if (config.ignoreForSleep) {
            Mc.level(player).updateSleepingPlayerList();
        }
        String key = !afk ? "afk.back" : reason.isEmpty() ? "afk.now" : "afk.now_reason";
        List<ServerPlayer> recipients = config.broadcast && !VanishService.isVanished(player.getUUID())
            ? Mc.server(player).getPlayerList().getPlayers()
            : List.of(player);
        for (ServerPlayer recipient : recipients) {
            String name = Names.seesRealNames(recipient) ? Names.realName(player) : Names.plainDisplay(player);
            Msg.info(recipient, key, name, reason);
        }
    }

    /** Tab list entry with the AFK tag; {@code name} is SMPutilities' own entry or null for vanilla. */
    public static Component decorateTab(ServerPlayer player, Component name) {
        SuConfig.Afk config = SuServer.config().afk;
        if (SuServer.get() == null || !config.showInTabList || !isAfk(player)) {
            return name;
        }
        Component base = name != null ? name : PlayerTeam.formatNameForTeam(player.getTeam(), Texts.literal(Mc.name(player)));
        return Texts.empty().append(Msg.styled(config.tabPrefix)).append(base);
    }
}
