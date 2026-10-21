package com.arthou.smputilities.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InventoryViewCompat {
    public static final int VANILLA_X = 44;
    public static final int EXTRA_LEFT_X = 8;
    public static final int LEFT_BARRIER_X = 26;
    public static final int RIGHT_BARRIER_X = 214;
    public static final int EXTRA_RIGHT_X = 236;
    public static final int EXTRA_RIGHT_SECOND_X = 254;
    public static final int TOP_Y = 18;

    private InventoryViewCompat() {
    }

    public static CompatState capture(ServerPlayer target) {
        List<SlotAccessor> left = new ArrayList<>();
        List<SlotAccessor> right = new ArrayList<>();
        Runnable saveHook = () -> {
        };

        CosmeticState cosmetic = captureCosmeticArmor(target.getUUID());
        if (!cosmetic.slots().isEmpty()) {
            left.addAll(cosmetic.slots());
            saveHook = cosmetic.saveHook();
        }

        right.addAll(captureCurios(target));
        return new CompatState(left, right, saveHook);
    }

    private static CosmeticState captureCosmeticArmor(UUID playerId) {
        try {
            Class<?> apiClass = Class.forName("lain.mods.cos.api.CosArmorAPI");
            Object inventory = apiClass.getMethod("getCAStacks", UUID.class).invoke(null, playerId);
            if (inventory == null) {
                return CosmeticState.EMPTY;
            }

            Method getStackInSlot = inventory.getClass().getMethod("getStackInSlot", int.class);
            Method setStackInSlot = inventory.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);
            List<SlotAccessor> slots = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                int slot = i;
                slots.add(new SlotAccessor(
                    "Cosmetic Armor " + (i + 1),
                    () -> invokeStack(getStackInSlot, inventory, slot),
                    stack -> invokeVoid(setStackInSlot, inventory, slot, stack.copy())
                ));
            }

            Class<?> modObjectsClass = Class.forName("lain.mods.cos.impl.ModObjects");
            Field invManField = modObjectsClass.getDeclaredField("invMan");
            invManField.setAccessible(true);
            Object manager = invManField.get(null);
            Method saveMethod = null;
            for (Method method : manager.getClass().getDeclaredMethods()) {
                if (method.getName().equals("saveInventory") && method.getParameterCount() == 2) {
                    method.setAccessible(true);
                    saveMethod = method;
                    break;
                }
            }
            Method finalSaveMethod = saveMethod;
            Runnable saveHook = finalSaveMethod == null ? () -> {
            } : () -> invokeVoid(finalSaveMethod, manager, playerId, inventory);
            return new CosmeticState(slots, saveHook);
        } catch (ReflectiveOperationException ignored) {
            return CosmeticState.EMPTY;
        }
    }

    private static List<SlotAccessor> captureCurios(ServerPlayer target) {
        List<SlotAccessor> slots = new ArrayList<>();
        try {
            Class<?> curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            @SuppressWarnings("unchecked")
            Optional<Object> optionalHandler = (Optional<Object>) curiosApiClass
                .getMethod("getCuriosInventory", LivingEntity.class)
                .invoke(null, target);
            if (optionalHandler.isEmpty()) {
                return slots;
            }

            Object curiosHandler = optionalHandler.get();
            @SuppressWarnings("unchecked")
            Map<String, Object> curios = (Map<String, Object>) curiosHandler.getClass().getMethod("getCurios").invoke(curiosHandler);
            List<Map.Entry<String, Object>> ordered = curios.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .toList();

            for (Map.Entry<String, Object> entry : ordered) {
                Object stackHandler = entry.getValue().getClass().getMethod("getStacks").invoke(entry.getValue());
                Method getSlots = stackHandler.getClass().getMethod("getSlots");
                Method getStackInSlot = stackHandler.getClass().getMethod("getStackInSlot", int.class);
                Method setStackInSlot = stackHandler.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);
                int size = (int) getSlots.invoke(stackHandler);
                for (int i = 0; i < size; i++) {
                    int slot = i;
                    String label = entry.getKey() + " #" + (i + 1);
                    slots.add(new SlotAccessor(
                        label,
                        () -> invokeStack(getStackInSlot, stackHandler, slot),
                        stack -> invokeVoid(setStackInSlot, stackHandler, slot, stack.copy())
                    ));
                }
            }
        } catch (ReflectiveOperationException ignored) {
            return List.of();
        }
        return slots;
    }

    private static ItemStack invokeStack(Method method, Object target, Object... args) {
        try {
            Object result = method.invoke(target, args);
            return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static void invokeVoid(Method method, Object target, Object... args) {
        try {
            method.invoke(target, args);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    public record CompatState(List<SlotAccessor> leftSlots, List<SlotAccessor> rightSlots, Runnable saveHook) {
    }

    public record CosmeticState(List<SlotAccessor> slots, Runnable saveHook) {
        private static final CosmeticState EMPTY = new CosmeticState(List.of(), () -> {
        });
    }

    public record SlotAccessor(String label, StackGetter getter, StackSetter setter) {
        public ItemStack get() {
            ItemStack stack = getter.get();
            return stack == null ? ItemStack.EMPTY : stack.copy();
        }

        public void set(ItemStack stack) {
            setter.set(stack == null ? ItemStack.EMPTY : stack.copy());
        }
    }

    @FunctionalInterface
    public interface StackGetter {
        ItemStack get();
    }

    @FunctionalInterface
    public interface StackSetter {
        void set(ItemStack stack);
    }

    public static final class AccessorContainer implements Container {
        private final List<SlotAccessor> accessors;
        private final List<ItemStack> cache;
        private final Runnable onChanged;

        public AccessorContainer(List<SlotAccessor> accessors, Runnable onChanged) {
            this.accessors = accessors;
            this.onChanged = onChanged;
            this.cache = new ArrayList<>(accessors.size());
            for (SlotAccessor accessor : accessors) {
                this.cache.add(accessor.get());
            }
        }

        @Override
        public int getContainerSize() {
            return accessors.size();
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack stack : cache) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < cache.size() ? cache.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (slot < 0 || slot >= cache.size()) {
                return ItemStack.EMPTY;
            }
            ItemStack current = cache.get(slot);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack result = current.split(amount);
            apply(slot, current);
            return result;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            if (slot < 0 || slot >= cache.size()) {
                return ItemStack.EMPTY;
            }
            ItemStack current = cache.get(slot);
            cache.set(slot, ItemStack.EMPTY);
            accessors.get(slot).set(ItemStack.EMPTY);
            onChanged.run();
            return current;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot < 0 || slot >= cache.size()) {
                return;
            }
            apply(slot, stack);
        }

        @Override
        public void setChanged() {
            onChanged.run();
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < cache.size(); i++) {
                apply(i, ItemStack.EMPTY);
            }
        }

        private void apply(int slot, ItemStack stack) {
            ItemStack copy = stack == null ? ItemStack.EMPTY : stack.copy();
            cache.set(slot, copy);
            accessors.get(slot).set(copy);
            onChanged.run();
        }
    }

    public static ItemStack namedBarrier(Component name) {
        ItemStack stack = new ItemStack(net.minecraft.world.item.Items.BARRIER);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
        return stack;
    }
}
