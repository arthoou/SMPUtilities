package com.arthou.smputilities.platform;

import com.arthou.smputilities.backup.BackupService;
import com.arthou.smputilities.chat.ChatCommands;
import com.arthou.smputilities.chat.ChatService;
import com.arthou.smputilities.chat.PresenceFilter;
import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.dimension.DimensionService;
import com.arthou.smputilities.inventory.TargetInventoryMenu;
import com.arthou.smputilities.mc.Ids;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.migrate.LegacyImport;
import com.arthou.smputilities.moderation.PunishmentService;
import com.arthou.smputilities.modsync.ModSyncServer;
import com.arthou.smputilities.network.SuNetwork;
import com.arthou.smputilities.nick.HiddenNamesCommands;
import com.arthou.smputilities.nick.NicknameService;
import com.arthou.smputilities.portal.PortalService;
import com.arthou.smputilities.profile.ProfileService;
import com.arthou.smputilities.qol.QolCommands;
import com.arthou.smputilities.server.PermCache;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.smp.AfkService;
import com.arthou.smputilities.smp.Announcer;
import com.arthou.smputilities.smp.InfoCommands;
import com.arthou.smputilities.smp.ReportService;
import com.arthou.smputilities.smp.RestartService;
import com.arthou.smputilities.smp.StaffChat;
import com.arthou.smputilities.smp.WelcomeService;
import com.arthou.smputilities.staff.StaffCommands;
import com.arthou.smputilities.staff.StaffService;
import com.arthou.smputilities.teleport.TeleportCommands;
import com.arthou.smputilities.teleport.Teleports;
import com.arthou.smputilities.troll.TrollService;
import com.arthou.smputilities.tts.TtsService;
import com.arthou.smputilities.vanish.VanishService;
import com.arthou.smputilities.voice.VoiceChatCompat;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-independent event handlers. Each loader's glue forwards its own
 * events (or mixin hooks) here; nothing in this class touches a loader API.
 */
public final class CommonEvents {
    private static int ticks;

    private CommonEvents() {
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        ChatCommands.register(dispatcher);
        TeleportCommands.register(dispatcher);
        QolCommands.register(dispatcher);
        StaffCommands.register(dispatcher);
        AfkService.register(dispatcher);
        ReportService.register(dispatcher);
        StaffChat.register(dispatcher);
        WelcomeService.register(dispatcher);
        InfoCommands.register(dispatcher);
        HiddenNamesCommands.register(dispatcher);
    }

    public static void serverStarting(MinecraftServer server) {
        SuServer context = SuServer.start(server);
        BackupService.onServerStart(context);
        ProfileService.onServerStart(context);
        VanishService.onServerStart(context);
        StaffService.onServerStart();
        Announcer.onServerStart();
        RestartService.onServerStart();
        LegacyImport.run(server, context);
        ModSyncServer.onServerStart();
    }

    public static void serverStopped(MinecraftServer server) {
        ModSyncServer.onServerStop();
        BackupService.onServerStop();
        ProfileService.onServerStop();
        PermCache.clear();
        SuServer.stop();
    }

    /** End of every server tick. */
    public static void serverTick(MinecraftServer server) {
        SuServer context = SuServer.get();
        if (context == null) {
            return;
        }
        Teleports.tick(server);
        QolCommands.tick(server);
        PortalService.tick(server);
        TrollService.tick(server);
        ticks++;
        if (ticks % 20 == 0) {
            PermCache.clear();
            VanishService.tickIndicator(server);
            BackupService.tick(server);
            SuLog.guard("afk", server, AfkService::tick);
            SuLog.guard("announcements", server, Announcer::tick);
            SuLog.guard("restart", server, RestartService::tick);
        }
        if (ticks % 600 == 0) {
            context.players().flush();
        }
        SmokeTest.tick(server);
    }

    /** Before the vanilla join line is broadcast: installs the presence filter in time. */
    public static void beforeJoinMessage(ServerPlayer player) {
        PresenceFilter.install(player);
    }

