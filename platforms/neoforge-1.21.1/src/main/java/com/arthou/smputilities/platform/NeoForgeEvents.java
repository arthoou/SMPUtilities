package com.arthou.smputilities.platform;

import com.arthou.smputilities.backup.BackupService;
import com.arthou.smputilities.chat.ChatCommands;
import com.arthou.smputilities.chat.ChatService;
import com.arthou.smputilities.chat.PresenceFilter;
import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.dimension.DimensionService;
import com.arthou.smputilities.inventory.TargetInventoryMenu;
import com.arthou.smputilities.migrate.LegacyImport;
import com.arthou.smputilities.moderation.PunishmentService;
import com.arthou.smputilities.modsync.ModSyncServer;
import com.arthou.smputilities.network.SuNetwork;
import com.arthou.smputilities.nick.NicknameService;
import com.arthou.smputilities.portal.PortalService;
import com.arthou.smputilities.profile.ProfileService;
import com.arthou.smputilities.qol.QolCommands;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.staff.StaffCommands;
import com.arthou.smputilities.staff.StaffService;
import com.arthou.smputilities.teleport.TeleportCommands;
import com.arthou.smputilities.teleport.Teleports;
import com.arthou.smputilities.troll.TrollService;
import com.arthou.smputilities.vanish.VanishService;
import com.arthou.smputilities.voice.VoiceChatCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Connects NeoForge events to the loader-independent services. Keeping all
 * loader events here is what lets another loader reuse the services with its
 * own event glue.
 */
public final class NeoForgeEvents {
    private static int ticks;

    private NeoForgeEvents() {
    }

