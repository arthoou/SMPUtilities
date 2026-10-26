package com.arthou.smputilities.mixin;

import com.arthou.smputilities.blocklog.BlockLogService;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block log: block changes made while the server handles a player's dig or use packet are that player's. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class BlockLogActionMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleUseItemOn", at = @At("HEAD"))
    private void smputilities$useOnStart(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        BlockLogService.beginPlayer(player, packet.getHitResult().getBlockPos());
    }

    @Inject(method = "handleUseItemOn", at = @At("RETURN"))
    private void smputilities$useOnEnd(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        BlockLogService.endPlayer(player);
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"))
    private void smputilities$useStart(ServerboundUseItemPacket packet, CallbackInfo ci) {
        BlockLogService.beginPlayer(player, null);
    }

    @Inject(method = "handleUseItem", at = @At("RETURN"))
    private void smputilities$useEnd(ServerboundUseItemPacket packet, CallbackInfo ci) {
        BlockLogService.endPlayer(player);
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"))
    private void smputilities$actionStart(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        BlockLogService.beginPlayer(player, packet.getPos());
    }

    @Inject(method = "handlePlayerAction", at = @At("RETURN"))
    private void smputilities$actionEnd(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        BlockLogService.endPlayer(player);
    }
}
