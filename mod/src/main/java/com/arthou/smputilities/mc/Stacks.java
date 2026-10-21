package com.arthou.smputilities.mc;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
//#if MC>=1.20.5
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
//#else
//$$ import net.minecraft.nbt.ListTag;
//$$ import net.minecraft.nbt.NbtUtils;
//$$ import net.minecraft.nbt.StringTag;
//#endif

/** Item stacks to and from NBT, and the decorations chest menus use, on every version. */
public final class Stacks {
    private Stacks() {
    }

    /** The stack as a compound (item id, count, components or tag). Empty stacks give an empty compound. */
    public static CompoundTag save(ItemStack stack, RegistryAccess registries) {
        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }
        //#if MC>=1.20.5
        Tag tag = ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack).result().orElse(null);
        return tag instanceof CompoundTag compound ? compound : new CompoundTag();
        //#else
        //$$ return stack.save(new CompoundTag());
        //#endif
    }

    /** Reads a stack; unknown items or broken data give an empty stack. Extra keys (like "Slot") are ignored. */
    public static ItemStack load(CompoundTag tag, RegistryAccess registries) {
        if (tag == null || tag.isEmpty()) {
            return ItemStack.EMPTY;
        }
        //#if MC>=1.20.5
        return ItemStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result().orElse(ItemStack.EMPTY);
        //#else
        //$$ return ItemStack.of(tag);
        //#endif
    }

    /** Copy with a custom name (not italic) and gray lore lines. */
    public static ItemStack named(ItemStack stack, Component name, List<Component> lore) {
        ItemStack copy = stack.copy();
        //#if MC>=1.20.5
        copy.set(DataComponents.CUSTOM_NAME, name);
        if (!lore.isEmpty()) {
            copy.set(DataComponents.LORE, new ItemLore(lore));
        }
        //#else
        //$$ copy.setHoverName(name);
        //$$ if (!lore.isEmpty()) {
        //$$     ListTag lines = new ListTag();
        //$$     for (Component line : lore) {
        //$$         lines.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        //$$     }
        //$$     copy.getOrCreateTagElement("display").put("Lore", lines);
        //$$ }
        //#endif
        return copy;
    }

    /** A player head showing the account's skin. */
    public static ItemStack head(GameProfile profile) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        //#if MC>=1.21.9
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        //#elif MC>=1.20.5
        //$$ stack.set(DataComponents.PROFILE, new ResolvableProfile(profile));
        //#else
        //$$ stack.getOrCreateTag().put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(), profile));
        //#endif
        return stack;
    }

    /** Enchantment glint without enchantments, to mark the selected entry of a menu. */
    public static void glint(ItemStack stack) {
        //#if MC>=1.20.5
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        //#else
        //$$ ListTag enchantments = new ListTag();
        //$$ enchantments.add(new CompoundTag());
        //$$ stack.getOrCreateTag().put("Enchantments", enchantments);
        //$$ stack.getOrCreateTag().putInt("HideFlags", 1);
        //#endif
    }

    public static boolean same(ItemStack a, ItemStack b) {
        return ItemStack.matches(a, b);
    }
}
