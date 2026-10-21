package com.arthou.smputilities.mixin.client;

import com.arthou.smputilities.client.SuClient;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nameplate visibility, as in HiddenNames: hidden nameplates, nameplates
 * hidden behind blocks and your own nameplate in third person.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    //#if MC>=1.21.3
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void smputilities$nameplate(LivingEntity entity, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
    //#else
    //$$ @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    //$$ private void smputilities$nameplate(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
    //#endif
        Boolean visible = SuClient.nameplateVisible(entity);
        if (visible != null) {
            cir.setReturnValue(visible);
        }
    }
}
