package com.arthou.smputilities.backup;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Why a snapshot was taken. Names match Inventory Rollback Plus so its data imports as-is. */
public enum SnapshotType {
    JOIN(Items.OAK_SAPLING),
    QUIT(Items.GRAY_DYE),
    DEATH(Items.BONE),
    DIMENSION_CHANGE(Items.FLINT),
    MANUAL(Items.DIAMOND),
    PERIODIC(Items.CLOCK),
    PRE_RESTORE(Items.SHIELD),
    STAFF_EDIT(Items.WRITABLE_BOOK),
    PROFILE_SWITCH(Items.NAME_TAG);

    private final Item icon;

    SnapshotType(Item icon) {
        this.icon = icon;
    }

    public Item icon() {
        return icon;
    }

    public String langKey() {
        return "backup.type." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static SnapshotType parse(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return MANUAL;
        }
    }
}
