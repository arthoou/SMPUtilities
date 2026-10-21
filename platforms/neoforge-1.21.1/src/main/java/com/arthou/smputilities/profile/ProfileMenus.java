package com.arthou.smputilities.profile;

import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.inventory.PlayerNbt;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.util.ButtonMenu;
import com.arthou.smputilities.util.ConfirmMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Chest GUIs for profiles: the profile list, Inv Profile's Quick Change screen and a read-only preview. */
public final class ProfileMenus {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private ProfileMenus() {
    }

    public static void openList(ServerPlayer viewer, UUID owner, String ownerName) {
        String title = viewer.getUUID().equals(owner) ? Lang.tr(viewer, "profile.title.own") : Lang.tr(viewer, "profile.title.other", ownerName);
        ButtonMenu.open(viewer, Component.literal(title), (id, inventory) -> new ListMenu(id, inventory, viewer, owner, ownerName));
    }

    /** The original Inv Profile screen: preview of the stored slot and the Quick Change button. */
    public static void openQuickChange(ServerPlayer viewer) {
        ButtonMenu.open(viewer, Component.literal(Lang.tr(viewer, "invprofile.title")), (id, inventory) -> new QuickChangeMenu(id, inventory, viewer));
    }

    private static final class ListMenu extends ButtonMenu {
        private final UUID owner;
        private final String ownerName;
        private final List<String> names;
        private final boolean self;

        ListMenu(int containerId, Inventory inventory, ServerPlayer viewer, UUID owner, String ownerName) {
            super(containerId, inventory, viewer, 4);
            this.owner = owner;
            this.ownerName = ownerName;
            this.self = viewer.getUUID().equals(owner);
            ProfileStore.PlayerProfiles profiles = ProfileService.of(owner);
            this.names = profiles.names();
            for (int i = 0; i < names.size() && i < 27; i++) {
                String name = names.get(i);
                ProfileStore.Profile profile = profiles.profiles.get(name);
                boolean active = profiles.active.equals(name);
                String updated = profile == null || profile.updated == 0 ? "-" : TIME.format(Instant.ofEpochMilli(profile.updated));
                ItemStack icon = named(active ? Items.NAME_TAG : Items.PAPER, (active ? "▶ " : "") + name,
                    Lang.tr(viewer, active ? "profile.lore.active" : "profile.lore.inactive"),
                    Lang.tr(viewer, "profile.lore.updated", updated),
                    profile != null && !profile.nick.isEmpty() ? Lang.tr(viewer, "profile.lore.nick", profile.nick) : "",
                    Lang.tr(viewer, self ? "profile.lore.click_self" : "profile.lore.click_staff"));
                if (active) {
                    icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                }
                set(i, icon);
            }
            set(27, named(Items.BOOK, Lang.tr(viewer, "profile.info"), Lang.tr(viewer, "profile.info_lore")));
            if (self) {
                set(31, named(Items.LIME_WOOL, Lang.tr(viewer, "invprofile.button.quick_change"), Lang.tr(viewer, "profile.quick_lore")));
            }
            set(35, named(Items.BARRIER, Lang.tr(viewer, "gui.close")));
        }

        @Override
        protected void onButton(int slot, ClickType clickType, int button) {
            if (slot == 35) {
                viewer.closeContainer();
                return;
            }
            if (slot == 31 && self) {
                openQuickChange(viewer);
                return;
            }
            if (slot >= names.size() || slot >= 27) {
                return;
            }
            String name = names.get(slot);
            if (clickType == ClickType.QUICK_MOVE) {
                // Shift-click: delete (with confirmation).
                if (!self && !Perms.has(viewer, Perm.STAFF_PROFILES)) {
                    return;
                }
                ConfirmMenu.open(viewer, named(Items.PAPER, name), Lang.tr(viewer, "profile.confirm.delete", name), () -> {
                    String error = ProfileService.delete(owner, ownerName, name, viewer);
                    if (error == null) {
                        Msg.ok(viewer, "profile.deleted", name);
                    } else {
                        Msg.error(viewer, error, name);
                    }
                    openList(viewer, owner, ownerName);
                }, () -> openList(viewer, owner, ownerName));
                return;
            }
            if (button == 1 || !self) {
                // Right-click (or staff): preview the stored contents.
                ButtonMenu.open(viewer, Component.literal(Lang.tr(viewer, "profile.title.preview", name)),
                    (id, inventory) -> new PreviewMenu(id, inventory, viewer, owner, ownerName, name, false));
                return;
            }
            String error = ProfileService.switchTo(viewer, name, null, false);
            viewer.closeContainer();
            if (error == null) {
                Msg.ok(viewer, "profile.switched", name);
            } else {
                Msg.error(viewer, error, name);
            }
        }
    }

