package com.arthou.smputilities.fabric.mixin;

import com.arthou.smputilities.platform.CommonEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#if MC>=1.21.2
import net.minecraft.world.level.portal.TeleportTransition;
//#elif MC>=1.21
//$$ import net.minecraft.world.level.portal.DimensionTransition;
//#else
//$$ import net.minecraft.server.level.ServerLevel;
//#endif

/** Dimension travel of non-player entities (players have their own override, see ServerPlayerMixin). */
@Mixin(Entity.class)
public abstract class EntityMixin {
    //#if MC>=1.21.2
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;",
        at = @At("HEAD"), cancellable = true, require = 0)
    private void smputilities$travel(TeleportTransition transition, CallbackInfoReturnable<Entity> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof ServerPlayer) && transition.newLevel() != self.level() && CommonEvents.travel(self, transition.newLevel().dimension())) {
            cir.setReturnValue(null);
        }
    }
    //#elif MC>=1.21
    //$$ @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true, require = 0)
    //$$ private void smputilities$travel(DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
    //$$     Entity self = (Entity) (Object) this;
    //$$     if (!(self instanceof ServerPlayer) && transition.newLevel() != self.level() && CommonEvents.travel(self, transition.newLevel().dimension())) {
    //$$         cir.setReturnValue(null);
    //$$     }
    //$$ }
    //#else
    //$$ @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true, require = 0)
    //$$ private void smputilities$travel(ServerLevel destination, CallbackInfoReturnable<Entity> cir) {
    //$$     Entity self = (Entity) (Object) this;
    //$$     if (!(self instanceof ServerPlayer) && CommonEvents.travel(self, destination.dimension())) {
    //$$         cir.setReturnValue(null);
    //$$     }
    //$$ }
    //#endif
}
