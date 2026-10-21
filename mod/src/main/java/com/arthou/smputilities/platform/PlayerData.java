package com.arthou.smputilities.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * SMPutilities data stored inside the player's own save, so it is written
 * together with the inventory, and copied to the new player entity on
 * respawn. Implemented on ServerPlayer by a mixin on every loader.
 */
public interface PlayerData {
    String KEY = "SmpUtilities";

    CompoundTag smputilities$data();

    void smputilities$setData(CompoundTag data);

    static CompoundTag of(ServerPlayer player) {
        return ((PlayerData) player).smputilities$data();
    }
}
