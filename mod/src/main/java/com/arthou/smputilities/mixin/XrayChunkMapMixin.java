//#if MC<1.20.2
//$$ package com.arthou.smputilities.mixin;
//$$
//$$ import com.arthou.smputilities.antixray.AntiXray;
//$$ import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
//$$ import net.minecraft.server.level.ChunkMap;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.world.level.chunk.LevelChunk;
//$$ import org.apache.commons.lang3.mutable.MutableObject;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Unique;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ /**
//$$  * Anti-xray before 1.20.2: one chunk packet is made for the first player
//$$  * that needs a chunk and reused for the others. A player allowed to see the
//$$  * real blocks gets a packet of their own, which is not shared.
//$$  */
//$$ @Mixin(ChunkMap.class)
//$$ public abstract class XrayChunkMapMixin {
//$$     @Unique
//$$     private ClientboundLevelChunkWithLightPacket smputilities$shared;
//$$     @Unique
//$$     private boolean smputilities$own;
//$$
//$$     @Inject(method = "playerLoadedChunk", at = @At("HEAD"))
//$$     private void smputilities$recipient(ServerPlayer player, MutableObject<ClientboundLevelChunkWithLightPacket> packet, LevelChunk chunk, CallbackInfo ci) {
//$$         AntiXray.beginSend(player);
//$$         smputilities$own = AntiXray.active(chunk.getLevel()) && AntiXray.recipientBypasses();
//$$         if (smputilities$own) {
//$$             smputilities$shared = packet.getValue();
//$$             packet.setValue(null);
//$$         }
//$$     }
//$$
//$$     @Inject(method = "playerLoadedChunk", at = @At("RETURN"))
//$$     private void smputilities$sent(ServerPlayer player, MutableObject<ClientboundLevelChunkWithLightPacket> packet, LevelChunk chunk, CallbackInfo ci) {
//$$         if (smputilities$own) {
//$$             packet.setValue(smputilities$shared);
//$$             smputilities$shared = null;
//$$             smputilities$own = false;
//$$         }
//$$         AntiXray.endSend();
//$$     }
//$$ }
//#endif