    public static void login(ServerPlayer player) {
        SuServer context = SuServer.get();
        if (context == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String name = Mc.name(player);
        boolean[] firstJoin = new boolean[1];
        context.players().update(player.getUUID(), record -> {
            record.lastName = name;
            if (record.firstJoin == 0) {
                record.firstJoin = now;
                firstJoin[0] = true;
            }
            record.lastLogin = now;
            record.lastSeen = now;
            record.joinCount++;
        });
        PresenceFilter.install(player);
        AfkService.onLogin(player);
        SuLog.guard("punishment login check", player, PunishmentService::onLogin);
        SuLog.guard("profile check", player, ProfileService::onLogin);
        SuLog.guard("offline edit hand-over", player, TargetInventoryMenu::onTargetLogin);
        SuLog.guard("vanish", player, VanishService::onLogin);
        Platform.get().refreshNames(player);
        SuLog.guard("join message", player, value -> ChatService.broadcastPresence(value, true, false));
        SuLog.guard("welcome", player, value -> WelcomeService.onLogin(value, firstJoin[0]));
        SuLog.guard("pending reports", player, ReportService::onLogin);
        SuLog.guard("join backup", player, BackupService::onJoin);
        SuLog.guard("client sync", player, value -> {
            SuNetwork.syncState(value);
            SuNetwork.syncNicknamesToAll(Mc.server(value));
            VoiceChatCompat.refresh(value);
        });
        SuLog.guard("mod sync", player, ModSyncServer::onLogin);
    }

    public static void logout(ServerPlayer player) {
        SuServer context = SuServer.get();
        if (context == null) {
            return;
        }
        SuLog.guard("quit backup", player, BackupService::onQuit);
        context.players().update(player.getUUID(), record -> {
            record.lastSeen = System.currentTimeMillis();
            record.logout = Teleports.locationOf(player);
        });
        SuLog.guard("quit message", player, value -> ChatService.broadcastPresence(value, false, false));
        Teleports.forget(player);
        TeleportCommands.forget(player);
        AfkService.onLogout(player);
        ChatService.forget(player);
        TtsService.forget(player);
        DimensionService.forget(player);
        VanishService.onLogout(player);
        ModSyncServer.onLogout(player);
        StaffService.stopSpectating(player);
        PermCache.forget(player.getUUID());
        context.players().flush(player.getUUID());
    }

    public static void changedDimension(ServerPlayer player, ResourceKey<Level> to) {
        SuLog.guard("dimension backup", player, value -> BackupService.onDimensionChange(value, Ids.key(to)));
    }

    public static void respawn(ServerPlayer player, boolean endConquered) {
        SuServer context = SuServer.get();
        if (endConquered || context == null || !SuServer.config().features.teleport || !SuServer.config().teleport.respawnAtSpawn
            || context.teleports().spawn() == null) {
            return;
        }
        Teleports.teleport(player, context.teleports().spawn(), false);
    }

    /** Before death drops: backup and /back. */
    public static void death(ServerPlayer player, String cause) {
        SuServer context = SuServer.get();
        if (context == null) {
            return;
        }
        SuLog.guard("death backup", player, value -> BackupService.onDeath(value, cause));
        SuLog.guard("death location", player, InfoCommands::deathLocation);
        if (SuServer.config().teleport.backOnDeath) {
            context.players().update(player.getUUID(), record -> record.back = Teleports.locationOf(player));
        }
    }

    public static void damaged(ServerPlayer player, float amount) {
        if (amount > 0) {
            Teleports.onDamage(player);
        }
    }

    /** @return true when the chat message must not go through vanilla */
    public static boolean chat(ServerPlayer player, String rawText) {
        AfkService.activity(player);
        if (StaffChat.handleChat(player, rawText)) {
            return true;
        }
        return ChatService.onChat(player, rawText);
    }

    /** Display name for chat and death messages (server) and nametags (client). */
    public static Component displayName(Player player, Component original) {
        if (player instanceof ServerPlayer serverPlayer) {
            return NicknameService.chatDisplayName(serverPlayer, original);
        }
        return original;
    }

    /** Tab list entry; null keeps the vanilla one. */
    public static Component tabListName(ServerPlayer player) {
        return AfkService.decorateTab(player, NicknameService.tabListName(player));
    }

    /** @return true when the travel must be cancelled */
    public static boolean travel(Entity entity, ResourceKey<Level> to) {
        return !Mc.level(entity).isClientSide() && DimensionService.onTravel(entity, to);
    }

    /** A nether-style portal is about to be lit. @return true to cancel */
    public static boolean portalSpawn(Level level, BlockPos pos, BlockState state) {
        return PortalService.onPortalSpawn(level, pos, state);
    }

    /** Right click on a block. @return true to cancel */
    public static boolean useBlock(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
        return PortalService.onEyeUse(player, level, pos, stack);
    }

    public static void blockBroken(ServerLevel level, BlockPos pos, BlockState state) {
        PortalService.onBlockBroken(level, pos, state);
    }

    /** A mob is about to target something. @return true to cancel */
    public static boolean blockTarget(LivingEntity newTarget) {
        return SuServer.get() != null && VanishService.shouldBlockTarget(newTarget);
    }

    /** A player is about to pick up an item. @return true to cancel */
    public static boolean blockPickup(ServerPlayer player) {
        return SuServer.get() != null && VanishService.shouldBlockPickup(player);
    }

    /** An entity is being added to a world. @return true to discard it (seats left by a crash) */
    public static boolean discardOnLoad(Entity entity) {
        return !Mc.level(entity).isClientSide() && QolCommands.isOrphanSeat(entity);
    }
}
