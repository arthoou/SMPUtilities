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
        // Inventory indexes 0-40 as main, armor (feet..head) and offhand on every version.
        return slot >= 0 && slot <= 40 ? target.getInventory().getItem(slot) : ItemStack.EMPTY;
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
        if (slot >= 0 && slot <= 40) {
            target.getInventory().setItem(slot, stack);
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
        for (int i = 0; i <= 40; i++) {
            target.getInventory().setItem(i, ItemStack.EMPTY);
        }
        setChanged();
    }
}
