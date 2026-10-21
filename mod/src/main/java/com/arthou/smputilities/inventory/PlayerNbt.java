package com.arthou.smputilities.inventory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Reads and writes the item lists of a saved player ({@code Inventory} and
 * {@code EnderItems}) without constructing a player entity, so offline edits
 * leave every other saved value (position, dimension, effects...) untouched.
 */
public final class PlayerNbt {
    public static final int MAIN = 36;
    public static final int ARMOR = 4;
    public static final int ENDER = 27;

    /** Main 0-35, armor 36-39 (feet..head), offhand 40, like the vanilla inventory menu. */
    public record Contents(ItemStack[] main, ItemStack[] armor, ItemStack offhand) {
        public static Contents empty() {
            return new Contents(emptyArray(MAIN), emptyArray(ARMOR), ItemStack.EMPTY);
        }

        public ItemStack get(int index) {
            if (index < MAIN) {
                return main[index];
            }
            if (index < MAIN + ARMOR) {
                return armor[index - MAIN];
            }
            return index == 40 ? offhand : ItemStack.EMPTY;
        }
    }

    private PlayerNbt() {
    }

    public static Contents readInventory(CompoundTag player, HolderLookup.Provider registries) {
        Contents contents = Contents.empty();
        ItemStack offhand = ItemStack.EMPTY;
        ListTag list = player.getList("Inventory", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 255;
            ItemStack stack = ItemStack.parseOptional(registries, entry);
            if (slot < MAIN) {
                contents.main()[slot] = stack;
            } else if (slot >= 100 && slot < 100 + ARMOR) {
                contents.armor()[slot - 100] = stack;
            } else if (slot >= 150 && slot < 151) {
                offhand = stack;
            }
        }
        return new Contents(contents.main(), contents.armor(), offhand);
    }

    public static ListTag writeInventory(Contents contents, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < MAIN; i++) {
            add(list, contents.main()[i], i, registries);
        }
        for (int i = 0; i < ARMOR; i++) {
            add(list, contents.armor()[i], 100 + i, registries);
        }
        add(list, contents.offhand(), 150, registries);
        return list;
    }

    public static ItemStack[] readEnder(CompoundTag player, HolderLookup.Provider registries) {
        ItemStack[] stacks = emptyArray(ENDER);
        ListTag list = player.getList("EnderItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 255;
            if (slot < ENDER) {
                stacks[slot] = ItemStack.parseOptional(registries, entry);
            }
        }
        return stacks;
    }

    public static ListTag writeEnder(ItemStack[] stacks, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < stacks.length && i < ENDER; i++) {
            add(list, stacks[i], i, registries);
        }
        return list;
    }

    private static void add(ListTag list, ItemStack stack, int slot, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag entry = new CompoundTag();
        // Vanilla stores the slot as a byte; 150 (offhand) wraps to -106 exactly like Inventory#save.
        entry.putByte("Slot", (byte) slot);
        list.add(stack.save(registries, entry));
    }

    public static ItemStack[] emptyArray(int size) {
        ItemStack[] stacks = new ItemStack[size];
        java.util.Arrays.fill(stacks, ItemStack.EMPTY);
        return stacks;
    }

    public static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i] == null ? ItemStack.EMPTY : source[i].copy();
        }
        return result;
    }
}
