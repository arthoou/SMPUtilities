package com.arthou.smputilities.backup;

import com.arthou.smputilities.mc.Nbt;
import net.minecraft.nbt.CompoundTag;

/**
 * One backup. {@code playerData} is the player's save tag at that moment and
 * {@code compatData} holds Curios / Cosmetic Armor slots when those mods exist.
 * Tags are never modified after creation; readers copy them.
 */
public record PlayerSnapshot(String id, long timestamp, SnapshotType type, String detail, String createdBy,
                             CompoundTag playerData, CompoundTag compatData) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putLong("timestamp", timestamp);
        tag.putString("type", type.name());
        tag.putString("detail", detail == null ? "" : detail);
        tag.putString("createdBy", createdBy == null ? "" : createdBy);
        tag.put("playerData", playerData);
        tag.put("compatData", compatData == null ? new CompoundTag() : compatData);
        return tag;
    }

    public static PlayerSnapshot load(CompoundTag tag, String fallbackId) {
        String id = Nbt.getString(tag, "id");
        return new PlayerSnapshot(
            id.isEmpty() ? fallbackId : id,
            Nbt.getLong(tag, "timestamp", 0),
            SnapshotType.parse(Nbt.getString(tag, "type")),
            Nbt.getString(tag, "detail"),
            Nbt.getString(tag, "createdBy"),
            Nbt.getCompound(tag, "playerData"),
            Nbt.getCompound(tag, "compatData"));
    }
}
