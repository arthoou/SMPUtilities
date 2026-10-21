package com.arthou.smputilities.inventory;

import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Opens inventory views for staff (command and panel share these). */
public final class InventoryViews {
    private InventoryViews() {
    }

    private static boolean canEdit(ServerPlayer viewer, boolean online) {
        return Perms.has(viewer, Perm.STAFF_INVENTORY_EDIT) && (online || SuServer.config().inventory.allowOfflineEdit);
    }

    public static boolean openInventory(ServerPlayer viewer, Names.Target target) {
        MinecraftServer server = viewer.server;
        ServerPlayer online = server.getPlayerList().getPlayer(target.id());
        boolean edit = canEdit(viewer, online != null);
        Component title = Component.literal(Lang.tr(viewer, edit ? "inventory.title_edit" : "inventory.title", display(viewer, target)));
        if (online != null) {
            viewer.openMenu(new SimpleMenuProvider((id, inventory, player) -> TargetInventoryMenu.live(id, inventory, online, edit), title));
        } else {
            Optional<CompoundTag> data = OfflinePlayerAccess.load(server, target.id());
            if (data.isEmpty()) {
                Msg.error(viewer, "inventory.no_data", target.name());
                return false;
            }
            PlayerNbt.Contents contents = PlayerNbt.readInventory(data.get(), server.registryAccess());
            viewer.openMenu(new SimpleMenuProvider((id, inventory, player) ->
                TargetInventoryMenu.offline(id, inventory, server, target.id(), target.name(), contents, edit), title));
        }
        SuServer.get().log(viewer, "inventory.view", target.id(), target.name(), online == null ? "offline" : "online");
        return true;
    }

    public static boolean openEnderChest(ServerPlayer viewer, Names.Target target) {
        MinecraftServer server = viewer.server;
        ServerPlayer online = server.getPlayerList().getPlayer(target.id());
        boolean edit = canEdit(viewer, online != null);
        Component title = Component.literal(Lang.tr(viewer, edit ? "inventory.ender_title_edit" : "inventory.ender_title", display(viewer, target)));
        if (online != null) {
            viewer.openMenu(new SimpleMenuProvider((id, inventory, player) -> TargetInventoryMenu.liveEnderChest(id, inventory, online, edit), title));
        } else {
            Optional<CompoundTag> data = OfflinePlayerAccess.load(server, target.id());
            if (data.isEmpty()) {
                Msg.error(viewer, "inventory.no_data", target.name());
                return false;
            }
            ItemStack[] ender = PlayerNbt.readEnder(data.get(), server.registryAccess());
            viewer.openMenu(new SimpleMenuProvider((id, inventory, player) ->
                TargetInventoryMenu.offlineEnderChest(id, inventory, server, target.id(), target.name(), ender, edit), title));
        }
        SuServer.get().log(viewer, "inventory.view_ender", target.id(), target.name(), online == null ? "offline" : "online");
        return true;
    }

    public static boolean openCurios(ServerPlayer viewer, ServerPlayer target) {
        InventoryViewCompat.CompatState compat = InventoryViewCompat.capture(target);
        if (compat.rightSlots().isEmpty()) {
            Msg.error(viewer, "inventory.no_curios");
            return false;
        }
        boolean edit = canEdit(viewer, true);
        viewer.openMenu(new SimpleMenuProvider((id, inventory, player) ->
            new TargetExtraInventoryMenu(id, inventory, compat.rightSlots(), compat.saveHook(), edit),
            Component.literal(Lang.tr(viewer, "inventory.curios_title", Names.realName(target)))));
        return true;
    }

    public static boolean openCosmetic(ServerPlayer viewer, ServerPlayer target) {
        InventoryViewCompat.CompatState compat = InventoryViewCompat.capture(target);
        if (compat.leftSlots().isEmpty()) {
            Msg.error(viewer, "inventory.no_cosmetic");
            return false;
        }
        boolean edit = canEdit(viewer, true);
        viewer.openMenu(new SimpleMenuProvider((id, inventory, player) ->
            new TargetExtraInventoryMenu(id, inventory, compat.leftSlots(), compat.saveHook(), edit),
            Component.literal(Lang.tr(viewer, "inventory.cosmetic_title", Names.realName(target)))));
        return true;
    }

    private static String display(ServerPlayer viewer, Names.Target target) {
        return Names.seesRealNames(viewer) ? target.name() : Names.plainDisplay(target.id(), target.name());
    }
}
