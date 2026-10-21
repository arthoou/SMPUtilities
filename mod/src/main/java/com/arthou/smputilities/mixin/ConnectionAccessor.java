package com.arthou.smputilities.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The Netty channel of a connection, for the presence filter (no public getter on every version). */
@Mixin(Connection.class)
public interface ConnectionAccessor {
    @Accessor("channel")
    Channel smputilities$channel();
}
