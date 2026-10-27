//#if MC>=1.19 && MC<1.20.2
//$$ package com.arthou.smputilities.forge.mixin;
//$$
//$$ import com.arthou.smputilities.platform.CommonEvents;
//$$ import net.minecraft.network.chat.ChatType;
//$$ import net.minecraft.network.chat.PlayerChatMessage;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.players.PlayerList;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ /**
//$$  * Chat on Forge 1.19 to 1.20.1, which fires ServerChatEvent on a worker thread:
//$$  * handled here instead, on the server thread, where Fabric's chat event runs too.
//$$  */
//$$ @Mixin(PlayerList.class)
//$$ public abstract class ChatBroadcastMixin {
//$$     @Inject(method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
//$$         at = @At("HEAD"), cancellable = true)
//$$     private void smputilities$chat(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound bound, CallbackInfo ci) {
        //#if MC>=1.19.3
        //$$ String raw = message.signedContent();
        //#else
        //$$ String raw = message.signedContent().plain();
        //#endif
//$$         if (CommonEvents.chat(sender, raw)) {
//$$             ci.cancel();
//$$         }
//$$     }
//$$ }
//#endif
