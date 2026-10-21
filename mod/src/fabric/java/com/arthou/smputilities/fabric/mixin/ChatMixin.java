//#if MC<1.19
//$$ package com.arthou.smputilities.fabric.mixin;
//$$
//$$ import com.arthou.smputilities.platform.CommonEvents;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.network.ServerGamePacketListenerImpl;
//$$ import net.minecraft.server.network.TextFilter;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ /** Chat before 1.19, when Fabric API had no message event. */
//$$ @Mixin(ServerGamePacketListenerImpl.class)
//$$ public abstract class ChatMixin {
//$$     @Shadow
//$$     public ServerPlayer player;
//$$
//$$     @Inject(method = "handleChat(Lnet/minecraft/server/network/TextFilter$FilteredText;)V", at = @At("HEAD"), cancellable = true)
//$$     private void smputilities$chat(TextFilter.FilteredText text, CallbackInfo ci) {
//$$         String raw = text.getRaw();
//$$         if (!raw.startsWith("/") && CommonEvents.chat(player, raw)) {
//$$             ci.cancel();
//$$         }
//$$     }
//$$ }
//#endif
