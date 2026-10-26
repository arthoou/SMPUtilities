package com.arthou.smputilities.mixin;

import com.arthou.smputilities.blocklog.BlockLogService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

/** Block log: a container's contents when a player opens it and when they close it. */
@Mixin(ServerPlayer.class)
public abstract class BlockLogContainerMixin {
    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;", at = @At("RETURN"))
    private void smputilities$opened(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (cir.getReturnValue().isPresent()) {
            BlockLogService.openedMenu((ServerPlayer) (Object) this);
        }
    }

    @Inject(method = "doCloseContainer", at = @At("HEAD"))
    private void smputilities$closing(CallbackInfo ci) {
        BlockLogService.closingMenu((ServerPlayer) (Object) this);
    }
}
