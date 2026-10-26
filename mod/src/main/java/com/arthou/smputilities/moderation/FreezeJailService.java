package com.arthou.smputilities.moderation;

import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.player.PlayerRecord;
import com.arthou.smputilities.core.teleport.Location;
import com.arthou.smputilities.core.util.Durations;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.teleport.Teleports;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Freezing (a player cannot move, break or use blocks or teleport away) and
 * jails (a player is kept within a radius of a jail until released or until
 * their time is up). Both are kept in the player's record, so they last
 * through relogs and restarts.
 */
public final class FreezeJailService {
    /** Where each frozen online player must stay. */
    private static final Map<UUID, Location> ANCHORS = new HashMap<>();
    private static final double FREEZE_SLACK = 0.25;

    private FreezeJailService() {
    }

    private static boolean enabled() {
        return SuServer.config().features.freezeJail;
    }

    private static PlayerRecord record(UUID id) {
        SuServer context = SuServer.get();
        return context == null ? null : context.players().get(id);
    }

    public static boolean isFrozen(ServerPlayer player) {
        return enabled() && ANCHORS.containsKey(player.getUUID());
    }

    public static boolean isJailed(ServerPlayer player) {
        PlayerRecord record = enabled() ? record(player.getUUID()) : null;
        return record != null && record.jailed != null;
    }

