package com.arthou.smputilities.inventory;

import com.arthou.smputilities.mc.Nbt;
import com.arthou.smputilities.mc.Stacks;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Reads and writes the item parts of a saved player ({@code Inventory},
 * armor, offhand and {@code EnderItems}) without constructing a player
 * entity, so offline edits leave every other saved value untouched.
 *
 * <p>Reading accepts both layouts: armor and offhand inside {@code Inventory}
 * (slots 100-103 and 150, before 1.21.5) and in the {@code equipment}
 * compound (1.21.5 and later). Writing uses the layout of the running game.
 */
public final class PlayerNbt {
    public static final int MAIN = 36;
    public static final int ARMOR = 4;
    public static final int ENDER = 27;
    /** Equipment keys for armor slots 0-3 (feet to head), as saved from 1.21.5. */
    private static final String[] ARMOR_KEYS = {"feet", "legs", "chest", "head"};

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

    // -------------------------------------------------------------- live player

    /**
     * The live inventory. {@link net.minecraft.world.entity.player.Inventory}
     * indexes 0-40 the same way on every version (armor and offhand map to
     * equipment from 1.21.5).
     */
    public static Contents contents(Player player) {
        ItemStack[] main = new ItemStack[MAIN];
        for (int i = 0; i < MAIN; i++) {
            main[i] = player.getInventory().getItem(i).copy();
        }
        ItemStack[] armor = new ItemStack[ARMOR];
        for (int i = 0; i < ARMOR; i++) {
            armor[i] = player.getInventory().getItem(MAIN + i).copy();
        }
        return new Contents(main, armor, player.getInventory().getItem(40).copy());
    }

    public static void setContents(Player player, Contents contents) {
        for (int i = 0; i <= 40; i++) {
            player.getInventory().setItem(i, contents.get(i).copy());
        }
    }

    // --------------------------------------------------------------- saved data

    public static Contents readInventory(CompoundTag player, RegistryAccess registries) {
        Contents contents = Contents.empty();
        ItemStack offhand = ItemStack.EMPTY;
        for (CompoundTag entry : Nbt.compounds(Nbt.getList(player, "Inventory"))) {
            int slot = Nbt.getByte(entry, "Slot", (byte) 0) & 255;
            ItemStack stack = Stacks.load(entry, registries);
            if (slot < MAIN) {
                contents.main()[slot] = stack;
            } else if (slot >= 100 && slot < 100 + ARMOR) {
                contents.armor()[slot - 100] = stack;
            } else if (slot == 150) {
                offhand = stack;
            }
        }
        CompoundTag equipment = Nbt.getCompound(player, "equipment");
        for (int i = 0; i < ARMOR; i++) {
            if (Nbt.hasCompound(equipment, ARMOR_KEYS[i])) {
                contents.armor()[i] = Stacks.load(Nbt.getCompound(equipment, ARMOR_KEYS[i]), registries);
            }
        }
        if (Nbt.hasCompound(equipment, "offhand")) {
            offhand = Stacks.load(Nbt.getCompound(equipment, "offhand"), registries);
        }
        return new Contents(contents.main(), contents.armor(), offhand);
    }

    /** Puts inventory, armor and offhand into {@code player} in the running game's layout. */
    public static void writeInventory(CompoundTag player, Contents contents, RegistryAccess registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < MAIN; i++) {
            add(list, contents.main()[i], i, registries);
        }
        //#if MC>=1.21.5
        CompoundTag equipment = Nbt.getCompound(player, "equipment").copy();
        for (int i = 0; i < ARMOR; i++) {
            put(equipment, ARMOR_KEYS[i], contents.armor()[i], registries);
        }
        put(equipment, "offhand", contents.offhand(), registries);
        if (equipment.isEmpty()) {
            player.remove("equipment");
        } else {
            player.put("equipment", equipment);
        }
        //#else
        //$$ for (int i = 0; i < ARMOR; i++) {
        //$$     add(list, contents.armor()[i], 100 + i, registries);
        //$$ }
        //$$ add(list, contents.offhand(), 150, registries);
        //#endif
        player.put("Inventory", list);
    }

    public static ItemStack[] readEnder(CompoundTag player, RegistryAccess registries) {
        ItemStack[] stacks = emptyArray(ENDER);
        for (CompoundTag entry : Nbt.compounds(Nbt.getList(player, "EnderItems"))) {
            int slot = Nbt.getByte(entry, "Slot", (byte) 0) & 255;
            if (slot < ENDER) {
                stacks[slot] = Stacks.load(entry, registries);
            }
        }
        return stacks;
    }

    public static ListTag writeEnder(ItemStack[] stacks, RegistryAccess registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < stacks.length && i < ENDER; i++) {
            add(list, stacks[i], i, registries);
        }
        return list;
    }

    private static void add(ListTag list, ItemStack stack, int slot, RegistryAccess registries) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag entry = Stacks.save(stack, registries);
        // Vanilla stores the slot as a byte; 150 (offhand) wraps to -106 exactly like Inventory#save.
        entry.putByte("Slot", (byte) slot);
        list.add(entry);
    }

    private static void put(CompoundTag equipment, String key, ItemStack stack, RegistryAccess registries) {
        if (stack == null || stack.isEmpty()) {
            equipment.remove(key);
        } else {
            equipment.put(key, Stacks.save(stack, registries));
        }
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
