package com.arthou.smputilities.staff;

import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.backup.BackupMenus;
import com.arthou.smputilities.backup.BackupService;
import com.arthou.smputilities.backup.PlayerSnapshot;
import com.arthou.smputilities.backup.SnapshotType;
import com.arthou.smputilities.chat.ChatCommands;
import com.arthou.smputilities.chat.ChatService;
import com.arthou.smputilities.core.chat.StyledText;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.dimension.DimensionRules;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.portal.PortalRecord;
import com.arthou.smputilities.core.portal.PortalRegistry;
import com.arthou.smputilities.core.punish.Punishment;
import com.arthou.smputilities.core.util.Durations;
import com.arthou.smputilities.inventory.InventoryViews;
import com.arthou.smputilities.inventory.PlayerState;
import com.arthou.smputilities.moderation.PunishmentService;
import com.arthou.smputilities.modsync.ModSyncServer;
import com.arthou.smputilities.network.SuNetwork;
import com.arthou.smputilities.nick.NicknameService;
import com.arthou.smputilities.profile.ProfileMenus;
import com.arthou.smputilities.profile.ProfileService;
import com.arthou.smputilities.profile.ProfileStore;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.smp.ReportService;
import com.arthou.smputilities.smp.RestartService;
import com.arthou.smputilities.troll.TrollService;
import com.arthou.smputilities.tts.TtsPresets;
import com.arthou.smputilities.tts.TtsService;
import com.arthou.smputilities.vanish.VanishService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** The /su command tree. Every node checks its permission on the server. */
public final class StaffCommands {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private StaffCommands() {
    }

    private interface Body {
        int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    private static Predicate<CommandSourceStack> perm(Perm perm) {
        return Perms.require(perm);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> player(String name, boolean offline) {
        return Commands.argument(name, StringArgumentType.string()).suggests(offline ? Names.knownSuggestions() : Names.onlineSuggestions());
    }

    private static Names.Target target(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        String input = StringArgumentType.getString(context, argument);
        Optional<Names.Target> found = Names.resolve(context.getSource().getServer(), input, Mc.player(context.getSource()));
        if (found.isEmpty()) {
            throw new SimpleError(Lang.tr(Mc.player(context.getSource()), "player.not_found", input));
        }
        return found.get();
    }

    private static ServerPlayer onlineTarget(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        Names.Target found = target(context, argument);
        if (!found.isOnline()) {
            throw new SimpleError(Lang.tr(Mc.player(context.getSource()), "player.not_found_online", found.name()));
        }
        return found.online();
    }

    /** Lets helpers fail with a translated message through Brigadier's normal error path. */
    private static final class SimpleError extends CommandSyntaxException {
        SimpleError(String message) {
            super(new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Texts.literal(message)), Texts.literal(message));
        }
    }

    private static String shown(CommandSourceStack source, Names.Target target) {
        return Names.seesRealNames(Mc.player(source)) ? target.name() : Names.plainDisplay(target.id(), target.name());
    }

