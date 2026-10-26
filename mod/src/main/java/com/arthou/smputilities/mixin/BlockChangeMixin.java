package com.arthou.smputilities.mixin;

import com.arthou.smputilities.antixray.AntiXray;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Anti-xray: a solid block that gives way uncovers the real blocks around it (AntiXray's onBlockChange). */
@Mixin(Level.class)
public abstract class XrayBlockChangeMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void smputilities$uncover(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerLevel level) {
            AntiXray.beforeBlockChange(level, pos, state);
        }
    }
}
