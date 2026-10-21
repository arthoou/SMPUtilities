//#if MC>=1.21.3 && MC<1.21.9
package com.arthou.smputilities.forge.mixin.client;

import com.arthou.smputilities.client.bubble.BubbleRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Speech bubbles on Forge 1.21.3 to 1.21.8, which has no level render event:
 * drawn right after the entities. The model-view matrix carries the camera
 * there, so an identity pose stack is camera-relative.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderEntities", at = @At("TAIL"), require = 0)
    private void smputilities$bubbles(CallbackInfo ci) {
        BubbleRenderer.renderWorld(new PoseStack());
    }
}
//#endif
