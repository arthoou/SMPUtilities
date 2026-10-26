//#if MC>=1.20.2
package com.arthou.smputilities.mixin;

import com.arthou.smputilities.antixray.AntiXray;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Anti-xray: tells the chunk packet who it is for, so players allowed to see the real blocks get them. */
@Mixin(PlayerChunkSender.class)
public abstract class XrayChunkSenderMixin {
    @Inject(method = "sendChunk", at = @At("HEAD"))
    private static void smputilities$recipient(ServerGamePacketListenerImpl listener, ServerLevel level, LevelChunk chunk, CallbackInfo ci) {
        AntiXray.beginSend(listener.player);
    }

    @Inject(method = "sendChunk", at = @At("RETURN"))
    private static void smputilities$sent(ServerGamePacketListenerImpl listener, ServerLevel level, LevelChunk chunk, CallbackInfo ci) {
        AntiXray.endSend();
    }
}
//#endif
