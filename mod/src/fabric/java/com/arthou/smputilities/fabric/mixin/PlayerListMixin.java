package com.arthou.smputilities.fabric.mixin;

import com.arthou.smputilities.platform.CommonEvents;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC>=1.20.2
import net.minecraft.server.network.CommonListenerCookie;
//#endif

/**
 * Login and logout where Forge and NeoForge fire PlayerLoggedInEvent and
 * PlayerLoggedOutEvent. Fabric API's JOIN event fires in the middle of
 * placeNewPlayer, before the player is in the player list (so they missed
 * their own join line), and DISCONNECT can run on the network thread.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    //#if MC>=1.20.2
    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void smputilities$login(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
    //#else
    //$$ @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    //$$ private void smputilities$login(Connection connection, ServerPlayer player, CallbackInfo ci) {
    //#endif
        CommonEvents.login(player);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void smputilities$logout(ServerPlayer player, CallbackInfo ci) {
        CommonEvents.logout(player);
    }
}
