//#if MC<1.20.2
//$$ package com.arthou.smputilities.mixin;
//$$
//$$ import com.arthou.smputilities.platform.ClientLanguage;
//$$ import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Unique;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ /** Remembers the client language from its options packet (vanilla dropped it before 1.20.2). */
//$$ @Mixin(ServerPlayer.class)
//$$ public abstract class ServerPlayerLanguageMixin implements ClientLanguage {
//$$     @Unique
//$$     private String smputilities$language = "en_us";
//$$
//$$     @Inject(method = "updateOptions", at = @At("HEAD"))
//$$     private void smputilities$rememberLanguage(ServerboundClientInformationPacket packet, CallbackInfo ci) {
//$$         if (packet.language() != null && !packet.language().isEmpty()) {
//$$             smputilities$language = packet.language();
//$$         }
//$$     }
//$$
//$$     @Override
//$$     public String smputilities$language() {
//$$         return smputilities$language;
//$$     }
//$$ }
//#endif
