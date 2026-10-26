package com.arthou.smputilities.mixin;

import com.arthou.smputilities.staff.MaintenanceService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;
//#if MC>=1.21.9
import net.minecraft.server.players.NameAndId;
//#else
//$$ import com.arthou.smputilities.mc.Mc;
//$$ import com.mojang.authlib.GameProfile;
//#endif

/** Maintenance mode: players who may not join are refused with the maintenance message. */
@Mixin(PlayerList.class)
public abstract class MaintenanceLoginMixin {
    @Shadow
    @Final
    private MinecraftServer server;

    //#if MC>=1.21.9
    @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    private void smputilities$maintenance(SocketAddress address, NameAndId player, CallbackInfoReturnable<Component> cir) {
        if (cir.getReturnValue() == null) {
            Component refused = MaintenanceService.refuse(server, player.id(), player.name());
            if (refused != null) {
                cir.setReturnValue(refused);
            }
        }
    }
    //#else
    //$$ @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    //$$ private void smputilities$maintenance(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Component> cir) {
    //$$     if (cir.getReturnValue() == null) {
    //$$         Component refused = MaintenanceService.refuse(server, Mc.id(profile), Mc.name(profile));
    //$$         if (refused != null) {
    //$$             cir.setReturnValue(refused);
    //$$         }
    //$$     }
    //$$ }
    //#endif
}
