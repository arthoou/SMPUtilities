//#if MC<1.20.2
//$$ package com.arthou.smputilities.mixin;
//$$
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.gen.Accessor;
//$$
//$$ /** The client language before 1.20.2, when ServerPlayer kept it in a private field. */
//$$ @Mixin(ServerPlayer.class)
//$$ public interface ServerPlayerAccessor {
//$$     @Accessor("language")
//$$     String smputilities$language();
//$$ }
//#endif
