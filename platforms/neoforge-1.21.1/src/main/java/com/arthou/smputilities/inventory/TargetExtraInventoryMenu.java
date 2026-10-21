package com.arthou.smputilities.inventory;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Curios or Cosmetic Armor slots of an online player, edited live through their mod APIs. */
public final class TargetExtraInventoryMenu extends AbstractContainerMenu {
    private final Runnable saveHook;

    public TargetExtraInventoryMenu(int containerId, Inventory playerInventory, List<InventoryViewCompat.SlotAccessor> accessors, Runnable saveHook, boolean mutable) {
        super(TargetInventoryMenu.menuTypeForSize(Math.max(9, Math.min(54, ((accessors.size() + 8) / 9) * 9))), containerId);
        InventoryViewCompat.AccessorContainer extraContainer = new InventoryViewCompat.AccessorContainer(accessors, this::broadcastChanges);
        this.saveHook = saveHook;
        int rows = Math.max(1, Math.min(6, (accessors.size() + 8) / 9));
        SimpleContainer filler = new SimpleContainer(rows * 9);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = col + row * 9;
                int x = 8 + col * 18;
                int y = 18 + row * 18;
                if (slotIndex < accessors.size()) {
                    addSlot(mutable ? new Slot(extraContainer, slotIndex, x, y) : new ReadOnlySlot(extraContainer, slotIndex, x, y));
                } else {
                    addSlot(new ReadOnlySlot(filler, slotIndex, x, y));
                }
            }
        }

        int offset = (rows - 4) * 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 103 + row * 18 + offset));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 161 + offset));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        saveHook.run();
    }
}
