package com.arthou.smputilities.mixin;

import com.arthou.smputilities.blocklog.BlockLogService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//#if MC>=1.21.3
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
//#else
//$$ import net.minecraft.world.level.Explosion;
//$$ import net.minecraft.world.level.Level;
//#endif
//#if MC>=1.21.9
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#else
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#endif

/** Block log: blocks an explosion destroys are logged as "#explosion". */
//#if MC>=1.21.3
@Mixin(ServerExplosion.class)
//#else
//$$ @Mixin(Explosion.class)
//#endif
public abstract class BlockLogExplosionMixin {
    //#if MC>=1.21.3
    @Shadow
    @Final
    private ServerLevel level;
    //#else
    //$$ @Shadow
    //$$ @Final
    //$$ private Level level;
    //#endif

    //#if MC>=1.21.9
    @Inject(method = "explode", at = @At("HEAD"))
    private void smputilities$start(CallbackInfoReturnable<Integer> cir) {
        BlockLogService.beginExplosion(level);
    }

    @Inject(method = "explode", at = @At("RETURN"))
    private void smputilities$end(CallbackInfoReturnable<Integer> cir) {
        BlockLogService.endExplosion(level);
    }
    //#elif MC>=1.21.3
    //$$ @Inject(method = "explode", at = @At("HEAD"))
    //$$ private void smputilities$start(CallbackInfo ci) {
    //$$     BlockLogService.beginExplosion(level);
    //$$ }
    //$$
    //$$ @Inject(method = "explode", at = @At("RETURN"))
    //$$ private void smputilities$end(CallbackInfo ci) {
    //$$     BlockLogService.endExplosion(level);
    //$$ }
    //#else
    //$$ @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    //$$ private void smputilities$start(boolean particles, CallbackInfo ci) {
    //$$     BlockLogService.beginExplosion(level);
    //$$ }
    //$$
    //$$ @Inject(method = "finalizeExplosion", at = @At("RETURN"))
    //$$ private void smputilities$end(boolean particles, CallbackInfo ci) {
    //$$     BlockLogService.endExplosion(level);
    //$$ }
    //#endif
}
