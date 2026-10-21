package com.arthou.smputilities.util;

import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.server.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** "Are you sure?" step for destructive actions in chest GUIs. */
public final class ConfirmMenu extends ButtonMenu {
    private final Runnable onConfirm;
    private final Runnable onCancel;

    private ConfirmMenu(int containerId, Inventory inventory, ServerPlayer viewer, ItemStack subject, String question,
                        Runnable onConfirm, Runnable onCancel) {
        super(containerId, inventory, viewer, 3);
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        set(11, named(Items.LIME_CONCRETE, "✔ " + Lang.tr(viewer, "gui.confirm"), question));
        set(13, subject);
        set(15, named(Items.RED_CONCRETE, "✘ " + Lang.tr(viewer, "gui.cancel")));
        fill(Items.GRAY_STAINED_GLASS_PANE);
    }

    public static void open(ServerPlayer viewer, ItemStack subject, String question, Runnable onConfirm, Runnable onCancel) {
        ButtonMenu.open(viewer, Texts.literal(Lang.tr(viewer, "gui.confirm_title")),
            (id, inventory) -> new ConfirmMenu(id, inventory, viewer, subject, question, onConfirm, onCancel));
    }

    @Override
    protected void onButton(int slot, ClickType clickType, int button) {
        if (slot == 11) {
            onConfirm.run();
        } else if (slot == 15) {
            if (onCancel != null) {
                onCancel.run();
            } else {
                viewer.closeContainer();
            }
        }
    }
}