    /** Frozen or jailed players cannot teleport themselves away. */
    public static boolean blocksTeleport(ServerPlayer player) {
        if (isFrozen(player)) {
            Msg.error(player, "freeze.no_teleport");
            return true;
        }
        if (isJailed(player)) {
            Msg.error(player, "jail.no_teleport");
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- login

    public static void onLogin(ServerPlayer player) {
        PlayerRecord record = record(player.getUUID());
        if (record == null || !enabled()) {
            return;
        }
        if (record.frozen) {
            ANCHORS.put(player.getUUID(), Teleports.locationOf(player));
            Msg.error(player, "freeze.still");
        }
        if (record.jailed != null) {
            Msg.error(player, "jail.still", record.jailed.reason.isEmpty() ? "-" : record.jailed.reason);
        }
    }

    public static void onLogout(ServerPlayer player) {
        if (ANCHORS.remove(player.getUUID()) != null) {
            for (ServerPlayer staff : Mc.server(player).getPlayerList().getPlayers()) {
                if (Perms.has(staff, Perm.STAFF_FREEZE)) {
                    Msg.info(staff, "freeze.logged_out", Names.realName(player));
                }
            }
        }
    }

    public static void onServerStop() {
        ANCHORS.clear();
    }

    // ----------------------------------------------------------------- tick

    public static void tick(MinecraftServer server) {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        SuConfig.Jail config = SuServer.config().jail;
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            Location anchor = ANCHORS.get(player.getUUID());
            if (anchor != null) {
                Location at = Teleports.locationOf(player);
                if (!at.dimension().equals(anchor.dimension()) || at.distanceSquared(anchor) > FREEZE_SLACK) {
                    Teleports.teleport(player, new Location(anchor.dimension(), anchor.x(), anchor.y(), anchor.z(), at.yaw(), at.pitch()), false);
                }
            }
            PlayerRecord record = record(player.getUUID());
            if (record == null || record.jailed == null) {
                continue;
            }
            if (record.jailed.until > 0 && now >= record.jailed.until) {
                release(server, player.getUUID(), null);
                continue;
            }
            Location jail = config.jails.get(record.jailed.jail);
            if (jail == null) {
                continue;
            }
            Location at = Teleports.locationOf(player);
            double radius = Math.max(1, config.radius);
            if (!at.dimension().equals(jail.dimension()) || at.distanceSquared(jail) > radius * radius) {
                Teleports.teleport(player, jail, false);
            }
        }
    }

    // --------------------------------------------------------------- freeze

    /** Freezes or unfreezes; returns the new state. */
    public static boolean toggleFreeze(ServerPlayer actor, ServerPlayer target) {
        boolean frozen = !ANCHORS.containsKey(target.getUUID());
        if (frozen) {
            ANCHORS.put(target.getUUID(), Teleports.locationOf(target));
        } else {
            ANCHORS.remove(target.getUUID());
        }
        SuServer.get().players().update(target.getUUID(), record -> record.frozen = frozen);
        SuServer.get().log(actor, frozen ? "freeze" : "unfreeze", target.getUUID(), Names.realName(target), "");
        if (frozen) {
            Mc.send(target, Texts.literal(Lang.tr(target, "freeze.you")).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        } else {
            Msg.ok(target, "freeze.you_off");
        }
        return frozen;
    }

    // ----------------------------------------------------------------- jail

    /** Jails a player (online or not); {@code millis} 0 means until released. */
    public static String jail(MinecraftServer server, ServerPlayer actor, UUID target, String targetName, String jailName, long millis, String reason) {
        SuConfig config = SuServer.config();
        Location jail = config.jail.jails.get(jailName.toLowerCase(Locale.ROOT));
        if (jail == null) {
            return "jail.unknown";
        }
        ServerPlayer online = server.getPlayerList().getPlayer(target);
        long until = millis > 0 ? System.currentTimeMillis() + millis : 0;
        Location from = online == null ? null : Teleports.locationOf(online);
        SuServer.get().players().update(target, record -> {
            PlayerRecord.Jailing jailing = new PlayerRecord.Jailing();
            jailing.jail = jailName.toLowerCase(Locale.ROOT);
            jailing.reason = reason == null ? "" : reason;
            jailing.by = actor == null ? "Console" : Names.realName(actor);
            jailing.until = until;
            jailing.from = from != null ? from : record.logout;
            record.jailed = jailing;
        });
        SuServer.get().log(actor, "jail", target, targetName, jailName + (millis > 0 ? " " + Durations.format(millis) : "") + (reason == null || reason.isEmpty() ? "" : " " + reason));
        if (online != null) {
            Teleports.teleport(online, jail, false);
            Msg.error(online, "jail.you", reason == null || reason.isEmpty() ? "-" : reason, millis > 0 ? Durations.format(millis) : "∞");
        }
        return null;
    }

    public static boolean release(MinecraftServer server, UUID target, ServerPlayer actor) {
        PlayerRecord record = record(target);
        if (record == null || record.jailed == null) {
            return false;
        }
        Location from = record.jailed.from;
        SuServer.get().players().update(target, changed -> changed.jailed = null);
        SuServer.get().log(actor, "unjail", target, record.lastName, "");
        ServerPlayer online = server.getPlayerList().getPlayer(target);
        if (online != null) {
            if (from != null) {
                Teleports.teleport(online, from, false);
            }
            Msg.ok(online, "jail.released");
        }
        return true;
    }

    // ------------------------------------------------------------- commands

    private static final SuggestionProvider<CommandSourceStack> JAILS = (context, builder) ->
        SharedSuggestionProvider.suggest(SuServer.config().jail.jails.keySet(), builder);

    public static LiteralArgumentBuilder<CommandSourceStack> freezeBranch() {
        return Commands.literal("freeze")
            .requires(source -> enabled() && Perms.has(source, Perm.STAFF_FREEZE))
            .then(Commands.argument("player", StringArgumentType.string()).suggests(Names.onlineSuggestions()).executes(FreezeJailService::freezeCommand));
    }

    private static int freezeCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String input = StringArgumentType.getString(context, "player");
        Optional<Names.Target> target = Names.resolve(source.getServer(), input, Mc.player(source));
        if (target.isEmpty() || target.get().online() == null) {
            return Msg.error(source, "player.not_found_online", input);
        }
        boolean frozen = toggleFreeze(Mc.player(source), target.get().online());
        return Msg.ok(source, frozen ? "freeze.on" : "freeze.off", target.get().name());
    }

    public static LiteralArgumentBuilder<CommandSourceStack> jailBranch() {
        return Commands.literal("jail")
            .requires(source -> enabled() && Perms.has(source, Perm.STAFF_JAIL))
            .then(Commands.literal("list").executes(FreezeJailService::listJails))
            .then(Commands.literal("set").then(Commands.argument("name", StringArgumentType.word()).executes(FreezeJailService::setJail)))
            .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).suggests(JAILS).executes(FreezeJailService::removeJail)))
            .then(Commands.literal("release").then(Commands.argument("player", StringArgumentType.string()).suggests(Names.knownSuggestions())
                .executes(FreezeJailService::releaseCommand)))
            .then(Commands.literal("put").then(Commands.argument("player", StringArgumentType.string()).suggests(Names.knownSuggestions())
                .then(Commands.argument("jail", StringArgumentType.word()).suggests(JAILS).executes(context -> jailCommand(context, ""))
                    .then(Commands.argument("time_and_reason", StringArgumentType.greedyString())
                        .executes(context -> jailCommand(context, StringArgumentType.getString(context, "time_and_reason")))))));
    }

    private static int listJails(CommandContext<CommandSourceStack> context) {
        Map<String, Location> jails = SuServer.config().jail.jails;
        if (jails.isEmpty()) {
            return Msg.info(context.getSource(), "jail.none");
        }
        Msg.info(context.getSource(), "jail.list", String.join(", ", jails.keySet()));
        return 1;
    }

    private static int setJail(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "name").toLowerCase(Locale.ROOT);
        Location at = Teleports.locationOf(player);
        SuServer.configManager().edit(config -> config.jail.jails.put(name, at));
        SuServer.get().log(player, "jail.set", null, name, at.shortText());
        return Msg.ok(context.getSource(), "jail.set", name);
    }

    private static int removeJail(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name").toLowerCase(Locale.ROOT);
        if (!SuServer.config().jail.jails.containsKey(name)) {
            return Msg.error(context.getSource(), "jail.unknown", name);
        }
        SuServer.configManager().edit(config -> config.jail.jails.remove(name));
        return Msg.ok(context.getSource(), "jail.removed", name);
    }

    private static int jailCommand(CommandContext<CommandSourceStack> context, String rest) {
        CommandSourceStack source = context.getSource();
        String input = StringArgumentType.getString(context, "player");
        Optional<Names.Target> target = Names.resolve(source.getServer(), input, Mc.player(source));
        if (target.isEmpty()) {
            return Msg.error(source, "player.not_found", input);
        }
        String jailName = StringArgumentType.getString(context, "jail");
        long millis = 0;
        String reason = rest.trim();
        int space = reason.indexOf(' ');
        Long parsed = Durations.parse(space < 0 ? reason : reason.substring(0, space));
        if (parsed != null && parsed > 0) {
            millis = parsed;
            reason = space < 0 ? "" : reason.substring(space + 1).trim();
        }
        String error = jail(source.getServer(), Mc.player(source), target.get().id(), target.get().name(), jailName, millis, reason);
        if (error != null) {
            return Msg.error(source, error, jailName);
        }
        return Msg.ok(source, "jail.done", target.get().name(), jailName.toLowerCase(Locale.ROOT));
    }

    private static int releaseCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String input = StringArgumentType.getString(context, "player");
        Optional<Names.Target> target = Names.resolve(source.getServer(), input, Mc.player(source));
        if (target.isEmpty() || !release(source.getServer(), target.get().id(), Mc.player(source))) {
            return Msg.error(source, "jail.not_jailed", input);
        }
        return Msg.ok(source, "jail.released_staff", target.get().name());
    }

    /** Jail names, for the panel. */
    public static List<String> jailNames() {
        return new ArrayList<>(SuServer.config().jail.jails.keySet());
    }
}
