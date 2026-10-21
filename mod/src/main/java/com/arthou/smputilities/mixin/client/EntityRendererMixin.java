//#if MC>=1.21.9
package com.arthou.smputilities.mixin.client;

import com.arthou.smputilities.client.SuClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * From 1.21.9 the player renderer has its own shouldShowName, so hidden
 * nameplates are also cleared from the render state once it is filled.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
        at = @At("TAIL"), require = 0)
    private void smputilities$hideNameplate(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        if (state.nameTag != null && Boolean.FALSE.equals(SuClient.nameplateVisible(entity))) {
            state.nameTag = null;
        }
    }
}
//#endif
