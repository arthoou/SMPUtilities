package com.arthou.smputilities.fabric.mixin;

import com.arthou.smputilities.platform.CommonEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Logout on the server thread, before the player leaves the world, where
 * Forge and NeoForge fire PlayerLoggedOutEvent. Fabric API's DISCONNECT
 * event can run on the network thread when the client closes the
 * connection.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void smputilities$logout(ServerPlayer player, CallbackInfo ci) {
        CommonEvents.logout(player);
    }
}