    private static final class PreviewMenu extends ButtonMenu {
        private final UUID owner;
        private final String ownerName;
        private final String name;
        private final boolean ender;

        PreviewMenu(int containerId, Inventory inventory, ServerPlayer viewer, UUID owner, String ownerName, String name, boolean ender) {
            super(containerId, inventory, viewer, 6);
            this.owner = owner;
            this.ownerName = ownerName;
            this.name = name;
            this.ender = ender;
            ProfileStore.PlayerProfiles profiles = ProfileService.of(owner);
            ProfileStore.Profile profile = profiles.profiles.get(name);
            boolean active = profiles.active.equals(name);
            if (active) {
                set(22, named(Items.NAME_TAG, Lang.tr(viewer, "profile.preview_active")));
            } else if (profile != null) {
                CompoundTag data = profile.data;
                if (ender) {
                    ItemStack[] stacks = PlayerNbt.readEnder(data, viewer.server.registryAccess());
                    for (int i = 0; i < stacks.length; i++) {
                        set(i, stacks[i].copy());
                    }
                } else {
                    PlayerNbt.Contents contents = PlayerNbt.readInventory(data, viewer.server.registryAccess());
                    for (int i = 0; i <= 40; i++) {
                        set(i, contents.get(i).copy());
                    }
                }
                set(47, named(Items.EXPERIENCE_BOTTLE, Lang.tr(viewer, "profile.preview_xp", data.getInt("XpLevel"))));
                set(48, named(Items.GOLDEN_APPLE, Lang.tr(viewer, "profile.preview_health",
                    String.format(java.util.Locale.ROOT, "%.1f", data.contains("Health") ? data.getFloat("Health") : 20.0F),
                    data.contains("foodLevel") ? data.getInt("foodLevel") : 20)));
            }
            set(49, named(ender ? Items.CHEST : Items.ENDER_CHEST, Lang.tr(viewer, ender ? "backup.button.show_inventory" : "backup.button.show_ender")));
            set(53, named(Items.BARRIER, Lang.tr(viewer, "gui.back")));
        }

        @Override
        protected void onButton(int slot, ClickType clickType, int button) {
            if (slot == 49) {
                ButtonMenu.open(viewer, Component.literal(Lang.tr(viewer, "profile.title.preview", name)),
                    (id, inventory) -> new PreviewMenu(id, inventory, viewer, owner, ownerName, name, !ender));
            } else if (slot == 53) {
                openList(viewer, owner, ownerName);
            }
        }
    }

    /** Port of Inv Profile's InventoryProfileMenu: same layout (slots 0-40 preview, 49 Quick Change). */
    private static final class QuickChangeMenu extends ButtonMenu {
        private static final int QUICK_CHANGE_SLOT = 49;

        QuickChangeMenu(int containerId, Inventory inventory, ServerPlayer viewer) {
            super(containerId, inventory, viewer, 6);
            refresh();
        }

        private void refresh() {
            buttons.clearContent();
            CompoundTag quick = ProfileService.of(viewer.getUUID()).quick;
            if (quick.contains("Inventory")) {
                PlayerNbt.Contents contents = PlayerNbt.readInventory(quick, viewer.server.registryAccess());
                for (int i = 0; i <= 40; i++) {
                    set(i, contents.get(i).copy());
                }
            }
            set(QUICK_CHANGE_SLOT, named(new ItemStack(Items.LIME_WOOL),
                Component.literal(Lang.tr(viewer, "invprofile.button.quick_change")).withStyle(ChatFormatting.GOLD)));
        }

        @Override
        protected void onButton(int slot, ClickType clickType, int button) {
            if (slot != QUICK_CHANGE_SLOT) {
                return;
            }
            String error = ProfileService.quickChange(viewer, getCarried());
            if (error != null) {
                viewer.sendSystemMessage(Component.literal(Lang.tr(viewer, error)).withStyle(ChatFormatting.RED));
                return;
            }
            viewer.sendSystemMessage(Component.literal(Lang.tr(viewer, "invprofile.quick_change_done")).withStyle(ChatFormatting.GREEN));
            refresh();
            broadcastChanges();
        }
    }

    public static String shownName(ServerPlayer viewer, UUID id, String name) {
        return Names.seesRealNames(viewer) ? name : Names.plainDisplay(id, name);
    }
}
