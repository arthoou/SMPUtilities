package com.arthou.smputilities.fabric.mixin;

import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.platform.CommonEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#if MC>=1.21.2
import net.minecraft.world.level.portal.TeleportTransition;
//#elif MC>=1.21
//$$ import net.minecraft.world.entity.Entity;
//$$ import net.minecraft.world.level.portal.DimensionTransition;
//#else
//$$ import net.minecraft.server.level.ServerLevel;
//$$ import net.minecraft.world.entity.Entity;
//#endif

/** Player hooks Fabric API has no event for. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    private ServerPlayer smputilities$self() {
        return (ServerPlayer) (Object) this;
    }

    /** Before the drops, so the death backup still has the inventory. */
    @Inject(method = "die", at = @At("HEAD"))
    private void smputilities$death(DamageSource source, CallbackInfo ci) {
        CommonEvents.death(smputilities$self(), Mc.damageCause(source));
    }

    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void smputilities$tabListName(CallbackInfoReturnable<Component> cir) {
        Component name = CommonEvents.tabListName(smputilities$self());
        if (name != null) {
            cir.setReturnValue(name);
        }
    }

    //#if MC>=1.21.2
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
        at = @At("HEAD"), cancellable = true, require = 0)
    private void smputilities$travel(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer self = smputilities$self();
        if (transition.newLevel() != self.level() && CommonEvents.travel(self, transition.newLevel().dimension())) {
            cir.setReturnValue(null);
        }
    }
    //#elif MC>=1.21
    //$$ @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true, require = 0)
    //$$ private void smputilities$travel(DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
    //$$     ServerPlayer self = smputilities$self();
    //$$     if (transition.newLevel() != self.level() && CommonEvents.travel(self, transition.newLevel().dimension())) {
    //$$         cir.setReturnValue(null);
    //$$     }
    //$$ }
    //#else
    //$$ @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true, require = 0)
    //$$ private void smputilities$travel(ServerLevel destination, CallbackInfoReturnable<Entity> cir) {
    //$$     if (CommonEvents.travel(smputilities$self(), destination.dimension())) {
    //$$         cir.setReturnValue(null);
    //$$     }
    //$$ }
    //#endif

    //#if MC<1.19
    //$$ /** Fabric API has no damage event before 1.19. */
    //$$ @Inject(method = "hurt", at = @At("HEAD"))
    //$$ private void smputilities$damaged(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
    //$$     CommonEvents.damaged(smputilities$self(), amount);
    //$$ }
    //#endif
}
