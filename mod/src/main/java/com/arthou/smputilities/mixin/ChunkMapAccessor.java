package com.arthou.smputilities.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    /** Values are {@code ChunkMap.TrackedEntity}, which is not visible outside its package. */
    @Accessor("entityMap")
    Int2ObjectMap<?> smputilities$entityMap();
}
