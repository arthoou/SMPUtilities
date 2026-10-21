package com.arthou.smputilities.backup;

import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Nbt;
import com.arthou.smputilities.mc.Stacks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CompatInventorySupport {
    public static final int VANILLA_X = 44;
    public static final int EXTRA_LEFT_X = 8;
    public static final int LEFT_BARRIER_X = 26;
    public static final int RIGHT_BARRIER_X = 214;
    public static final int EXTRA_RIGHT_X = 236;
    public static final int EXTRA_RIGHT_SECOND_X = 254;
    public static final int TOP_Y = 18;

    private CompatInventorySupport() {
    }

    public static CompoundTag capture(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        captureCosmeticArmor(Mc.server(player), player.getUUID()).ifPresent(data -> tag.put("CosmeticArmor", data));
        captureCurios(player).ifPresent(data -> tag.put("Curios", data));
        return tag;
    }

    public static void apply(ServerPlayer player, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        applyCosmeticArmor(Mc.server(player), player.getUUID(), Nbt.getCompound(tag, "CosmeticArmor"));
        applyCurios(player, Nbt.getList(tag, "Curios"));
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    public static void applyCuriosOnly(ServerPlayer player, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        applyCurios(player, Nbt.getList(tag, "Curios"));
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    public static void applyCosmeticOnly(ServerPlayer player, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        applyCosmeticArmor(Mc.server(player), player.getUUID(), Nbt.getCompound(tag, "CosmeticArmor"));
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    public static CompatPreviewData preview(MinecraftServer server, CompoundTag tag) {
        ItemStack[] cosmetic = readCosmeticArmor(server, tag == null ? new CompoundTag() : Nbt.getCompound(tag, "CosmeticArmor"));
        ItemStack[] curios = readCurios(server, tag == null ? new CompoundTag() : tag);
        return new CompatPreviewData(cosmetic, curios);
    }

    private static Optional<CompoundTag> captureCosmeticArmor(MinecraftServer server, UUID playerId) {
        try {
            Class<?> apiClass = Class.forName("lain.mods.cos.api.CosArmorAPI");
            Object inventory = apiClass.getMethod("getCAStacks", UUID.class).invoke(null, playerId);
            if (inventory == null) {
                return Optional.empty();
            }
            // serializeNBT(HolderLookup.Provider) from 1.20.5, serializeNBT() before.
            Method serializeMethod = method(inventory.getClass(), "serializeNBT");
            Object result = serializeMethod.getParameterCount() == 1
                ? serializeMethod.invoke(inventory, server.registryAccess()) : serializeMethod.invoke(inventory);
            return result instanceof CompoundTag compound ? Optional.of(compound.copy()) : Optional.empty();
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private static void applyCosmeticArmor(MinecraftServer server, UUID playerId, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("lain.mods.cos.api.CosArmorAPI");
            Object inventory = apiClass.getMethod("getCAStacks", UUID.class).invoke(null, playerId);
            if (inventory == null) {
                return;
            }
            Method deserializeMethod = method(inventory.getClass(), "deserializeNBT");
            if (deserializeMethod.getParameterCount() == 2) {
                deserializeMethod.invoke(inventory, server.registryAccess(), tag.copy());
            } else {
                deserializeMethod.invoke(inventory, tag.copy());
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Optional<ListTag> captureCurios(ServerPlayer player) {
        try {
            Class<?> curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            @SuppressWarnings("unchecked")
            Optional<Object> optionalHandler = (Optional<Object>) curiosApiClass
                .getMethod("getCuriosInventory", LivingEntity.class)
                .invoke(null, player);
            if (optionalHandler.isEmpty()) {
                return Optional.empty();
            }
            Object handler = optionalHandler.get();
            Object result = handler.getClass().getMethod("saveInventory", boolean.class).invoke(handler, false);
            return result instanceof ListTag list ? Optional.of(list.copy()) : Optional.empty();
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private static void applyCurios(ServerPlayer player, ListTag data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        try {
            Class<?> curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            @SuppressWarnings("unchecked")
            Optional<Object> optionalHandler = (Optional<Object>) curiosApiClass
                .getMethod("getCuriosInventory", LivingEntity.class)
                .invoke(null, player);
            if (optionalHandler.isEmpty()) {
                return;
            }
            Object handler = optionalHandler.get();
            handler.getClass().getMethod("loadInventory", ListTag.class).invoke(handler, data.copy());
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static ItemStack[] readCosmeticArmor(MinecraftServer server, CompoundTag tag) {
        ItemStack[] stacks = empty(4);
        if (tag == null || tag.isEmpty()) {
            return stacks;
        }
        for (CompoundTag itemTag : Nbt.compounds(Nbt.getList(tag, "Items"))) {
            int slot = Nbt.getInt(itemTag, "Slot", -1);
            if (slot >= 0 && slot < stacks.length && itemTag.contains("id")) {
                stacks[slot] = Stacks.load(itemTag, server.registryAccess());
            }
        }
        return stacks;
    }

    private static ItemStack[] readCurios(MinecraftServer server, CompoundTag tag) {
        if (tag == null || !Nbt.hasList(tag, "Curios")) {
            return empty(0);
        }
        List<CurioEntry> entries = new ArrayList<>();
        for (CompoundTag entry : Nbt.compounds(Nbt.getList(tag, "Curios"))) {
            String identifier = Nbt.getString(entry, "Identifier");
            for (CompoundTag itemTag : Nbt.compounds(Nbt.getList(Nbt.getCompound(entry, "Stacks"), "Items"))) {
                int slot = Nbt.getInt(itemTag, "Slot", 0);
                ItemStack stack = Nbt.hasCompound(itemTag, "Stack") ? Stacks.load(Nbt.getCompound(itemTag, "Stack"), server.registryAccess()) : ItemStack.EMPTY;
                entries.add(new CurioEntry(identifier, slot, stack));
            }
        }
        entries.sort(Comparator.comparing(CurioEntry::identifier).thenComparingInt(CurioEntry::slot));
        ItemStack[] stacks = empty(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            stacks[i] = entries.get(i).stack().copy();
        }
        return stacks;
    }

    /** The public method called {@code name}, whatever its parameters (they differ between versions). */
    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static ItemStack[] empty(int size) {
        ItemStack[] stacks = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            stacks[i] = ItemStack.EMPTY;
        }
        return stacks;
    }

    public static ItemStack namedBarrier(String name) {
        return Stacks.named(new ItemStack(Items.BARRIER), Texts.literal(name), List.of());
    }

    public record CompatPreviewData(ItemStack[] cosmeticArmor, ItemStack[] curios) {
    }

    private record CurioEntry(String identifier, int slot, ItemStack stack) {
    }
}
