package com.arthou.smputilities.fabric.mixin;

import com.arthou.smputilities.platform.CommonEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nether portal limits: a portal that may not be lit is never filled in. */
@Mixin(PortalShape.class)
public abstract class PortalShapeMixin {
    //#if MC>=1.21.2
    @Shadow
    @Final
    private BlockPos bottomLeft;

    @Inject(method = "createPortalBlocks", at = @At("HEAD"), cancellable = true)
    private void smputilities$portal(LevelAccessor level, CallbackInfo ci) {
        if (level instanceof Level world && bottomLeft != null && CommonEvents.portalSpawn(world, bottomLeft, Blocks.FIRE.defaultBlockState())) {
            ci.cancel();
        }
    }
    //#else
    //$$ @Shadow
    //$$ private BlockPos bottomLeft;
    //$$
    //$$ @Shadow
    //$$ @Final
    //$$ private LevelAccessor level;
    //$$
    //$$ @Inject(method = "createPortalBlocks", at = @At("HEAD"), cancellable = true)
    //$$ private void smputilities$portal(CallbackInfo ci) {
    //$$     if (level instanceof Level world && bottomLeft != null && CommonEvents.portalSpawn(world, bottomLeft, Blocks.FIRE.defaultBlockState())) {
    //$$         ci.cancel();
    //$$     }
    //$$ }
    //#endif
}
