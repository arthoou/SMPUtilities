package com.arthou.smputilities.mixin;

import com.arthou.smputilities.antixray.AntiXray;
import com.arthou.smputilities.antixray.XrayPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Anti-xray: a chunk packet made on the server remembers its chunk, and its
 * block data is written with the hidden blocks replaced. The work happens on
 * the network thread that encodes the packet, once per packet.
 */
@Mixin(ClientboundLevelChunkPacketData.class)
public abstract class XrayChunkPacketMixin implements XrayPacketData {
    @Shadow
    @Final
    private byte[] buffer;

    @Unique
    private AntiXray.Pending smputilities$pending;
    @Unique
    private byte[] smputilities$hidden;

    @Inject(method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At("TAIL"))
    private void smputilities$capture(LevelChunk chunk, CallbackInfo ci) {
        smputilities$pending = AntiXray.capture(chunk);
    }

    @Redirect(method = "write", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
        target = "Lnet/minecraft/network/protocol/game/ClientboundLevelChunkPacketData;buffer:[B"))
    private byte[] smputilities$hiddenBuffer(ClientboundLevelChunkPacketData self) {
        return smputilities$sentBuffer();
    }

    @Override
    public byte[] smputilities$rawBuffer() {
        return buffer;
    }

    @Override
    public byte[] smputilities$sentBuffer() {
        synchronized (this) {
            if (smputilities$hidden == null) {
                AntiXray.Pending pending = smputilities$pending;
                // Written once; the neighbours are not needed afterwards.
                smputilities$pending = null;
                smputilities$hidden = pending == null ? buffer : AntiXray.hide(pending, buffer);
            }
            return smputilities$hidden;
        }
    }
}
