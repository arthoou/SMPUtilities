package com.arthou.smputilities.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The target's real inventory seen through a 45-slot container: 0-35 main,
 * 36-39 armor, 40 offhand. Edits go straight into the live player, so there
 * is never a second copy that could duplicate items.
 */
public final class LivePlayerInventoryContainer implements Container {
    private final Player target;

    public LivePlayerInventoryContainer(Player target) {
        this.target = target;
    }

    @Override
    public int getContainerSize() {
        return 45;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 36) {
            return target.getInventory().items.get(slot);
        }
        if (slot < 40) {
            return target.getInventory().armor.get(slot - 36);
        }
        if (slot == 40) {
            return target.getInventory().offhand.get(0);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = getItem(slot);
        ItemStack result = stack.split(amount);
        setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = getItem(slot);
        setItem(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 36) {
            target.getInventory().items.set(slot, stack);
        } else if (slot < 40) {
            target.getInventory().armor.set(slot - 36, stack);
        } else if (slot == 40) {
            target.getInventory().offhand.set(0, stack);
        }
        setChanged();
    }

    @Override
    public void setChanged() {
        target.getInventory().setChanged();
        if (target instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
            serverPlayer.containerMenu.broadcastChanges();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return !target.isRemoved();
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < 36; i++) {
            target.getInventory().items.set(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < 4; i++) {
            target.getInventory().armor.set(i, ItemStack.EMPTY);
        }
        target.getInventory().offhand.set(0, ItemStack.EMPTY);
        setChanged();
    }
}
