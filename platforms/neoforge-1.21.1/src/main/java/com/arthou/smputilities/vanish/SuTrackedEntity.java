package com.arthou.smputilities.vanish;

import net.minecraft.server.level.ServerPlayer;

/** Implemented on {@code ChunkMap.TrackedEntity} so vanish can re-evaluate one viewer right away. */
public interface SuTrackedEntity {
    void smputilities$update(ServerPlayer viewer);
}