    // ================================================================ tree

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("su")
            .executes(StaffCommands::help)
            .then(Commands.literal("help").executes(StaffCommands::help))
            .then(staff())
            .then(Commands.literal("reload").requires(perm(Perm.ADMIN_RELOAD)).executes(context -> {
                reload(context.getSource().getServer(), Mc.player(context.getSource()));
                return Msg.ok(context.getSource(), "staff.reloaded");
            }))
            .then(Commands.literal("say").requires(perm(Perm.STAFF_ANNOUNCE))
                .then(Commands.argument("message", StringArgumentType.greedyString()).executes(context -> {
                    ChatService.serverAnnouncement(context.getSource().getServer(), StringArgumentType.getString(context, "message"));
                    SuServer.get().log(Mc.player(context.getSource()), "say", null, "", StringArgumentType.getString(context, "message"));
                    return 1;
                })))
            .then(chat())
            .then(ChatCommands.mentionsBranch())
            .then(Commands.literal("realnames").requires(perm(Perm.STAFF_REALNAMES))
                .executes(context -> realNames(context, null))
                .then(Commands.literal("on").executes(context -> realNames(context, true)))
                .then(Commands.literal("off").executes(context -> realNames(context, false))))
            .then(Commands.literal("socialspy").requires(perm(Perm.CHAT_MANAGE)).executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                boolean[] now = new boolean[1];
                SuServer.get().players().update(player.getUUID(), record -> now[0] = record.socialSpy = !record.socialSpy);
                return Msg.ok(context.getSource(), now[0] ? "socialspy.on" : "socialspy.off");
            }))
            .then(nick())
            .then(Commands.literal("whois").requires(perm(Perm.STAFF_REALNAMES))
                .then(player("player", true).executes(context -> {
                    Names.Target found = target(context, "player");
                    String nick = Names.nickname(found.id());
                    return Msg.info(context.getSource(), "nick.whois", nick.isEmpty() ? found.name() : StyledText.strip(nick), found.name() + " (" + found.id() + ")");
                })))
            .then(inventory())
            .then(Commands.literal("enderchest").requires(perm(Perm.STAFF_INVENTORY))
                .then(player("player", true).executes(context -> InventoryViews.openEnderChest(context.getSource().getPlayerOrException(), target(context, "player")) ? 1 : 0)))
            .then(profile())
            .then(moderation("ban", Perm.STAFF_BAN))
            .then(moderation("mute", Perm.STAFF_MUTE))
            .then(moderation("voicemute", Perm.STAFF_VOICE_MUTE))
            .then(Commands.literal("unban").requires(perm(Perm.STAFF_BAN)).then(player("player", true).executes(context -> {
                Names.Target found = target(context, "player");
                return PunishmentService.unban(context.getSource().getServer(), Mc.player(context.getSource()), found, "")
                    ? Msg.ok(context.getSource(), "punish.done.unban", shown(context.getSource(), found))
                    : Msg.error(context.getSource(), "punish.not_banned", shown(context.getSource(), found));
            })))
            .then(unmute("unmute", Perm.STAFF_MUTE, false))
            .then(unmute("voiceunmute", Perm.STAFF_VOICE_MUTE, true))
            .then(Commands.literal("kick").requires(perm(Perm.STAFF_KICK)).then(player("player", false)
                .executes(context -> kick(context, ""))
                .then(Commands.argument("reason", StringArgumentType.greedyString()).executes(context -> kick(context, StringArgumentType.getString(context, "reason"))))))
            .then(Commands.literal("warn").requires(perm(Perm.STAFF_WARN)).then(player("player", true)
                .then(Commands.argument("reason", StringArgumentType.greedyString()).executes(context -> {
                    Names.Target found = target(context, "player");
                    PunishmentService.warn(context.getSource().getServer(), Mc.player(context.getSource()), found, StringArgumentType.getString(context, "reason"));
                    return Msg.ok(context.getSource(), "punish.done.warn", shown(context.getSource(), found));
                }))))
            .then(Commands.literal("history").requires(perm(Perm.STAFF_HISTORY)).then(player("player", true).executes(StaffCommands::history)))
            .then(portal())
            .then(dimension())
            .then(troll())
            .then(tts())
            .then(Commands.literal("silentjoin").requires(perm(Perm.CHAT_MANAGE)).then(player("player", true).executes(context -> silent(context, true))))
            .then(Commands.literal("silentquit").requires(perm(Perm.CHAT_MANAGE)).then(player("player", true).executes(context -> silent(context, false))))
            .then(Commands.literal("announce").requires(perm(Perm.STAFF_ANNOUNCE))
                .then(Commands.literal("action").executes(context -> announce(context, TrollService.Action.TITLE_ACTION, ""))
                    .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(context -> announce(context, TrollService.Action.TITLE_ACTION, StringArgumentType.getString(context, "text")))))
                .then(Commands.literal("death").then(Commands.argument("text", StringArgumentType.greedyString())
                    .executes(context -> announce(context, TrollService.Action.TITLE_DEATH, StringArgumentType.getString(context, "text"))))))
            .then(Commands.literal("modsync").requires(perm(Perm.MODSYNC_MANAGE))
                .then(Commands.literal("rescan").executes(context -> {
                    Thread thread = new Thread(ModSyncServer::rescan, "SMPutilities-modsync-scan");
                    thread.setDaemon(true);
                    thread.start();
                    return Msg.ok(context.getSource(), "modsync.rescanning");
                })))
            .then(ReportService.branch())
            .then(RestartService.branch())
            .then(Commands.literal("spectate").requires(perm(Perm.STAFF_SPECTATE))
                .then(Commands.literal("stop").executes(context -> {
                    StaffService.stopSpectating(context.getSource().getPlayerOrException());
                    return 1;
                })));
        dispatcher.register(root);

        dispatcher.register(Commands.literal("vanish").requires(source -> SuServer.config().features.vanish && Perms.has(source, Perm.STAFF_VANISH))
            .executes(context -> vanish(context, null, null))
            .then(Commands.literal("on").executes(context -> vanish(context, null, true)))
            .then(Commands.literal("off").executes(context -> vanish(context, null, false)))
            .then(player("player", false).requires(perm(Perm.STAFF_VANISH_OTHERS))
                .executes(context -> vanish(context, onlineTarget(context, "player"), null))));

        // Commands of the standalone mods, kept so staff habits keep working.
        dispatcher.register(Commands.literal("invprofile").requires(source -> SuServer.config().features.profiles && Perms.has(source, Perm.PROFILE_USE))
            .executes(context -> {
                ProfileMenus.openQuickChange(context.getSource().getPlayerOrException());
                return 1;
            }));
        dispatcher.register(Commands.literal("view").requires(perm(Perm.STAFF_INVENTORY))
            .then(Commands.literal("inv").then(player("target", true).executes(context -> openInventory(context, "target")))
                .then(Commands.literal("modify").then(player("target", true).executes(context -> openInventory(context, "target")))))
            .then(Commands.literal("echest").then(player("target", true).executes(context -> InventoryViews.openEnderChest(context.getSource().getPlayerOrException(), target(context, "target")) ? 1 : 0))
                .then(Commands.literal("modify").then(player("target", true).executes(context -> InventoryViews.openEnderChest(context.getSource().getPlayerOrException(), target(context, "target")) ? 1 : 0))))
            .then(Commands.literal("curios").then(player("target", false).executes(context -> InventoryViews.openCurios(context.getSource().getPlayerOrException(), onlineTarget(context, "target")) ? 1 : 0)))
            .then(Commands.literal("cosmetic").then(player("target", false).executes(context -> InventoryViews.openCosmetic(context.getSource().getPlayerOrException(), onlineTarget(context, "target")) ? 1 : 0))));
        dispatcher.register(Commands.literal("inventoryrollback").requires(perm(Perm.STAFF_INVENTORY))
            .then(Commands.literal("forcebackup").then(player("target", true).executes(StaffCommands::backup)))
            .then(Commands.literal("restore").then(player("target", true).executes(context -> openBackups(context, BackupMenus.Mode.VANILLA))
                .then(Commands.argument("index", IntegerArgumentType.integer(1)).requires(perm(Perm.STAFF_INVENTORY_RESTORE))
                    .executes(context -> restore(context, String.valueOf(IntegerArgumentType.getInteger(context, "index")), BackupService.FULL)))))
            .then(Commands.literal("curios").then(player("target", true).executes(context -> openBackups(context, BackupMenus.Mode.CURIOS))))
            .then(Commands.literal("cosmetic").then(player("target", true).executes(context -> openBackups(context, BackupMenus.Mode.COSMETIC))))
            .then(Commands.literal("latest").requires(perm(Perm.STAFF_INVENTORY_RESTORE))
                .then(player("target", true).executes(context -> restore(context, "1", BackupService.FULL)))));
        dispatcher.register(tts());
        // DuTTS used /dutts.
        dispatcher.register(Commands.literal("dutts").requires(source -> SuServer.config().features.tts && source.getEntity() instanceof ServerPlayer)
            .executes(StaffCommands::ttsMenu)
            .then(Commands.literal("off").executes(StaffCommands::ttsOff))
            .then(ttsVoice("feminine", 3))
            .then(ttsVoice("masculine", 2)));
    }

    // ================================================================ help

    private static int help(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = Mc.player(source);
        List<String> lines = new ArrayList<>();
        lines.add("help.header");
        if (Perms.has(source, Perm.STAFF_PANEL)) {
            lines.add("help.staff");
        }
        if (Perms.has(source, Perm.STAFF_BAN) || Perms.has(source, Perm.STAFF_MUTE)) {
            lines.add("help.moderation");
        }
        if (Perms.has(source, Perm.STAFF_INVENTORY)) {
            lines.add("help.inventory");
        }
        if (Perms.has(source, Perm.CHAT_MANAGE)) {
            lines.add("help.chat");
        }
        if (Perms.has(source, Perm.PORTAL_MANAGE) || Perms.has(source, Perm.DIMENSION_MANAGE)) {
            lines.add("help.world");
        }
        lines.add("help.player");
        for (String key : lines) {
            Mc.reply(source, Texts.literal(Lang.tr(player, key)).withStyle(key.equals("help.header") ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        return 1;
    }

    // =============================================================== staff

    private static LiteralArgumentBuilder<CommandSourceStack> staff() {
        return Commands.literal("staff").requires(source -> SuServer.config().features.staffPanel && Perms.has(source, Perm.STAFF_PANEL))
            .executes(context -> openPanel(context, null))
            .then(player("player", true).executes(context -> openPanel(context, target(context, "player"))));
    }

    private static int openPanel(CommandContext<CommandSourceStack> context, Names.Target target) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!StaffService.openPanel(player, target == null ? "dashboard" : "player", target == null ? "" : target.id().toString())) {
            return Msg.error(context.getSource(), "staff.client_missing");
        }
        return 1;
    }

    public static void reload(MinecraftServer server, ServerPlayer actor) {
        SuServer.configManager().load();
        if (SuServer.get() != null) {
            SuServer.get().log(actor, "reload", null, "", "");
        }
        StaffService.afterSettingChange(server, "features.reload");
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            com.arthou.smputilities.platform.Platform.get().refreshNames(player);
        }
    }

    // ================================================================ chat

    private static LiteralArgumentBuilder<CommandSourceStack> chat() {
        return Commands.literal("chat").requires(perm(Perm.CHAT_MANAGE))
            .then(Commands.literal("lock").executes(context -> chatLock(context, true)))
            .then(Commands.literal("unlock").executes(context -> chatLock(context, false)))
            .then(Commands.literal("clear").executes(context -> {
                clearChat(context.getSource().getServer(), Mc.player(context.getSource()));
                return 1;
            }))
            .then(toggle("format", "chat.customFormat"))
            .then(toggle("joinmessages", "chat.joinMessages"))
            .then(toggle("quitmessages", "chat.quitMessages"))
            .then(toggle("privatemessages", "chat.privateMessagesEnabled"))
            .then(toggle("pmwhilelocked", "chat.allowPrivateMessagesWhileLocked"))
            .then(toggle("rolecolors", "chat.roleColors"))
            .then(toggle("mentions", "chat.mentions.enabled"))
            .then(Commands.literal("cooldown").then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                .executes(context -> setting(context, "chat.cooldownSeconds", String.valueOf(IntegerArgumentType.getInteger(context, "seconds"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> toggle(String literal, String path) {
        return Commands.literal(literal)
            .then(Commands.literal("on").executes(context -> setting(context, path, "true")))
            .then(Commands.literal("off").executes(context -> setting(context, path, "false")));
    }

    private static int setting(CommandContext<CommandSourceStack> context, String path, String value) {
        String[] error = new String[1];
        SuServer.configManager().edit(config -> error[0] = com.arthou.smputilities.core.config.ConfigPaths.set(config, path, value));
        if (error[0] != null) {
            return Msg.error(context.getSource(), "settings." + error[0], path);
        }
        SuServer.get().log(Mc.player(context.getSource()), "setting", null, path, value);
        StaffService.afterSettingChange(context.getSource().getServer(), path);
        return Msg.ok(context.getSource(), "settings.changed", path, value);
    }

    private static int chatLock(CommandContext<CommandSourceStack> context, boolean lock) {
        SuServer.configManager().edit(config -> config.chat.globalChatEnabled = !lock);
        SuServer.get().log(Mc.player(context.getSource()), lock ? "chat.lock" : "chat.unlock", null, "", "");
        Msg.broadcastStaffAware(lock ? "chat.lock.on" : "chat.lock.off", lock ? "chat.lock.on.public" : "chat.lock.off.public", context.getSource().getTextName());
        return 1;
    }

    public static void clearChat(MinecraftServer server, ServerPlayer actor) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (Perms.has(player, Perm.CHAT_MANAGE)) {
                continue;
            }
            for (int i = 0; i < 100; i++) {
                Mc.send(player, Texts.empty());
            }
        }
        Msg.broadcast("chat.cleared");
        if (SuServer.get() != null) {
            SuServer.get().log(actor, "chat.clear", null, "", "");
        }
    }

    private static int realNames(CommandContext<CommandSourceStack> context, Boolean value) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean on = value != null ? value : !SuServer.get().players().get(player.getUUID()).realNames;
        SuServer.get().players().update(player.getUUID(), record -> record.realNames = on);
        SuNetwork.syncState(player);
        SuNetwork.syncNicknames(player);
        return Msg.ok(context.getSource(), on ? "realnames.on" : "realnames.off");
    }

    // ============================================================ nicknames

    private static LiteralArgumentBuilder<CommandSourceStack> nick() {
        return Commands.literal("nick").requires(source -> SuServer.config().features.nicknames && Perms.has(source, Perm.NICKNAME_CHANGE))
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                String nick = Names.nickname(player.getUUID());
                return nick.isEmpty() ? Msg.info(context.getSource(), "nick.none") : Msg.info(context.getSource(), "nick.current", StyledText.strip(nick));
            })
            .then(Commands.literal("clear").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                NicknameService.clear(Mc.server(player), player.getUUID(), player);
                return Msg.ok(context.getSource(), "nick.cleared");
            }).then(player("player", true).requires(perm(Perm.NICKNAME_OTHERS)).executes(context -> {
                Names.Target found = target(context, "player");
                NicknameService.clear(context.getSource().getServer(), found.id(), Mc.player(context.getSource()));
                return Msg.ok(context.getSource(), "nick.cleared_other", found.name());
            })))
            .then(Commands.literal("set").requires(perm(Perm.NICKNAME_OTHERS))
                .then(player("player", true).then(Commands.argument("nickname", StringArgumentType.greedyString()).executes(context -> {
                    Names.Target found = target(context, "player");
                    String nickname = StringArgumentType.getString(context, "nickname");
                    String error = NicknameService.set(context.getSource().getServer(), found.id(), nickname, true, Mc.player(context.getSource()));
                    return error == null ? Msg.ok(context.getSource(), "nick.set_other", found.name(), StyledText.strip(nickname)) : Msg.error(context.getSource(), error);
                }))))
            .then(Commands.argument("nickname", StringArgumentType.greedyString()).executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                String nickname = StringArgumentType.getString(context, "nickname");
                boolean colors = Perms.has(player, Perm.NICKNAME_COLOR);
                String error = NicknameService.set(Mc.server(player), player.getUUID(), nickname, colors, player);
                return error == null ? Msg.ok(context.getSource(), "nick.set", StyledText.strip(nickname)) : Msg.error(context.getSource(), error);
            }));
    }

    // ============================================================ inventory

    private static LiteralArgumentBuilder<CommandSourceStack> inventory() {
        return Commands.literal("inventory").requires(source -> SuServer.config().features.inventory && Perms.has(source, Perm.STAFF_INVENTORY))
            .then(Commands.literal("backup").then(player("player", true).executes(StaffCommands::backupNamed)))
            .then(Commands.literal("backups").then(player("player", true).executes(context -> openBackupsNamed(context, BackupMenus.Mode.VANILLA))))
            .then(Commands.literal("list").then(player("player", true).executes(StaffCommands::listBackups)))
            .then(Commands.literal("restore").requires(perm(Perm.STAFF_INVENTORY_RESTORE))
                .then(player("player", true)
                    .executes(context -> openBackupsNamed(context, BackupMenus.Mode.VANILLA))
                    .then(Commands.argument("backup", StringArgumentType.word()).suggests(backupSuggestions())
                        .executes(context -> restoreNamed(context, BackupService.FULL))
                        .then(Commands.literal("full").executes(context -> restoreNamed(context, BackupService.FULL)))
                        .then(Commands.literal("inventory").executes(context -> restoreNamed(context, BackupService.INVENTORY_ONLY)))
                        .then(Commands.literal("ender").executes(context -> restoreNamed(context, BackupService.ENDER_ONLY))))))
            .then(Commands.literal("enderchest").then(player("player", true).executes(context ->
                InventoryViews.openEnderChest(context.getSource().getPlayerOrException(), target(context, "player")) ? 1 : 0)))
            .then(Commands.literal("curios").then(player("player", false).executes(context ->
                InventoryViews.openCurios(context.getSource().getPlayerOrException(), onlineTarget(context, "player")) ? 1 : 0)))
            .then(Commands.literal("cosmetic").then(player("player", false).executes(context ->
                InventoryViews.openCosmetic(context.getSource().getPlayerOrException(), onlineTarget(context, "player")) ? 1 : 0)))
            .then(player("player", true).executes(context -> openInventory(context, "player")));
    }

    private static int openInventory(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        return InventoryViews.openInventory(context.getSource().getPlayerOrException(), target(context, argument)) ? 1 : 0;
    }

    private static SuggestionProvider<CommandSourceStack> backupSuggestions() {
        return (context, builder) -> {
            try {
                Names.Target found = target(context, "player");
                List<String> ids = new ArrayList<>();
                for (PlayerSnapshot snapshot : BackupService.list(found.id())) {
                    ids.add(snapshot.id());
                }
                return SharedSuggestionProvider.suggest(ids, builder);
            } catch (CommandSyntaxException e) {
                return builder.buildFuture();
            }
        };
    }

    private static int backup(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return createBackup(context, target(context, "target"));
    }

    private static int backupNamed(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return createBackup(context, target(context, "player"));
    }

    private static int createBackup(CommandContext<CommandSourceStack> context, Names.Target found) {
        MinecraftServer server = context.getSource().getServer();
        String by = context.getSource().getTextName();
        PlayerSnapshot snapshot = found.isOnline()
            ? BackupService.snapshot(found.online(), SnapshotType.MANUAL, "manual backup", by)
            : BackupService.snapshotOffline(server, found.id(), found.name(), SnapshotType.MANUAL, "manual backup by " + by);
        if (snapshot == null) {
            return Msg.error(context.getSource(), "inventory.no_data", found.name());
        }
        SuServer.get().log(Mc.player(context.getSource()), "backup.create", found.id(), found.name(), snapshot.id());
        return Msg.ok(context.getSource(), "backup.created", snapshot.id(), shown(context.getSource(), found));
    }

    private static int listBackups(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Names.Target found = target(context, "player");
        List<PlayerSnapshot> snapshots = BackupService.list(found.id());
        if (snapshots.isEmpty()) {
            return Msg.info(context.getSource(), "backup.none", found.name());
        }
        ServerPlayer viewer = Mc.player(context.getSource());
        Msg.info(context.getSource(), "backup.list.header", found.name());
        for (int i = 0; i < Math.min(20, snapshots.size()); i++) {
            PlayerSnapshot snapshot = snapshots.get(i);
            Mc.reply(context.getSource(), Texts.literal(Lang.tr(viewer, "backup.list.line", i + 1, snapshot.id(),
                TIME.format(Instant.ofEpochMilli(snapshot.timestamp())), Lang.tr(viewer, snapshot.type().langKey()))).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int openBackups(CommandContext<CommandSourceStack> context, BackupMenus.Mode mode) throws CommandSyntaxException {
        Names.Target found = target(context, "target");
        BackupMenus.openCategories(context.getSource().getPlayerOrException(), new BackupMenus.Target(found.id(), found.name()), mode);
        return 1;
    }

    private static int openBackupsNamed(CommandContext<CommandSourceStack> context, BackupMenus.Mode mode) throws CommandSyntaxException {
        Names.Target found = target(context, "player");
        BackupMenus.openCategories(context.getSource().getPlayerOrException(), new BackupMenus.Target(found.id(), found.name()), mode);
        return 1;
    }

    private static int restore(CommandContext<CommandSourceStack> context, String backup, Set<PlayerState.Part> parts) throws CommandSyntaxException {
        return doRestore(context, target(context, "target"), backup, parts);
    }

    private static int restoreNamed(CommandContext<CommandSourceStack> context, Set<PlayerState.Part> parts) throws CommandSyntaxException {
        return doRestore(context, target(context, "player"), StringArgumentType.getString(context, "backup"), parts);
    }

    private static int doRestore(CommandContext<CommandSourceStack> context, Names.Target found, String backup, Set<PlayerState.Part> parts) {
        if (BackupService.store() == null) {
            return Msg.error(context.getSource(), "error.feature_disabled");
        }
        Optional<PlayerSnapshot> snapshot = BackupService.store().find(found.id(), backup);
        if (snapshot.isEmpty()) {
            return Msg.error(context.getSource(), "backup.not_found", backup);
        }
        boolean ok = BackupService.restore(context.getSource().getServer(), Mc.player(context.getSource()), found.id(), found.name(),
            snapshot.get(), parts, parts == BackupService.FULL);
        return ok ? Msg.ok(context.getSource(), "backup.restored", snapshot.get().id(), shown(context.getSource(), found))
            : Msg.error(context.getSource(), "backup.restore_failed");
    }

    // ============================================================= profiles

    private static LiteralArgumentBuilder<CommandSourceStack> profile() {
        return Commands.literal("profile").requires(source -> SuServer.config().features.profiles
                && (Perms.has(source, Perm.PROFILE_USE) || Perms.has(source, Perm.STAFF_PROFILES)))
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                ProfileMenus.openList(player, player.getUUID(), Names.realName(player));
                return 1;
            })
            .then(Commands.literal("list").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                ProfileStore.PlayerProfiles profiles = ProfileService.of(player.getUUID());
                return Msg.info(context.getSource(), "profile.list", profiles.active, String.join(", ", profiles.names()));
            }).then(player("player", true).requires(perm(Perm.STAFF_PROFILES)).executes(context -> {
                Names.Target found = target(context, "player");
                ProfileStore.PlayerProfiles profiles = ProfileService.of(found.id());
                return Msg.info(context.getSource(), "profile.list", profiles.active, String.join(", ", profiles.names()));
            })))
            .then(Commands.literal("create").requires(perm(Perm.PROFILE_USE))
                .then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String name = ProfileStore.normalize(StringArgumentType.getString(context, "name"));
                    String error = ProfileService.create(player, name, player);
                    return error == null ? Msg.ok(context.getSource(), "profile.created", name, name) : Msg.error(context.getSource(), error, name);
                })))
            .then(Commands.literal("switch").requires(perm(Perm.PROFILE_USE))
                .then(Commands.argument("name", StringArgumentType.word()).suggests(ownProfiles()).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String name = StringArgumentType.getString(context, "name");
                    String error = ProfileService.switchTo(player, name, null, false);
                    return error == null ? Msg.ok(context.getSource(), "profile.switched", ProfileStore.normalize(name)) : Msg.error(context.getSource(), error, name);
                })))
            .then(Commands.literal("delete").requires(perm(Perm.PROFILE_USE))
                .then(Commands.argument("name", StringArgumentType.word()).suggests(ownProfiles())
                    .then(Commands.literal("confirm").executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        String name = StringArgumentType.getString(context, "name");
                        String error = ProfileService.delete(player.getUUID(), Names.realName(player), name, player);
                        return error == null ? Msg.ok(context.getSource(), "profile.deleted", name) : Msg.error(context.getSource(), error, name);
                    }))
                    .executes(context -> Msg.info(context.getSource(), "profile.delete_confirm", StringArgumentType.getString(context, "name")))))
            .then(Commands.literal("quick").requires(perm(Perm.PROFILE_USE)).executes(context -> {
                ProfileMenus.openQuickChange(context.getSource().getPlayerOrException());
                return 1;
            }))
            .then(Commands.literal("nick").requires(perm(Perm.PROFILE_USE))
                .then(Commands.argument("name", StringArgumentType.word()).suggests(ownProfiles())
                    .then(Commands.argument("nickname", StringArgumentType.greedyString()).executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        String name = StringArgumentType.getString(context, "name");
                        String error = ProfileService.setNick(player.getUUID(), name, StringArgumentType.getString(context, "nickname"));
                        return error == null ? Msg.ok(context.getSource(), "profile.nick_set", name) : Msg.error(context.getSource(), error, name);
                    }))))
            .then(Commands.literal("view").requires(perm(Perm.STAFF_PROFILES)).then(player("player", true).executes(context -> {
                Names.Target found = target(context, "player");
                ProfileMenus.openList(context.getSource().getPlayerOrException(), found.id(), found.name());
                return 1;
            })))
            .then(Commands.literal("switchfor").requires(perm(Perm.STAFF_PROFILES))
                .then(player("player", false).then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
                    ServerPlayer target = onlineTarget(context, "player");
                    String name = StringArgumentType.getString(context, "name");
                    String error = ProfileService.switchTo(target, name, Mc.player(context.getSource()), true);
                    return error == null ? Msg.ok(context.getSource(), "profile.switched_other", Names.realName(target), name) : Msg.error(context.getSource(), error, name);
                }))));
    }

    private static SuggestionProvider<CommandSourceStack> ownProfiles() {
        return (context, builder) -> {
            ServerPlayer player = Mc.player(context.getSource());
            return player == null ? builder.buildFuture() : SharedSuggestionProvider.suggest(ProfileService.of(player.getUUID()).names(), builder);
        };
    }

    // =========================================================== moderation

    /** /su ban|mute|voicemute <player> [duration] [reason] */
    private static LiteralArgumentBuilder<CommandSourceStack> moderation(String literal, Perm perm) {
        return Commands.literal(literal).requires(perm(perm))
            .then(player("player", true)
                .executes(context -> punish(context, literal, ""))
                .then(Commands.argument("details", StringArgumentType.greedyString())
                    .executes(context -> punish(context, literal, StringArgumentType.getString(context, "details")))));
    }

    private static int punish(CommandContext<CommandSourceStack> context, String kind, String details) throws CommandSyntaxException {
        Names.Target found = target(context, "player");
        ServerPlayer actor = Mc.player(context.getSource());
        if (actor != null && found.isOnline() && found.online() != actor && Perms.opLevel(found.online()) > Perms.opLevel(actor)) {
            return Msg.error(context.getSource(), "punish.cannot_target");
        }
        String[] parts = details.trim().split(" ", 2);
        Long duration = parts[0].isEmpty() ? Long.valueOf(-1) : Durations.parse(parts[0]);
        String reason;
        if (duration == null) {
            duration = -1L;
            reason = details.trim();
        } else {
            reason = parts.length > 1 ? parts[1] : "";
        }
        MinecraftServer server = context.getSource().getServer();
        String durationText = duration < 0 ? Lang.tr(actor, "punish.permanent") : Durations.format(duration);
        switch (kind) {
            case "ban" -> {
                PunishmentService.ban(server, actor, found, duration, reason);
                return Msg.ok(context.getSource(), "punish.done.ban", shown(context.getSource(), found), durationText);
            }
            case "mute" -> {
                PunishmentService.mute(server, actor, found, duration, reason, false);
                return Msg.ok(context.getSource(), "punish.done.mute", shown(context.getSource(), found), durationText);
            }
            default -> {
                PunishmentService.mute(server, actor, found, duration, reason, true);
                return Msg.ok(context.getSource(), "punish.done.voice_mute", shown(context.getSource(), found), durationText);
            }
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> unmute(String literal, Perm perm, boolean voice) {
        return Commands.literal(literal).requires(perm(perm)).then(player("player", true).executes(context -> {
            Names.Target found = target(context, "player");
            return PunishmentService.unmute(context.getSource().getServer(), Mc.player(context.getSource()), found, voice)
                ? Msg.ok(context.getSource(), voice ? "punish.done.voice_unmute" : "punish.done.unmute", shown(context.getSource(), found))
                : Msg.error(context.getSource(), "punish.not_muted", shown(context.getSource(), found));
        }));
    }

    private static int kick(CommandContext<CommandSourceStack> context, String reason) throws CommandSyntaxException {
        ServerPlayer target = onlineTarget(context, "player");
        ServerPlayer actor = Mc.player(context.getSource());
        if (actor != null && target != actor && Perms.opLevel(target) > Perms.opLevel(actor)) {
            return Msg.error(context.getSource(), "punish.cannot_target");
        }
        PunishmentService.kick(context.getSource().getServer(), actor, target, reason);
        return Msg.ok(context.getSource(), "punish.done.kick", Names.realName(target));
    }

    private static int history(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Names.Target found = target(context, "player");
        List<Punishment> records = SuServer.get().punishments().history(found.id());
        if (records.isEmpty()) {
            return Msg.info(context.getSource(), "punish.history.empty", found.name());
        }
        ServerPlayer viewer = Mc.player(context.getSource());
        Msg.info(context.getSource(), "punish.history.header", found.name(), records.size());
        for (int i = 0; i < Math.min(15, records.size()); i++) {
            Punishment record = records.get(i);
            Mc.reply(context.getSource(), Texts.literal(Lang.tr(viewer, "punish.history.line", record.id, record.type.name(),
                TIME.format(Instant.ofEpochMilli(record.createdAt)), record.actorName, record.reason)).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int silent(CommandContext<CommandSourceStack> context, boolean join) throws CommandSyntaxException {
        Names.Target found = target(context, "player");
        boolean[] now = new boolean[1];
        SuServer.get().players().update(found.id(), record -> {
            if (join) {
                now[0] = record.silentJoin = !record.silentJoin;
            } else {
                now[0] = record.silentQuit = !record.silentQuit;
            }
        });
        SuServer.get().log(Mc.player(context.getSource()), join ? "silent_join" : "silent_quit", found.id(), found.name(), String.valueOf(now[0]));
        return Msg.ok(context.getSource(), (join ? "silentjoin." : "silentquit.") + (now[0] ? "on" : "off"), found.name());
    }

    // ================================================================ world

    private static LiteralArgumentBuilder<CommandSourceStack> portal() {
        SuggestionProvider<CommandSourceStack> keys = (context, builder) -> SharedSuggestionProvider.suggest(
            List.of("nether", "end", "aether"), builder);
        return Commands.literal("portal").requires(perm(Perm.PORTAL_MANAGE))
            .then(Commands.literal("limit").then(Commands.argument("dimension", StringArgumentType.word()).suggests(keys)
                .then(Commands.argument("quantity", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("1", "2", "5", "unlimited"), builder))
                    .executes(context -> {
                        String key = StringArgumentType.getString(context, "dimension");
                        Integer limit = StaffService.parseLimit(StringArgumentType.getString(context, "quantity"));
                        if (limit == null) {
                            return Msg.error(context.getSource(), "settings.invalid_value", StringArgumentType.getString(context, "quantity"));
                        }
                        setPortalLimit(Mc.player(context.getSource()), key, limit);
                        ServerPlayer viewer = Mc.player(context.getSource());
                        return Msg.ok(context.getSource(), "portal.limit_set", PortalRegistry.normalizeKey(key), limit < 0 ? Lang.tr(viewer, "portal.unlimited") : limit);
                    }))))
            .then(Commands.literal("count").executes(context -> {
                SuConfig.Portals config = SuServer.config().portals;
                ServerPlayer viewer = Mc.player(context.getSource());
                List<String> keySet = new ArrayList<>(List.of("nether", "end"));
                config.limits.keySet().forEach(key -> {
                    if (!keySet.contains(key)) {
                        keySet.add(key);
                    }
                });
                for (String key : keySet) {
                    int limit = PortalRegistry.limitFor(config.limits, key);
                    Msg.info(context.getSource(), "portal.count", key, SuServer.get().portals().count(key), limit < 0 ? Lang.tr(viewer, "portal.unlimited") : limit);
                }
                return 1;
            }))
            .then(Commands.literal("list").executes(context -> listPortals(context, null))
                .then(Commands.argument("dimension", StringArgumentType.word()).suggests(keys)
                    .executes(context -> listPortals(context, StringArgumentType.getString(context, "dimension")))))
            .then(Commands.literal("remove").then(Commands.argument("id", StringArgumentType.word()).executes(context -> {
                String id = StringArgumentType.getString(context, "id").replace("#", "");
                boolean removed = SuServer.get().portals().remove(id);
                SuServer.get().log(Mc.player(context.getSource()), "portal.remove", null, "", "#" + id);
                return removed ? Msg.ok(context.getSource(), "portal.removed", id) : Msg.error(context.getSource(), "portal.not_found", id);
            })))
            .then(Commands.literal("clear").then(Commands.argument("dimension", StringArgumentType.word()).suggests(keys).executes(context -> {
                String key = StringArgumentType.getString(context, "dimension");
                int removed = SuServer.get().portals().clear(key);
                SuServer.get().log(Mc.player(context.getSource()), "portal.clear", null, key, removed + " portals");
                return Msg.ok(context.getSource(), "portal.cleared", removed, key);
            })));
    }

    public static void setPortalLimit(ServerPlayer actor, String key, int limit) {
        String normalized = PortalRegistry.normalizeKey(key);
        SuServer.configManager().edit(config -> {
            if (limit < 0) {
                config.portals.limits.remove(normalized);
            } else {
                config.portals.limits.put(normalized, limit);
            }
        });
        SuServer.get().log(actor, "portal.limit", null, normalized, limit < 0 ? "unlimited" : String.valueOf(limit));
    }

    private static int listPortals(CommandContext<CommandSourceStack> context, String key) {
        List<PortalRecord> portals = SuServer.get().portals().list(key);
        if (portals.isEmpty()) {
            return Msg.info(context.getSource(), "portal.list_empty", key == null ? "*" : key);
        }
        ServerPlayer viewer = Mc.player(context.getSource());
        for (PortalRecord portal : portals) {
            Mc.reply(context.getSource(), Texts.literal(Lang.tr(viewer, "portal.list_line", portal.id, portal.key, portal.dimension,
                portal.x, portal.y, portal.z, portal.creatorName.isEmpty() ? "?" : portal.creatorName)).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> dimension() {
        SuggestionProvider<CommandSourceStack> dimensions = (context, builder) -> {
            List<String> names = new ArrayList<>(SuServer.config().dimensions.aliases.keySet());
            for (ServerLevel level : context.getSource().getServer().getAllLevels()) {
                names.add(level.dimension().location().toString());
            }
            return SharedSuggestionProvider.suggest(names, builder);
        };
        return Commands.literal("dimension").requires(perm(Perm.DIMENSION_MANAGE))
            .then(Commands.literal("list").executes(context -> {
                ServerPlayer viewer = Mc.player(context.getSource());
                SuConfig.Dimensions config = SuServer.config().dimensions;
                List<String> entries = new ArrayList<>();
                for (ServerLevel level : context.getSource().getServer().getAllLevels()) {
                    String id = level.dimension().location().toString();
                    entries.add(DimensionRules.shortName(config, id) + " (" + Lang.tr(viewer, DimensionRules.isBlocked(config, id) ? "dimension.state_off" : "dimension.state_on") + ")");
                }
                return Msg.info(context.getSource(), "dimension.list", String.join(", ", entries));
            }))
            .then(Commands.argument("dimension", StringArgumentType.string()).suggests(dimensions)
                .then(Commands.literal("on").executes(context -> setDimension(context, true)))
                .then(Commands.literal("off").executes(context -> setDimension(context, false))));
    }

    private static int setDimension(CommandContext<CommandSourceStack> context, boolean open) {
        String input = StringArgumentType.getString(context, "dimension");
        if (!setDimension(Mc.player(context.getSource()), input, open)) {
            return Msg.error(context.getSource(), "dimension.unknown", input);
        }
        return Msg.ok(context.getSource(), open ? "dimension.unblocked_now" : "dimension.blocked_now", input);
    }

    /** @return false when the dimension does not exist on this server */
    public static boolean setDimension(ServerPlayer actor, String input, boolean open) {
        SuConfig.Dimensions config = SuServer.config().dimensions;
        String id = DimensionRules.resolve(config, input);
        boolean exists = false;
        for (ServerLevel level : SuServer.get().server().getAllLevels()) {
            if (level.dimension().location().toString().equals(id)) {
                exists = true;
                break;
            }
        }
        // Allow pre-blocking a dimension of a mod that is not loaded yet, but not typos of vanilla names.
        if (!exists && id.startsWith("minecraft:")) {
            return false;
        }
        SuServer.configManager().edit(value -> DimensionRules.setBlocked(value.dimensions, id, !open));
        SuServer.get().log(actor, open ? "dimension.open" : "dimension.block", null, id, "");
        return true;
    }

    // ================================================================ troll

    private static LiteralArgumentBuilder<CommandSourceStack> troll() {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("troll").requires(source -> SuServer.config().features.troll && Perms.has(source, Perm.STAFF_TROLL));
        for (TrollService.Action action : TrollService.Action.values()) {
            node.then(Commands.literal(action.id()).then(player("player", false)
                .executes(context -> troll(context, action, ""))
                .then(Commands.argument("argument", StringArgumentType.greedyString())
                    .executes(context -> troll(context, action, StringArgumentType.getString(context, "argument"))))));
        }
        return node;
    }

    private static int troll(CommandContext<CommandSourceStack> context, TrollService.Action action, String argument) throws CommandSyntaxException {
        if (!TrollService.allowed(action)) {
            return Msg.error(context.getSource(), "error.feature_disabled");
        }
        ServerPlayer target = onlineTarget(context, "player");
        String error = TrollService.apply(action, target, argument, Mc.player(context.getSource()));
        if (error != null) {
            return Msg.error(context.getSource(), error, Names.realName(target));
        }
        SuServer.get().log(Mc.player(context.getSource()), "troll." + action.id(), target.getUUID(), Names.realName(target), argument);
        return Msg.ok(context.getSource(), "troll.done", action.id(), Names.realName(target));
    }

    private static int announce(CommandContext<CommandSourceStack> context, TrollService.Action action, String text) {
        TrollService.broadcastTitle(context.getSource().getServer(), action, text);
        SuServer.get().log(Mc.player(context.getSource()), "announce." + action.id(), null, "", text);
        return 1;
    }

    // ================================================================== tts

    private static LiteralArgumentBuilder<CommandSourceStack> tts() {
        return Commands.literal("tts").requires(source -> SuServer.config().features.tts && source.getEntity() instanceof ServerPlayer)
            .executes(StaffCommands::ttsMenu)
            .then(Commands.literal("off").executes(StaffCommands::ttsOff))
            .then(ttsVoice("feminine", 3))
            .then(ttsVoice("masculine", 2));
    }

    private static int ttsMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        TtsService.openMenu(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int ttsOff(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        TtsService.select(context.getSource().getPlayerOrException(), 0, -1);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> ttsVoice(String gender, int maxVariant) {
        return Commands.literal(gender)
            .then(Commands.argument("variant", IntegerArgumentType.integer(1, maxVariant))
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(TtsPresets.suggestionVariants(maxVariant), builder))
                .then(Commands.argument("language", StringArgumentType.string())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(TtsPresets.suggestionLanguages(), builder))
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        int mode = TtsPresets.modeFromSelection(gender, IntegerArgumentType.getInteger(context, "variant"));
                        TtsPresets.VoiceLanguage language = TtsPresets.languageFromInput(StringArgumentType.getString(context, "language"));
                        if (mode < 0) {
                            return Msg.error(context.getSource(), "tts.invalid_voice");
                        }
                        if (language == null) {
                            return Msg.error(context.getSource(), "tts.invalid_language");
                        }
                        TtsService.select(player, mode, Arrays.asList(TtsPresets.VoiceLanguage.values()).indexOf(language));
                        return 1;
                    })));
    }

    // ================================================================ vanish

    private static int vanish(CommandContext<CommandSourceStack> context, ServerPlayer target, Boolean value) throws CommandSyntaxException {
        ServerPlayer actor = Mc.player(context.getSource());
        ServerPlayer who = target != null ? target : context.getSource().getPlayerOrException();
        boolean on = value != null ? value : !VanishService.isVanished(who.getUUID());
        VanishService.set(who, on, actor);
        if (target != null && target != actor) {
            Msg.ok(context.getSource(), on ? "vanish.other_on" : "vanish.other_off", Names.realName(target));
        }
        return 1;
    }

    static MutableComponent line(String text) {
        return Texts.literal(text).withStyle(ChatFormatting.GRAY);
    }

    static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
