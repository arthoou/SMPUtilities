package com.arthou.smputilities.mixin;

import com.arthou.smputilities.chat.PresenceFilter;
import com.arthou.smputilities.vanish.VanishService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC<1.19
//$$ import net.minecraft.network.chat.ChatType;
//$$ import java.util.UUID;
//#endif

/**
 * Death and advancement lines of a vanished player (see VanishDeathMixin and
 * VanishAdvancementMixin) reach only the players who may see them, and the
 * vanilla join/leave lines are dropped when SMPutilities sends its own.
 */
@Mixin(PlayerList.class)
public abstract class VanishBroadcastMixin {
    //#if MC>=1.19
    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), cancellable = true)
    private void smputilities$announcement(Component message, boolean overlay, CallbackInfo ci) {
        if (PresenceFilter.dropAtSource(message) || VanishService.redirectAnnouncement(message)) {
            ci.cancel();
        }
    }

    @Inject(method = {"broadcastSystemToTeam", "broadcastSystemToAllExceptTeam"}, at = @At("HEAD"), cancellable = true)
    private void smputilities$teamAnnouncement(Player player, Component message, CallbackInfo ci) {
        if (VanishService.redirectAnnouncement(message)) {
            ci.cancel();
        }
    }
    //#else
    //$$ @Inject(method = "broadcastMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/ChatType;Ljava/util/UUID;)V",
    //$$     at = @At("HEAD"), cancellable = true)
    //$$ private void smputilities$announcement(Component message, ChatType type, UUID sender, CallbackInfo ci) {
    //$$     if (PresenceFilter.dropAtSource(message) || VanishService.redirectAnnouncement(message)) {
    //$$         ci.cancel();
    //$$     }
    //$$ }
    //$$
    //$$ @Inject(method = {"broadcastToTeam", "broadcastToAllExceptTeam"}, at = @At("HEAD"), cancellable = true)
    //$$ private void smputilities$teamAnnouncement(Player player, Component message, CallbackInfo ci) {
    //$$     if (VanishService.redirectAnnouncement(message)) {
    //$$         ci.cancel();
    //$$     }
    //$$ }
    //#endif
}
