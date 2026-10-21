package com.arthou.smputilities.forge;

import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.platform.CommonEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
//#if MC>=1.19
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
//#else
//$$ import net.minecraftforge.event.entity.EntityJoinWorldEvent;
//$$ import net.minecraftforge.event.world.BlockEvent;
//$$ import net.minecraftforge.server.ServerLifecycleHooks;
//#endif

/** Forwards Forge game events to {@link CommonEvents}. */
public final class ForgeEvents {
    private ForgeEvents() {
    }

    static void register() {
        ForgeBus.on(PermissionGatherEvent.Nodes.class, ForgePlatform::onGatherNodes);
        ForgeBus.on(RegisterCommandsEvent.class, event -> CommonEvents.registerCommands(event.getDispatcher()));
        ForgeBus.on(ServerStartingEvent.class, event -> CommonEvents.serverStarting(event.getServer()));
        ForgeBus.on(ServerStoppedEvent.class, event -> CommonEvents.serverStopped(event.getServer()));
        //#if MC>=1.21.9
        ForgeBus.on(TickEvent.ServerTickEvent.Post.class, event -> CommonEvents.serverTick(event.server()));
        //#elif MC>=1.20.2
        //$$ ForgeBus.on(TickEvent.ServerTickEvent.Post.class, event -> CommonEvents.serverTick(event.getServer()));
        //#elif MC>=1.19
        //$$ ForgeBus.on(TickEvent.ServerTickEvent.class, event -> {
        //$$     if (event.phase == TickEvent.Phase.END) {
        //$$         CommonEvents.serverTick(event.getServer());
        //$$     }
        //$$ });
        //#else
        //$$ ForgeBus.on(TickEvent.ServerTickEvent.class, event -> {
        //$$     if (event.phase == TickEvent.Phase.END) {
        //$$         CommonEvents.serverTick(ServerLifecycleHooks.getCurrentServer());
        //$$     }
        //$$ });
        //#endif
        // Fires before the vanilla join line is broadcast, so the presence filter is in place in time.
        ForgeBus.on(OnDatapackSyncEvent.class, event -> {
            if (event.getPlayer() != null) {
                CommonEvents.beforeJoinMessage(event.getPlayer());
            }
        });
        ForgeBus.on(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CommonEvents.login(player);
            }
        });
        ForgeBus.on(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CommonEvents.logout(player);
            }
        });
        ForgeBus.on(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CommonEvents.changedDimension(player, event.getTo());
            }
        });
        ForgeBus.on(PlayerEvent.PlayerRespawnEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CommonEvents.respawn(player, event.isEndConquered());
            }
        });
        ForgeBus.on(LivingDeathEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CommonEvents.death(player, Mc.damageCause(event.getSource()));
            }
        });
        ForgeBus.on(LivingDamageEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CommonEvents.damaged(player, event.getAmount());
            }
        });
        //#if MC>=1.19
        ForgeBus.cancelIf(ServerChatEvent.class, event -> CommonEvents.chat(event.getPlayer(), event.getRawText()));
        //#else
        //$$ ForgeBus.cancelIf(ServerChatEvent.class, event -> CommonEvents.chat(event.getPlayer(), event.getMessage()));
        //#endif
        ForgeBus.on(PlayerEvent.NameFormat.class, event -> {
            //#if MC>=1.19
            Player player = event.getEntity();
            //#else
            //$$ Player player = event.getPlayer();
            //#endif
            Component name = event.getDisplayname();
            event.setDisplayname(Mc.level(player).isClientSide() ? ForgeClient.nametag(player, name) : CommonEvents.displayName(player, name));
        });
        ForgeBus.on(PlayerEvent.TabListNameFormat.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                Component name = CommonEvents.tabListName(player);
                if (name != null) {
                    event.setDisplayName(name);
                }
            }
        });
        ForgeBus.cancelIf(EntityTravelToDimensionEvent.class, event -> CommonEvents.travel(event.getEntity(), event.getDimension()));
        //#if MC>=1.19
        ForgeBus.cancelIf(BlockEvent.PortalSpawnEvent.class, event ->
            event.getLevel() instanceof Level level && CommonEvents.portalSpawn(level, event.getPos(), event.getState()));
        ForgeBus.on(BlockEvent.BreakEvent.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                CommonEvents.blockBroken(level, event.getPos(), event.getState());
            }
        });
        ForgeBus.cancelIf(PlayerInteractEvent.RightClickBlock.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                && CommonEvents.useBlock(player, level, event.getPos(), event.getItemStack())) {
                event.setCancellationResult(InteractionResult.FAIL);
                return true;
            }
            return false;
        });
        ForgeBus.cancelIf(EntityJoinLevelEvent.class, event -> event.loadedFromDisk() && CommonEvents.discardOnLoad(event.getEntity()));
        //#else
        //$$ ForgeBus.cancelIf(BlockEvent.PortalSpawnEvent.class, event ->
        //$$     event.getWorld() instanceof Level level && CommonEvents.portalSpawn(level, event.getPos(), event.getState()));
        //$$ ForgeBus.on(BlockEvent.BreakEvent.class, event -> {
        //$$     if (event.getWorld() instanceof ServerLevel level) {
        //$$         CommonEvents.blockBroken(level, event.getPos(), event.getState());
        //$$     }
        //$$ });
        //$$ ForgeBus.cancelIf(PlayerInteractEvent.RightClickBlock.class, event -> {
        //$$     if (event.getEntity() instanceof ServerPlayer player && event.getWorld() instanceof ServerLevel level
        //$$         && CommonEvents.useBlock(player, level, event.getPos(), event.getItemStack())) {
        //$$         event.setCancellationResult(InteractionResult.FAIL);
        //$$         return true;
        //$$     }
        //$$     return false;
        //$$ });
        //$$ ForgeBus.cancelIf(EntityJoinWorldEvent.class, event -> event.loadedFromDisk() && CommonEvents.discardOnLoad(event.getEntity()));
        //#endif
        ForgeBus.cancelIf(LivingChangeTargetEvent.class, event -> CommonEvents.blockTarget(event.getNewTarget()));
        ForgeBus.cancelIf(EntityItemPickupEvent.class, event ->
            event.getEntity() instanceof ServerPlayer player && CommonEvents.blockPickup(player));
    }
}
