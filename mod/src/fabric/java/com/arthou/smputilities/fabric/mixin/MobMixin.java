package com.arthou.smputilities.fabric.mixin;

import com.arthou.smputilities.platform.CommonEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mobs ignore vanished staff. */
@Mixin(Mob.class)
public abstract class MobMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void smputilities$target(LivingEntity target, CallbackInfo ci) {
        if (target != null && CommonEvents.blockTarget(target)) {
            ci.cancel();
        }
    }
}
