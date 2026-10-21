package com.arthou.smputilities.smp;

import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.player.PlayerRecord;
import com.arthou.smputilities.core.util.Durations;
import com.arthou.smputilities.mc.Ids;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.stats.PlayerStats;
import com.arthou.smputilities.vanish.VanishService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.function.Predicate;

/** /playtime, /seen and the death location message. */
public final class InfoCommands {
    private InfoCommands() {
    }

    private static boolean enabled(Predicate<SuConfig.Qol> flag) {
        SuConfig config = SuServer.config();
        return config.features.qol && flag.test(config.qol);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("playtime")
            .requires(source -> enabled(qol -> qol.playtime) && Perms.has(source, Perm.PLAYTIME))
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                PlayerStats.Values stats = PlayerStats.read(context.getSource().getServer(), player.getUUID());
                return Msg.info(context.getSource(), "playtime.self", Durations.formatTicks(stats.playTicks()));
            })
            .then(Commands.argument("player", StringArgumentType.string()).suggests(Names.knownSuggestions())
                .executes(InfoCommands::playtime)));
        dispatcher.register(Commands.literal("seen")
            .requires(source -> enabled(qol -> qol.seen) && Perms.has(source, Perm.SEEN))
            .then(Commands.argument("player", StringArgumentType.string()).suggests(Names.knownSuggestions())
                .executes(InfoCommands::seen)));
    }

    private static Optional<Names.Target> target(CommandContext<CommandSourceStack> context) {
        return Names.resolve(context.getSource().getServer(), StringArgumentType.getString(context, "player"), Mc.player(context.getSource()));
    }

    private static String shown(CommandSourceStack source, Names.Target target) {
        return Names.seesRealNames(Mc.player(source)) ? target.name() : Names.plainDisplay(target.id(), target.name());
    }

    private static int playtime(CommandContext<CommandSourceStack> context) {
        Optional<Names.Target> found = target(context);
        if (found.isEmpty()) {
            return Msg.error(context.getSource(), "player.not_found", StringArgumentType.getString(context, "player"));
        }
        PlayerStats.Values stats = PlayerStats.read(context.getSource().getServer(), found.get().id());
        return Msg.info(context.getSource(), "playtime.other", shown(context.getSource(), found.get()), Durations.formatTicks(stats.playTicks()));
    }

    private static int seen(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Optional<Names.Target> found = target(context);
        if (found.isEmpty()) {
            return Msg.error(source, "player.not_found", StringArgumentType.getString(context, "player"));
        }
        Names.Target target = found.get();
        PlayerRecord record = SuServer.get().players().get(target.id());
        long now = System.currentTimeMillis();
        ServerPlayer viewer = Mc.player(source);
        boolean visible = target.isOnline() && (viewer == null || VanishService.canSee(viewer, target.online()));
        if (visible) {
            return Msg.info(source, "seen.online", shown(source, target), Durations.format(now - record.lastLogin));
        }
        if (record.lastSeen <= 0) {
            return Msg.info(source, "seen.never", shown(source, target));
        }
        return Msg.info(source, "seen.offline", shown(source, target), Durations.format(now - record.lastSeen));
    }

    /** Tells only the player where they died, with a /back shortcut when it is available. */
    public static void deathLocation(ServerPlayer player) {
        if (!enabled(qol -> qol.deathCoordinates)) {
            return;
        }
        String dimension = Ids.dimensionOf(Mc.level(player));
        if (dimension.startsWith("minecraft:")) {
            dimension = dimension.substring("minecraft:".length());
        }
        MutableComponent line = Msg.prefix().append(Texts.literal(Lang.tr(player, "death.location",
            player.getBlockX(), player.getBlockY(), player.getBlockZ(), dimension)).withStyle(style -> Texts.color(style, Msg.WARN)));
        SuConfig config = SuServer.config();
        if (config.features.teleport && config.teleport.backOnDeath && Perms.has(player, Perm.BACK)) {
            line.append(Texts.literal(" "))
                .append(Texts.hover(Texts.clickRun(Texts.literal("[/back]").withStyle(style -> Texts.color(style, Msg.ACCENT)), "/back"),
                    Texts.literal(Lang.tr(player, "death.back_hover"))));
        }
        Mc.send(player, line);
    }
}
