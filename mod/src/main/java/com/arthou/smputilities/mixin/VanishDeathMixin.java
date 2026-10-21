package com.arthou.smputilities.mixin;

import com.arthou.smputilities.vanish.VanishService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While a vanished player dies, their death message goes only to those who may see them. */
@Mixin(ServerPlayer.class)
public abstract class VanishDeathMixin {
    @Inject(method = "die", at = @At("HEAD"))
    private void smputilities$deathStart(CallbackInfo ci) {
        VanishService.beginAnnouncement((ServerPlayer) (Object) this);
    }

    @Inject(method = "die", at = @At("RETURN"))
    private void smputilities$deathEnd(CallbackInfo ci) {
        VanishService.endAnnouncement();
    }
}
