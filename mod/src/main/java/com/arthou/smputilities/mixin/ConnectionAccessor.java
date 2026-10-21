package com.arthou.smputilities.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The Netty channel of a connection; only Forge and NeoForge add a public getter. */
@Mixin(Connection.class)
public interface ConnectionAccessor {
    @Accessor("channel")
    Channel smputilities$channel();
}
