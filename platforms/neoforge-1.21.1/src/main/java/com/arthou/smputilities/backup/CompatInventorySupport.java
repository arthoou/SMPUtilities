package com.arthou.smputilities.backup;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
        captureCosmeticArmor(player.server, player.getUUID()).ifPresent(data -> tag.put("CosmeticArmor", data));
        captureCurios(player).ifPresent(data -> tag.put("Curios", data));
        return tag;
    }

    public static void apply(ServerPlayer player, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        applyCosmeticArmor(player.server, player.getUUID(), tag.getCompound("CosmeticArmor"));
        applyCurios(player, tag.getList("Curios", Tag.TAG_COMPOUND));
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    public static void applyCuriosOnly(ServerPlayer player, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        applyCurios(player, tag.getList("Curios", Tag.TAG_COMPOUND));
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    public static void applyCosmeticOnly(ServerPlayer player, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        applyCosmeticArmor(player.server, player.getUUID(), tag.getCompound("CosmeticArmor"));
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    public static CompatPreviewData preview(MinecraftServer server, CompoundTag tag) {
        ItemStack[] cosmetic = readCosmeticArmor(server, tag == null ? new CompoundTag() : tag.getCompound("CosmeticArmor"));
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
            Method serializeMethod = inventory.getClass().getMethod("serializeNBT", net.minecraft.core.HolderLookup.Provider.class);
            Object result = serializeMethod.invoke(inventory, server.registryAccess());
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
            Method deserializeMethod = inventory.getClass().getMethod("deserializeNBT", net.minecraft.core.HolderLookup.Provider.class, CompoundTag.class);
            deserializeMethod.invoke(inventory, server.registryAccess(), tag.copy());
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
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag itemTag = items.getCompound(i);
            int slot = itemTag.getInt("Slot");
            if (slot >= 0 && slot < stacks.length && itemTag.contains("id")) {
                ItemStack.parse(server.registryAccess(), itemTag).ifPresent(stack -> stacks[slot] = stack.copy());
            }
        }
        return stacks;
    }

    private static ItemStack[] readCurios(MinecraftServer server, CompoundTag tag) {
        if (tag == null || !tag.contains("Curios", Tag.TAG_LIST)) {
            return empty(0);
        }
        ListTag curios = tag.getList("Curios", Tag.TAG_COMPOUND);
        List<CurioEntry> entries = new ArrayList<>();
        for (int i = 0; i < curios.size(); i++) {
            CompoundTag entry = curios.getCompound(i);
            String identifier = entry.getString("Identifier");
            CompoundTag stacksTag = entry.getCompound("Stacks");
            ListTag items = stacksTag.getList("Items", Tag.TAG_COMPOUND);
            for (int j = 0; j < items.size(); j++) {
                CompoundTag itemTag = items.getCompound(j);
                int slot = itemTag.getInt("Slot");
                ItemStack stack = ItemStack.EMPTY;
                if (itemTag.contains("Stack", Tag.TAG_COMPOUND)) {
                    stack = ItemStack.parse(server.registryAccess(), itemTag.getCompound("Stack")).orElse(ItemStack.EMPTY);
                }
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

    private static ItemStack[] empty(int size) {
        ItemStack[] stacks = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            stacks[i] = ItemStack.EMPTY;
        }
        return stacks;
    }

    public static ItemStack namedBarrier(String name) {
        ItemStack stack = new ItemStack(Items.BARRIER);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    public record CompatPreviewData(ItemStack[] cosmeticArmor, ItemStack[] curios) {
    }

    private record CurioEntry(String identifier, int slot, ItemStack stack) {
    }
}
