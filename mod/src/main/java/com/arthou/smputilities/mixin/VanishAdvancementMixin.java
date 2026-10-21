package com.arthou.smputilities.mixin;

import com.arthou.smputilities.vanish.VanishService;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While a vanished player earns an advancement, its chat line goes only to those who may see them. */
@Mixin(PlayerAdvancements.class)
public abstract class VanishAdvancementMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("HEAD"))
    private void smputilities$awardStart(CallbackInfoReturnable<Boolean> cir) {
        VanishService.beginAnnouncement(player);
    }

    @Inject(method = "award", at = @At("RETURN"))
    private void smputilities$awardEnd(CallbackInfoReturnable<Boolean> cir) {
        VanishService.endAnnouncement();
    }
}
