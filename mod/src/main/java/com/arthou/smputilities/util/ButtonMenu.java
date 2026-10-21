package com.arthou.smputilities.util;

import com.arthou.smputilities.mc.Texts;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * A chest GUI made of locked "button" items, built on vanilla menus so it
 * works for players without the client mod. Clicks in the top part never move
 * items; they are routed to {@link #onButton}.
 */
public abstract class ButtonMenu extends AbstractContainerMenu {
    public interface Factory {
        ButtonMenu create(int containerId, Inventory inventory);
    }

    protected final ServerPlayer viewer;
    protected final SimpleContainer buttons;
    private final int size;

    protected ButtonMenu(int containerId, Inventory viewerInventory, ServerPlayer viewer, int rows) {
        super(menuType(rows), containerId);
        this.viewer = viewer;
        this.size = rows * 9;
        this.buttons = new SimpleContainer(size);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new LockedSlot(buttons, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }
        int offset = (rows - 4) * 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(viewerInventory, col + row * 9 + 9, 8 + col * 18, 103 + row * 18 + offset));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(viewerInventory, col, 8 + col * 18, 161 + offset));
        }
    }

    public static void open(ServerPlayer player, Component title, Factory factory) {
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> factory.create(id, inventory), title));
    }

    protected abstract void onButton(int slot, ClickType clickType, int button);

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < size) {
            if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE) {
                onButton(slotId, clickType, button);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    protected void set(int slot, ItemStack stack) {
        buttons.setItem(slot, stack);
    }

    protected void fill(Item item) {
        for (int i = 0; i < size; i++) {
            if (buttons.getItem(i).isEmpty()) {
                buttons.setItem(i, named(item, " "));
            }
        }
    }

    public static ItemStack named(Item item, String name, String... lore) {
        return named(new ItemStack(item), Texts.literal(name).withStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.WHITE)), lore);
    }

    public static ItemStack named(ItemStack stack, Component name, String... lore) {
        ItemStack copy = stack.copy();
        copy.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(Style.EMPTY.withItalic(false)));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                if (line == null || line.isEmpty()) {
                    continue;
                }
                MutableComponent text = Texts.literal(line).withStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.GRAY));
                lines.add(text);
            }
            copy.set(DataComponents.LORE, new ItemLore(lines));
        }
        return copy;
    }

    public static ItemStack head(GameProfile profile, String name, String... lore) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.PROFILE, new ResolvableProfile(profile));
        return named(stack, Texts.literal(name).withStyle(ChatFormatting.YELLOW), lore);
    }

    public static MenuType<?> menuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    private static final class LockedSlot extends Slot {
        private LockedSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
