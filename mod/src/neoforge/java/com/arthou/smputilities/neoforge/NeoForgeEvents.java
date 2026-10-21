package com.arthou.smputilities.neoforge;

import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.platform.CommonEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
//#if MC>=1.21.5
import net.minecraft.util.TriState;
//#elif MC>=1.20.6
//$$ import net.neoforged.neoforge.common.util.TriState;
//#endif
//#if MC>=1.20.6
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
//#else
//$$ import net.neoforged.neoforge.event.TickEvent;
//$$ import net.neoforged.neoforge.event.entity.player.EntityItemPickupEvent;
//#endif

/** Forwards NeoForge game events to {@link CommonEvents}. */
public final class NeoForgeEvents {
    private NeoForgeEvents() {
    }

    static void register() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(NeoForgePlatform::onGatherNodes);
        bus.addListener((RegisterCommandsEvent event) -> CommonEvents.registerCommands(event.getDispatcher()));
        bus.addListener((ServerStartingEvent event) -> CommonEvents.serverStarting(event.getServer()));
        bus.addListener((ServerStoppingEvent event) -> CommonEvents.serverStopping(event.getServer()));
        bus.addListener((ServerStoppedEvent event) -> CommonEvents.serverStopped(event.getServer()));
        //#if MC>=1.20.6
        bus.addListener((ServerTickEvent.Post event) -> CommonEvents.serverTick(event.getServer()));
        //#else
        //$$ bus.addListener((TickEvent.ServerTickEvent event) -> {
        //$$     if (event.phase == TickEvent.Phase.END) {
        //$$         CommonEvents.serverTick(event.getServer());
        //$$     }
        //$$ });
        //#endif
        // Fires before the vanilla join line is broadcast, so the presence filter is in place in time.
        bus.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() != null) {
                CommonEvents.beforeJoinMessage(event.getPlayer());
            }
        });
        bus.addListener(EventPriority.HIGH, (PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CommonEvents.login(player);
            }
        });
        bus.addListener(EventPriority.LOW, (PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CommonEvents.logout(player);
            }
        });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CommonEvents.changedDimension(player, event.getTo());
            }
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CommonEvents.respawn(player, event.isEndConquered());
            }
        });
        bus.addListener(EventPriority.LOWEST, (LivingDeathEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
                CommonEvents.death(player, Mc.damageCause(event.getSource()));
            }
        });
        //#if MC>=1.21
        bus.addListener((LivingDamageEvent.Post event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CommonEvents.damaged(player, event.getNewDamage());
            }
        });
        //#else
        //$$ bus.addListener(EventPriority.LOWEST, (LivingDamageEvent event) -> {
        //$$     if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
        //$$         CommonEvents.damaged(player, event.getAmount());
        //$$     }
        //$$ });
        //#endif
        bus.addListener(EventPriority.HIGH, (ServerChatEvent event) -> {
            if (CommonEvents.chat(event.getPlayer(), event.getRawText())) {
                event.setCanceled(true);
            }
        });
        bus.addListener(EventPriority.LOW, (PlayerEvent.NameFormat event) -> event.setDisplayname(
            CommonEvents.displayName(event.getEntity(), event.getDisplayname())));
        bus.addListener(EventPriority.LOW, (PlayerEvent.TabListNameFormat event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                Component name = CommonEvents.tabListName(player);
                if (name != null) {
                    event.setDisplayName(name);
                }
            }
        });
        bus.addListener((EntityTravelToDimensionEvent event) -> {
            if (CommonEvents.travel(event.getEntity(), event.getDimension())) {
                event.setCanceled(true);
            }
        });
        bus.addListener((BlockEvent.PortalSpawnEvent event) -> {
            if (event.getLevel() instanceof Level level && CommonEvents.portalSpawn(level, event.getPos(), event.getState())) {
                event.setCanceled(true);
            }
        });
        bus.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                && CommonEvents.useBlock(player, level, event.getPos(), event.getItemStack())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        });
        bus.addListener((BlockEvent.BreakEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                CommonEvents.blockBroken(level, event.getPos(), event.getState());
            }
        });
        bus.addListener((LivingChangeTargetEvent event) -> {
            //#if MC>=1.21
            if (CommonEvents.blockTarget(event.getNewAboutToBeSetTarget())) {
            //#else
            //$$ if (CommonEvents.blockTarget(event.getNewTarget())) {
            //#endif
                event.setCanceled(true);
            }
        });
        //#if MC>=1.20.6
        bus.addListener((ItemEntityPickupEvent.Pre event) -> {
            if (event.getPlayer() instanceof ServerPlayer player && CommonEvents.blockPickup(player)) {
                event.setCanPickup(TriState.FALSE);
            }
        });
        //#else
        //$$ bus.addListener((EntityItemPickupEvent event) -> {
        //$$     if (event.getEntity() instanceof ServerPlayer player && CommonEvents.blockPickup(player)) {
        //$$         event.setCanceled(true);
        //$$     }
        //$$ });
        //#endif
        bus.addListener((EntityJoinLevelEvent event) -> {
            if (event.loadedFromDisk() && CommonEvents.discardOnLoad(event.getEntity())) {
                event.setCanceled(true);
            }
        });
    }
}
