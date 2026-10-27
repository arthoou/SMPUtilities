package com.arthou.smputilities.fabric;

import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.platform.CommonEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;
//#if MC>=1.19
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.PlayerChatMessage;
//#else
//$$ import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
//#endif

/**
 * Forwards Fabric API events to {@link CommonEvents}. What Fabric API has no
 * event for (deaths, nametags, the tab list name, dimension travel, mob
 * targets, item pickup, portal ignition) comes from the mixins in
 * {@code com.arthou.smputilities.fabric.mixin}, which call the hooks here.
 */
public final class FabricEvents {
    /** Entities to discard after the tick: removing them while they are being loaded is not safe. */
    private static final Queue<Entity> DISCARD = new ConcurrentLinkedQueue<>();
    /** The client nametag hook, installed by the client glue. */
    private static volatile BiFunction<Player, Component, Component> clientNametag = (player, name) -> name;

    private FabricEvents() {
    }

    static void register() {
        //#if MC>=1.19
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> CommonEvents.registerCommands(dispatcher));
        //#else
        //$$ CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> CommonEvents.registerCommands(dispatcher));
        //#endif
        ServerLifecycleEvents.SERVER_STARTING.register(CommonEvents::serverStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(CommonEvents::serverStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(CommonEvents::serverStopped);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            CommonEvents.serverTick(server);
            for (Entity entity; (entity = DISCARD.poll()) != null; ) {
                entity.discard();
            }
        });
        // INIT runs while the player is being placed, before the vanilla join line is broadcast.
        ServerPlayConnectionEvents.INIT.register((handler, server) -> CommonEvents.beforeJoinMessage(handler.player));
        // Login and logout: PlayerListMixin, where Forge fires its events (JOIN comes before the player is listed).
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
            CommonEvents.changedDimension(player, destination.dimension()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> CommonEvents.respawn(newPlayer, alive));
        //#if MC>=1.19
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player) {
                CommonEvents.damaged(player, amount);
            }
            return true;
        });
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> !CommonEvents.chat(sender, rawText(message)));
        //#endif
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel
                && CommonEvents.useBlock(serverPlayer, serverLevel, hit.getBlockPos(), player.getItemInHand(hand))) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level instanceof ServerLevel serverLevel) {
                CommonEvents.blockBroken(serverLevel, pos, state);
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (CommonEvents.discardOnLoad(entity)) {
                DISCARD.add(entity);
            }
        });
    }

    //#if MC>=1.19
    private static String rawText(PlayerChatMessage message) {
        //#if MC>=1.19.3
        return message.signedContent();
        //#else
        //$$ return message.signedContent().plain();
        //#endif
    }
    //#endif

    // ------------------------------------------------------- mixin hooks

    static void installClientNametag(BiFunction<Player, Component, Component> hook) {
        clientNametag = hook;
    }

    /** The name inside {@code Player#getDisplayName}, before team colors are added. */
    public static Component displayName(Player player, Component name) {
        if (Mc.level(player).isClientSide()) {
            return clientNametag.apply(player, name);
        }
        return CommonEvents.displayName(player, name);
    }
}
