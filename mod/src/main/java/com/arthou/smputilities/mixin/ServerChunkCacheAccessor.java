package com.arthou.smputilities.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccessor {
    /** The holder of a chunk that is loaded at any generation status, or null. */
    @Invoker("getVisibleChunkIfPresent")
    ChunkHolder smputilities$visibleChunk(long pos);
}