    public static void register() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(NeoForgePermissions::onGatherNodes);
        bus.addListener(NeoForgeEvents::onRegisterCommands);
        bus.addListener(NeoForgeEvents::onServerStarting);
        bus.addListener(NeoForgeEvents::onServerStopped);
        bus.addListener(NeoForgeEvents::onServerTick);
        bus.addListener(NeoForgeEvents::onDatapackSync);
        bus.addListener(EventPriority.HIGH, NeoForgeEvents::onLogin);
        bus.addListener(EventPriority.LOW, NeoForgeEvents::onLogout);
        bus.addListener(NeoForgeEvents::onChangedDimension);
        bus.addListener(NeoForgeEvents::onRespawn);
        bus.addListener(EventPriority.LOWEST, NeoForgeEvents::onDeath);
        bus.addListener(NeoForgeEvents::onDamage);
        bus.addListener(EventPriority.HIGH, NeoForgeEvents::onChat);
        bus.addListener(EventPriority.LOW, NeoForgeEvents::onNameFormat);
        bus.addListener(EventPriority.LOW, NeoForgeEvents::onTabListName);
        bus.addListener(NeoForgeEvents::onTravel);
        bus.addListener(NeoForgeEvents::onPortalSpawn);
        bus.addListener(NeoForgeEvents::onRightClickBlock);
        bus.addListener(NeoForgeEvents::onBlockBreak);
        bus.addListener(NeoForgeEvents::onChangeTarget);
        bus.addListener(NeoForgeEvents::onPickup);
        bus.addListener(NeoForgeEvents::onEntityJoin);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        ChatCommands.register(event.getDispatcher());
        TeleportCommands.register(event.getDispatcher());
        QolCommands.register(event.getDispatcher());
        StaffCommands.register(event.getDispatcher());
    }

    private static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        SuServer context = SuServer.start(server);
        BackupService.onServerStart(context);
        ProfileService.onServerStart(context);
        VanishService.onServerStart(context);
        StaffService.onServerStart();
        LegacyImport.run(server, context);
        ModSyncServer.onServerStart();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ModSyncServer.onServerStop();
        BackupService.onServerStop();
        ProfileService.onServerStop();
        SuServer.stop();
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        SuServer context = SuServer.get();
        if (context == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        Teleports.tick(server);
        QolCommands.tick(server);
        PortalService.tick(server);
        TrollService.tick(server);
        ticks++;
        if (ticks % 20 == 0) {
            VanishService.tickIndicator(server);
            BackupService.tick(server);
        }
        if (ticks % 600 == 0) {
            context.players().flush();
        }
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        // Fires before the vanilla join line is broadcast, so the filter is in place in time.
        if (event.getPlayer() != null) {
            PresenceFilter.install(event.getPlayer());
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || SuServer.get() == null) {
            return;
        }
        SuServer context = SuServer.get();
        long now = System.currentTimeMillis();
        context.players().update(player.getUUID(), record -> {
            record.lastName = player.getGameProfile().getName();
            if (record.firstJoin == 0) {
                record.firstJoin = now;
            }
            record.lastLogin = now;
            record.lastSeen = now;
            record.joinCount++;
        });
        PresenceFilter.install(player);
        SuLog.guard("punishment login check", player, PunishmentService::onLogin);
        SuLog.guard("profile check", player, ProfileService::onLogin);
        SuLog.guard("offline edit hand-over", player, TargetInventoryMenu::onTargetLogin);
        SuLog.guard("vanish", player, VanishService::onLogin);
        player.refreshDisplayName();
        player.refreshTabListName();
        SuLog.guard("join message", player, value -> ChatService.broadcastPresence(value, true, false));
        SuLog.guard("join backup", player, BackupService::onJoin);
        SuLog.guard("client sync", player, value -> {
            SuNetwork.syncState(value);
            SuNetwork.syncNicknamesToAll(value.server);
            VoiceChatCompat.refresh(value);
        });
        SuLog.guard("mod sync", player, ModSyncServer::onLogin);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || SuServer.get() == null) {
            return;
        }
        SuServer context = SuServer.get();
        SuLog.guard("quit backup", player, BackupService::onQuit);
        context.players().update(player.getUUID(), record -> {
            record.lastSeen = System.currentTimeMillis();
            record.logout = Teleports.locationOf(player);
        });
        SuLog.guard("quit message", player, value -> ChatService.broadcastPresence(value, false, false));
        Teleports.forget(player);
        TeleportCommands.forget(player);
        ChatService.forget(player);
        DimensionService.forget(player);
        VanishService.onLogout(player);
        ModSyncServer.onLogout(player);
        StaffService.stopSpectating(player);
        context.players().flush(player.getUUID());
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SuLog.guard("dimension backup", player, value -> BackupService.onDimensionChange(value, event.getTo().location().toString()));
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isEndConquered() && SuServer.get() != null
            && SuServer.config().features.teleport && SuServer.config().teleport.respawnAtSpawn && SuServer.get().teleports().spawn() != null) {
            Teleports.teleport(player, SuServer.get().teleports().spawn(), false);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || SuServer.get() == null || event.isCanceled()) {
            return;
        }
        String cause = event.getSource().typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("death");
        SuLog.guard("death backup", player, value -> BackupService.onDeath(value, cause));
        if (SuServer.config().teleport.backOnDeath) {
            SuServer.get().players().update(player.getUUID(), record -> record.back = Teleports.locationOf(player));
        }
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0) {
            Teleports.onDamage(player);
        }
    }

    private static void onChat(ServerChatEvent event) {
        if (ChatService.onChat(event.getPlayer(), event.getRawText())) {
            event.setCanceled(true);
        }
    }

    private static void onNameFormat(PlayerEvent.NameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            event.setDisplayname(NicknameService.chatDisplayName(player, event.getDisplayname()));
        }
    }

    private static void onTabListName(PlayerEvent.TabListNameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Component name = NicknameService.tabListName(player);
            if (name != null) {
                event.setDisplayName(name);
            }
        }
    }

    private static void onTravel(EntityTravelToDimensionEvent event) {
        if (!event.getEntity().level().isClientSide() && DimensionService.onTravel(event.getEntity(), event.getDimension())) {
            event.setCanceled(true);
        }
    }

    private static void onPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        if (event.getLevel() instanceof Level level && PortalService.onPortalSpawn(level, event.getPos(), event.getState())) {
            event.setCanceled(true);
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
            && PortalService.onEyeUse(player, level, event.getPos(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PortalService.onBlockBroken(level, event.getPos(), event.getState());
        }
    }

    private static void onChangeTarget(LivingChangeTargetEvent event) {
        if (SuServer.get() != null && VanishService.shouldBlockTarget(event.getNewAboutToBeSetTarget())) {
            event.setCanceled(true);
        }
    }

    private static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer player && SuServer.get() != null && VanishService.shouldBlockPickup(player)) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        // Seats from /sit that survived a crash; fresh seats are registered right after spawning.
        if (!event.getLevel().isClientSide() && event.loadedFromDisk() && QolCommands.isOrphanSeat(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
