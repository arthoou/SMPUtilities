package com.arthou.smputilities.backup;

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
        String id = tag.getString("id");
        return new PlayerSnapshot(
            id.isEmpty() ? fallbackId : id,
            tag.getLong("timestamp"),
            SnapshotType.parse(tag.getString("type")),
            tag.getString("detail"),
            tag.getString("createdBy"),
            tag.getCompound("playerData"),
            tag.getCompound("compatData"));
    }
}
