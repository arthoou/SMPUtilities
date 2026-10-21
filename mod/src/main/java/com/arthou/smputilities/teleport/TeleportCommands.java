package com.arthou.smputilities.teleport;

import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.chat.ChatService;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.player.PlayerRecord;
import com.arthou.smputilities.core.teleport.Location;
import com.arthou.smputilities.core.teleport.TpaRequests;
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
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** /tpa, /home, /spawn, /back, /warp, /rtp and staff helpers (TPA mod, integrated). */
public final class TeleportCommands {
    private static final TpaRequests REQUESTS = new TpaRequests();

    private TeleportCommands() {
    }

    private static boolean enabled() {
        return SuServer.config().features.teleport;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
            .requires(source -> enabled() && Perms.has(source, Perm.TPA))
            .then(Commands.argument("target", StringArgumentType.string()).suggests(Names.onlineSuggestions())
                .executes(context -> request(context, TpaRequests.Kind.TO_TARGET))));
        for (String name : new String[] {"tpahere", "tphere"}) {
            dispatcher.register(Commands.literal(name)
                .requires(source -> enabled() && Perms.has(source, Perm.TPA))
                .then(Commands.argument("target", StringArgumentType.string()).suggests(Names.onlineSuggestions())
                    .executes(context -> request(context, TpaRequests.Kind.HERE))));
        }
        dispatcher.register(Commands.literal("tpacancel")
            .requires(source -> enabled() && Perms.has(source, Perm.TPA))
            .executes(TeleportCommands::cancel));
        dispatcher.register(Commands.literal("tpatoggle")
            .requires(source -> enabled() && Perms.has(source, Perm.TPA))
            .executes(TeleportCommands::toggle));
        dispatcher.register(Commands.literal("tpaccept")
            .requires(source -> enabled() && Perms.has(source, Perm.TPA))
            .executes(context -> accept(context, null))
            .then(Commands.argument("target", StringArgumentType.string()).suggests(Names.onlineSuggestions())
                .executes(context -> accept(context, StringArgumentType.getString(context, "target")))));
        for (String name : new String[] {"tpdeny", "tpadeny"}) {
            dispatcher.register(Commands.literal(name)
                .requires(source -> enabled() && Perms.has(source, Perm.TPA))
                .executes(context -> deny(context, null))
                .then(Commands.argument("target", StringArgumentType.string()).suggests(Names.onlineSuggestions())
                    .executes(context -> deny(context, StringArgumentType.getString(context, "target")))));
        }

