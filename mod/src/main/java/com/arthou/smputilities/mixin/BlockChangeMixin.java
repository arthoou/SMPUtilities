package com.arthou.smputilities.mixin;

import com.arthou.smputilities.antixray.AntiXray;
import com.arthou.smputilities.blocklog.BlockLogService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block changes on the server: the anti-xray sends the real blocks around a
 * solid block that gives way (AntiXray's onBlockChange), and the block log
 * records changes made by players and explosions.
 */
@Mixin(Level.class)
public abstract class BlockChangeMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void smputilities$beforeChange(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerLevel level) {
            AntiXray.beforeBlockChange(level, pos, state);
            BlockLogService.beforeSetBlock(level, pos);
        }
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
    private void smputilities$afterChange(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerLevel level) {
            BlockLogService.afterSetBlock(level, cir.getReturnValueZ());
        }
    }
}
