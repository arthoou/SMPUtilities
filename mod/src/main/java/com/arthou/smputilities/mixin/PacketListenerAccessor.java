package com.arthou.smputilities.mixin;

import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
//#if MC>=1.20.2
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
//#else
//$$ import net.minecraft.server.network.ServerGamePacketListenerImpl;
//#endif

/** A player's connection; vanilla has no public getter on every version. */
//#if MC>=1.20.2
@Mixin(ServerCommonPacketListenerImpl.class)
//#else
//$$ @Mixin(ServerGamePacketListenerImpl.class)
//#endif
public interface PacketListenerAccessor {
    @Accessor("connection")
    Connection smputilities$connection();
}