        dispatcher.register(Commands.literal("home")
            .requires(source -> enabled() && Perms.has(source, Perm.HOME))
            .executes(context -> home(context, null))
            .then(Commands.argument("name", StringArgumentType.word()).suggests(homeSuggestions())
                .executes(context -> home(context, StringArgumentType.getString(context, "name")))));
        dispatcher.register(Commands.literal("homes")
            .requires(source -> enabled() && Perms.has(source, Perm.HOME))
            .executes(TeleportCommands::listHomes));
        dispatcher.register(Commands.literal("sethome")
            .requires(source -> enabled() && Perms.has(source, Perm.HOME))
            .executes(context -> setHome(context, "home"))
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(context -> setHome(context, StringArgumentType.getString(context, "name")))));
        dispatcher.register(Commands.literal("delhome")
            .requires(source -> enabled() && Perms.has(source, Perm.HOME))
            .then(Commands.argument("name", StringArgumentType.word()).suggests(homeSuggestions())
                .executes(TeleportCommands::deleteHome)));
        dispatcher.register(Commands.literal("setdefaulthome")
            .requires(source -> enabled() && Perms.has(source, Perm.HOME))
            .then(Commands.argument("name", StringArgumentType.word()).suggests(homeSuggestions())
                .executes(TeleportCommands::setDefaultHome)));

        dispatcher.register(Commands.literal("spawn")
            .requires(source -> enabled() && Perms.has(source, Perm.SPAWN))
            .executes(TeleportCommands::spawn));
        dispatcher.register(Commands.literal("setspawn")
            .requires(source -> enabled() && Perms.has(source, Perm.SET_SPAWN))
            .executes(TeleportCommands::setSpawn));
        dispatcher.register(Commands.literal("back")
            .requires(source -> enabled() && Perms.has(source, Perm.BACK))
            .executes(TeleportCommands::back));

        dispatcher.register(Commands.literal("warp")
            .requires(source -> enabled() && Perms.has(source, Perm.WARP))
            .executes(TeleportCommands::listWarps)
            .then(Commands.argument("name", StringArgumentType.word()).suggests(warpSuggestions())
                .executes(TeleportCommands::warp)));
        dispatcher.register(Commands.literal("warps")
            .requires(source -> enabled() && Perms.has(source, Perm.WARP))
            .executes(TeleportCommands::listWarps));
        dispatcher.register(Commands.literal("setwarp")
            .requires(source -> enabled() && Perms.has(source, Perm.WARP_MANAGE))
            .then(Commands.argument("name", StringArgumentType.word()).executes(TeleportCommands::setWarp)));
        dispatcher.register(Commands.literal("delwarp")
            .requires(source -> enabled() && Perms.has(source, Perm.WARP_MANAGE))
            .then(Commands.argument("name", StringArgumentType.word()).suggests(warpSuggestions()).executes(TeleportCommands::deleteWarp)));
        dispatcher.register(Commands.literal("rtp")
            .requires(source -> enabled() && Perms.has(source, Perm.RTP))
            .executes(TeleportCommands::rtp));

        dispatcher.register(Commands.literal("tpall")
            .requires(source -> enabled() && Perms.has(source, Perm.STAFF_TPALL))
            .executes(TeleportCommands::tpAll));
        dispatcher.register(Commands.literal("tplogout")
            .requires(source -> enabled() && Perms.has(source, Perm.STAFF_TELEPORT))
            .then(Commands.argument("target", StringArgumentType.string()).suggests(Names.knownSuggestions())
                .executes(TeleportCommands::tpLogout)));
    }

    // ---------------------------------------------------------------- tpa

    private static int request(CommandContext<CommandSourceStack> context, TpaRequests.Kind kind) throws CommandSyntaxException {
        ServerPlayer requester = context.getSource().getPlayerOrException();
        String input = StringArgumentType.getString(context, "target");
        Optional<Names.Target> found = Names.resolve(Mc.server(requester), input, requester);
        if (found.isEmpty() || !found.get().isOnline()) {
            return Msg.error(context.getSource(), "player.not_found_online", input);
        }
        ServerPlayer target = found.get().online();
        if (target == requester) {
            return Msg.error(context.getSource(), "tpa.self");
        }
        if (ChatService.isIgnoring(target, requester)) {
            return Msg.error(context.getSource(), "tpa.ignored");
        }
        if (SuServer.get().players().get(target.getUUID()).tpaDisabled && !Perms.has(requester, Perm.STAFF_TELEPORT)) {
            return Msg.error(context.getSource(), "tpa.target_disabled");
        }
        long timeout = SuServer.config().teleport.requestTimeoutSeconds * 1000L;
        long now = System.currentTimeMillis();
        if (REQUESTS.hasPending(requester.getUUID(), target.getUUID(), now, timeout)) {
            return Msg.error(context.getSource(), "tpa.already_sent");
        }
        REQUESTS.add(new TpaRequests.Request(requester.getUUID(), Names.realName(requester), target.getUUID(), kind, now));
        String targetShown = Names.seesRealNames(requester) ? Names.realName(target) : Names.plainDisplay(target);
        String requesterShown = Names.seesRealNames(target) ? Names.realName(requester) : Names.plainDisplay(requester);
        Msg.ok(requester, kind == TpaRequests.Kind.TO_TARGET ? "tpa.sent" : "tpa.sent_here", targetShown, SuServer.config().teleport.requestTimeoutSeconds);
        Msg.info(target, kind == TpaRequests.Kind.TO_TARGET ? "tpa.received" : "tpa.received_here", requesterShown);
        Mc.send(target, decisionRow(target, Names.realName(requester)));
        Mc.playSound(target, "minecraft:block.note_block.hat", SoundSource.PLAYERS, 3.0F, 1.0F);
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer requester = context.getSource().getPlayerOrException();
        List<TpaRequests.Request> cancelled = REQUESTS.cancel(requester.getUUID(), System.currentTimeMillis(),
            SuServer.config().teleport.requestTimeoutSeconds * 1000L);
        if (cancelled.isEmpty()) {
            return Msg.error(context.getSource(), "tpa.nothing_to_cancel");
        }
        for (TpaRequests.Request request : cancelled) {
            ServerPlayer target = Mc.server(requester).getPlayerList().getPlayer(request.target());
            if (target != null) {
                String requesterShown = Names.seesRealNames(target) ? Names.realName(requester) : Names.plainDisplay(requester);
                Msg.info(target, "tpa.cancelled_by", requesterShown);
            }
        }
        return Msg.ok(context.getSource(), "tpa.cancelled", cancelled.size());
    }

    private static int toggle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean[] disabled = new boolean[1];
        SuServer.get().players().update(player.getUUID(), record -> disabled[0] = record.tpaDisabled = !record.tpaDisabled);
        return Msg.ok(context.getSource(), disabled[0] ? "tpa.toggle_off" : "tpa.toggle_on");
    }

    private static Component decisionRow(ServerPlayer viewer, String requesterName) {
        MutableComponent accept = Texts.literal("[" + Lang.tr(viewer, "tpa.accept_button") + "]").withStyle(style -> style
            .withColor(ChatFormatting.GREEN).withBold(true)
            .withClickEvent(Texts.runCommand("/tpaccept " + requesterName))
            .withHoverEvent(Texts.showText(Texts.literal(Lang.tr(viewer, "tpa.accept_hover")))));
        MutableComponent deny = Texts.literal("[" + Lang.tr(viewer, "tpa.deny_button") + "]").withStyle(style -> style
            .withColor(ChatFormatting.RED).withBold(true)
            .withClickEvent(Texts.runCommand("/tpdeny " + requesterName))
            .withHoverEvent(Texts.showText(Texts.literal(Lang.tr(viewer, "tpa.deny_hover")))));
        return Msg.prefix().append(accept).append(Texts.literal("  ")).append(deny);
    }

    private static int accept(CommandContext<CommandSourceStack> context, String from) throws CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        Optional<TpaRequests.Request> request = take(target, from);
        if (request.isEmpty()) {
            return Msg.error(context.getSource(), "tpa.none");
        }
        ServerPlayer requester = Mc.server(target).getPlayerList().getPlayer(request.get().requester());
        if (requester == null) {
            return Msg.error(context.getSource(), "tpa.requester_offline");
        }
        int cooldown = SuServer.config().teleport.tpaCooldownSeconds;
        boolean started;
        if (request.get().kind() == TpaRequests.Kind.TO_TARGET) {
            UUID targetId = target.getUUID();
            started = Teleports.schedule(requester, "tpa", cooldown, () -> {
                ServerPlayer current = Mc.server(requester).getPlayerList().getPlayer(targetId);
                return current == null ? null : Teleports.locationOf(current);
            }, true, null);
        } else {
            UUID requesterId = requester.getUUID();
            started = Teleports.schedule(target, "tpa", cooldown, () -> {
                ServerPlayer current = Mc.server(target).getPlayerList().getPlayer(requesterId);
                return current == null ? null : Teleports.locationOf(current);
            }, true, null);
        }
        if (started) {
            Msg.ok(target, "tpa.accepted");
            Msg.ok(requester, "tpa.accepted_by", Names.seesRealNames(requester) ? Names.realName(target) : Names.plainDisplay(target));
        }
        return started ? 1 : 0;
    }

    private static int deny(CommandContext<CommandSourceStack> context, String from) throws CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        Optional<TpaRequests.Request> request = take(target, from);
        if (request.isEmpty()) {
            return Msg.error(context.getSource(), "tpa.none");
        }
        ServerPlayer requester = Mc.server(target).getPlayerList().getPlayer(request.get().requester());
        if (requester != null) {
            Msg.error(requester, "tpa.denied_by", Names.seesRealNames(requester) ? Names.realName(target) : Names.plainDisplay(target));
        }
        return Msg.ok(context.getSource(), "tpa.denied");
    }

    private static Optional<TpaRequests.Request> take(ServerPlayer target, String from) {
        long timeout = SuServer.config().teleport.requestTimeoutSeconds * 1000L;
        UUID requester = null;
        if (from != null) {
            Optional<Names.Target> found = Names.resolve(Mc.server(target), from, target);
            if (found.isEmpty()) {
                return Optional.empty();
            }
            requester = found.get().id();
        }
        return REQUESTS.take(target.getUUID(), requester, System.currentTimeMillis(), timeout);
    }

    public static void forget(ServerPlayer player) {
        REQUESTS.forget(player.getUUID());
    }

    // -------------------------------------------------------------- homes

    private static SuggestionProvider<CommandSourceStack> homeSuggestions() {
        return (context, builder) -> {
            ServerPlayer player = Mc.player(context.getSource());
            if (player == null || SuServer.get() == null) {
                return builder.buildFuture();
            }
            return SharedSuggestionProvider.suggest(SuServer.get().players().get(player.getUUID()).homes.keySet(), builder);
        };
    }

    private static int home(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerRecord record = SuServer.get().players().get(player.getUUID());
        String homeName = name;
        if (homeName == null) {
            if (!record.defaultHome.isEmpty() && record.homes.containsKey(record.defaultHome)) {
                homeName = record.defaultHome;
            } else if (record.homes.size() == 1) {
                homeName = record.homes.keySet().iterator().next();
            } else if (record.homes.containsKey("home")) {
                homeName = "home";
            } else {
                return Msg.error(context.getSource(), record.homes.isEmpty() ? "home.none" : "home.choose");
            }
        }
        Location location = record.homes.get(homeName);
        if (location == null) {
            return Msg.error(context.getSource(), "home.not_found", homeName);
        }
        String shown = homeName;
        return Teleports.schedule(player, "home", SuServer.config().teleport.homeCooldownSeconds, () -> location, true,
            () -> Msg.ok(player, "home.teleported", shown)) ? 1 : 0;
    }

    private static int listHomes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerRecord record = SuServer.get().players().get(player.getUUID());
        if (record.homes.isEmpty()) {
            return Msg.info(context.getSource(), "home.none");
        }
        MutableComponent line = Msg.prefix().append(Texts.literal(Lang.tr(player, "home.list_header", record.homes.size(), limitText(player))).withStyle(ChatFormatting.GRAY));
        for (String name : record.homes.keySet()) {
            line.append(Texts.literal(" "));
            line.append(Texts.literal("[" + name + (name.equals(record.defaultHome) ? "*" : "") + "]").withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(Texts.runCommand("/home " + name))
                .withHoverEvent(Texts.showText(Texts.literal(record.homes.get(name).shortText())))));
        }
        Mc.send(player, line);
        return 1;
    }

    private static String limitText(ServerPlayer player) {
        int limit = Perms.homeLimit(player);
        return limit == Integer.MAX_VALUE ? "∞" : String.valueOf(limit);
    }

    private static int setHome(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!name.matches("[A-Za-z0-9_\\-]{1,24}")) {
            return Msg.error(context.getSource(), "home.invalid_name");
        }
        PlayerRecord record = SuServer.get().players().get(player.getUUID());
        if (!record.homes.containsKey(name) && record.homes.size() >= Perms.homeLimit(player)) {
            return Msg.error(context.getSource(), "home.limit", limitText(player));
        }
        java.util.List<String> blocked = SuServer.config().teleport.blockedInDimensions.get("sethome");
        if (blocked != null && blocked.contains(player.level().dimension().location().toString()) && !Perms.has(player, Perm.TELEPORT_BYPASS_DIMENSION)) {
            return Msg.error(context.getSource(), "teleport.blocked_here");
        }
        Location here = Teleports.locationOf(player);
        SuServer.get().players().update(player.getUUID(), value -> {
            value.homes.put(name, here);
            if (value.defaultHome.isEmpty()) {
                value.defaultHome = name;
            }
        });
        return Msg.ok(context.getSource(), "home.set", name);
    }

    private static int deleteHome(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "name");
        boolean[] removed = new boolean[1];
        SuServer.get().players().update(player.getUUID(), record -> {
            removed[0] = record.homes.remove(name) != null;
            if (name.equals(record.defaultHome)) {
                record.defaultHome = "";
            }
        });
        return removed[0] ? Msg.ok(context.getSource(), "home.deleted", name) : Msg.error(context.getSource(), "home.not_found", name);
    }

    private static int setDefaultHome(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "name");
        if (!SuServer.get().players().get(player.getUUID()).homes.containsKey(name)) {
            return Msg.error(context.getSource(), "home.not_found", name);
        }
        SuServer.get().players().update(player.getUUID(), record -> record.defaultHome = name);
        return Msg.ok(context.getSource(), "home.default_set", name);
    }

    // ----------------------------------------------------- spawn, back, warp

    private static int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Location spawn = spawnLocation(player);
        return Teleports.schedule(player, "spawn", SuServer.config().teleport.spawnCooldownSeconds, () -> spawn, true,
            () -> Msg.ok(player, "spawn.teleported")) ? 1 : 0;
    }

    /** The configured spawn, or the world spawn when none was set. */
    public static Location spawnLocation(ServerPlayer player) {
        Location spawn = SuServer.get().teleports().spawn();
        if (spawn != null) {
            return spawn;
        }
        Mc.Spawn world = Mc.worldSpawn(Mc.server(player));
        BlockPos pos = world.pos();
        return new Location(world.dimension(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.yaw(), 0);
    }

    private static int setSpawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        SuServer.get().teleports().setSpawn(Teleports.locationOf(player));
        SuServer.get().log(player, "spawn.set", null, "", Teleports.locationOf(player).shortText());
        return Msg.ok(context.getSource(), "spawn.set");
    }

    private static int back(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Location back = SuServer.get().players().get(player.getUUID()).back;
        if (back == null) {
            return Msg.error(context.getSource(), "back.none");
        }
        return Teleports.schedule(player, "back", SuServer.config().teleport.backCooldownSeconds, () -> back, true,
            () -> Msg.ok(player, "back.teleported")) ? 1 : 0;
    }

    private static SuggestionProvider<CommandSourceStack> warpSuggestions() {
        return (context, builder) -> SuServer.get() == null ? builder.buildFuture()
            : SharedSuggestionProvider.suggest(SuServer.get().teleports().warps().keySet(), builder);
    }

    private static int warp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "name");
        Location warp = SuServer.get().teleports().warps().get(name);
        if (warp == null) {
            return Msg.error(context.getSource(), "warp.not_found", name);
        }
        return Teleports.schedule(player, "warp", SuServer.config().teleport.warpCooldownSeconds, () -> warp, true,
            () -> Msg.ok(player, "warp.teleported", name)) ? 1 : 0;
    }

    private static int listWarps(CommandContext<CommandSourceStack> context) {
        Map<String, Location> warps = SuServer.get().teleports().warps();
        if (warps.isEmpty()) {
            return Msg.info(context.getSource(), "warp.none");
        }
        ServerPlayer player = Mc.player(context.getSource());
        MutableComponent line = Msg.prefix().append(Texts.literal(Lang.tr(player, "warp.list_header")).withStyle(ChatFormatting.GRAY));
        for (String name : warps.keySet()) {
            line.append(Texts.literal(" "));
            line.append(Texts.literal("[" + name + "]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withClickEvent(Texts.runCommand("/warp " + name))));
        }
        Mc.reply(context.getSource(), line);
        return 1;
    }

    private static int setWarp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "name");
        SuServer.get().teleports().setWarp(name, Teleports.locationOf(player));
        SuServer.get().log(player, "warp.set", null, name, Teleports.locationOf(player).shortText());
        return Msg.ok(context.getSource(), "warp.set", name);
    }

    private static int deleteWarp(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!SuServer.get().teleports().removeWarp(name)) {
            return Msg.error(context.getSource(), "warp.not_found", name);
        }
        SuServer.get().log(Mc.player(context.getSource()), "warp.delete", null, name, "");
        return Msg.ok(context.getSource(), "warp.deleted", name);
    }

    private static int rtp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        if (level.dimensionType().hasCeiling()) {
            return Msg.error(context.getSource(), "rtp.not_here");
        }
        SuConfig.Teleport config = SuServer.config().teleport;
        return Teleports.schedule(player, "rtp", config.rtpCooldownSeconds, () -> findSafeSpot(level, config.rtpRadius), true,
            () -> Msg.ok(player, "rtp.teleported")) ? 1 : 0;
    }

    private static Location findSafeSpot(ServerLevel level, int radius) {
        BlockPos center = Mc.worldSpawn(level.getServer()).pos();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 12; attempt++) {
            int x = center.getX() + random.nextInt(-radius, radius + 1);
            int z = center.getZ() + random.nextInt(-radius, radius + 1);
            if (!level.getWorldBorder().isWithinBounds(x, z)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos ground = new BlockPos(x, y - 1, z);
            if (y <= Mc.minY(level) + 1 || !level.getFluidState(ground).isEmpty() || !Mc.isSolid(level.getBlockState(ground))) {
                continue;
            }
            return new Location(level.dimension().location().toString(), x + 0.5, y, z + 0.5, 0, 0);
        }
        return null;
    }

    private static int tpAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Location here = Teleports.locationOf(player);
        int count = 0;
        for (ServerPlayer online : Mc.server(player).getPlayerList().getPlayers()) {
            if (online != player && !VanishService.isVanished(online.getUUID()) && Teleports.teleport(online, here, true)) {
                count++;
            }
        }
        SuServer.get().log(player, "tpall", null, "", count + " players");
        return Msg.ok(context.getSource(), "tpall.done", count);
    }

    private static int tpLogout(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String input = StringArgumentType.getString(context, "target");
        Optional<Names.Target> target = Names.resolve(Mc.server(player), input, player);
        if (target.isEmpty()) {
            return Msg.error(context.getSource(), "player.not_found", input);
        }
        Location logout = SuServer.get().players().get(target.get().id()).logout;
        if (logout == null) {
            return Msg.error(context.getSource(), "tplogout.none");
        }
        return Teleports.teleport(player, logout, true) ? 1 : 0;
    }
}
