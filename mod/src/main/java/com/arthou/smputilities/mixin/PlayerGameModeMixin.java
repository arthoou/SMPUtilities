package com.arthou.smputilities.mixin;

import com.arthou.smputilities.antixray.AntiXray;
import com.arthou.smputilities.antixray.XrayAlerts;
import com.arthou.smputilities.blocklog.BlockLogService;
import com.arthou.smputilities.moderation.FreezeJailService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Digging sends the real blocks around the dug block (AntiXray's
 * onPlayerLeftClickBlock), every block a player breaks is reported to the
 * x-ray alerts, and staff inspecting the block log see a block's history
 * when they hit it instead of breaking it.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class PlayerGameModeMixin {
    @Shadow
    protected ServerLevel level;
    @Shadow
    @Final
    protected ServerPlayer player;

    @Unique
    private BlockState smputilities$breaking;

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
    //#if MC>=1.19
    private void smputilities$inspect(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int maxBuildHeight, int sequence, CallbackInfo ci) {
    //#else
    //$$ private void smputilities$inspect(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int maxBuildHeight, CallbackInfo ci) {
    //#endif
        if (FreezeJailService.isFrozen(player)
            || action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK && BlockLogService.inspect(player, level, pos)) {
            player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockBreakAction", at = @At("TAIL"))
    //#if MC>=1.19
    private void smputilities$digging(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int maxBuildHeight, int sequence, CallbackInfo ci) {
    //#else
    //$$ private void smputilities$digging(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int maxBuildHeight, CallbackInfo ci) {
    //#endif
        AntiXray.digging(level, pos);
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void smputilities$beforeBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        smputilities$breaking = level.getBlockState(pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void smputilities$afterBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = smputilities$breaking;
        smputilities$breaking = null;
        if (state != null && cir.getReturnValueZ()) {
            XrayAlerts.broken(player, level, pos, state);
        }
    }
}
