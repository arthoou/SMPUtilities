package com.arthou.smputilities.inventory;

import com.arthou.smputilities.backup.BackupService;
import com.arthou.smputilities.backup.SnapshotType;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Staff view of another player's inventory or ender chest, built on the
 * vanilla chest menus so it works without the client mod.
 *
 * <p>Online targets are edited live. Offline targets are edited in a copy that
 * is written to their save file when the menu closes; if the target logs in
 * first, the copy is handed to the live player instead, so items can never be
 * duplicated or lost between the two.
 */
public final class TargetInventoryMenu extends AbstractContainerMenu {
    /** Open offline sessions by target, so a login can take them over. */
    private static final Map<UUID, List<TargetInventoryMenu>> OFFLINE_SESSIONS = new ConcurrentHashMap<>();

    private final MinecraftServer server;
    private final UUID targetId;
    private final String targetName;
    private final Container targetContainer;
    private final boolean mutable;
    private final boolean enderChest;
    private final boolean offline;
    private final ItemStack[] original;
    private boolean finished;

    private TargetInventoryMenu(int containerId, Inventory viewerInventory, MenuType<?> menuType, MinecraftServer server, UUID targetId,
                                String targetName, Container targetContainer, boolean mutable, boolean enderChest, boolean offline) {
        super(menuType, containerId);
        this.server = server;
        this.targetId = targetId;
        this.targetName = targetName;
        this.targetContainer = targetContainer;
        this.mutable = mutable;
        this.enderChest = enderChest;
        this.offline = offline;
        this.original = snapshot(targetContainer);
        int rows = targetContainer.getContainerSize() / 9;

        int offset = (rows - 4) * 18;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = col + row * 9;
                int x = 8 + col * 18;
                int y = 18 + row * 18;
                boolean filler = !enderChest && slotIndex > 40;
                addSlot(mutable && !filler ? new Slot(targetContainer, slotIndex, x, y) : new ReadOnlySlot(targetContainer, slotIndex, x, y));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(viewerInventory, col + row * 9 + 9, 8 + col * 18, 103 + row * 18 + offset));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(viewerInventory, col, 8 + col * 18, 161 + offset));
        }
        if (offline && mutable) {
            OFFLINE_SESSIONS.computeIfAbsent(targetId, ignored -> new ArrayList<>()).add(this);
        }
    }

    public static TargetInventoryMenu live(int containerId, Inventory viewerInventory, ServerPlayer target, boolean mutable) {
        return new TargetInventoryMenu(containerId, viewerInventory, MenuType.GENERIC_9x5, target.server, target.getUUID(),
            target.getGameProfile().getName(), new LivePlayerInventoryContainer(target), mutable, false, false);
    }

    public static TargetInventoryMenu liveEnderChest(int containerId, Inventory viewerInventory, ServerPlayer target, boolean mutable) {
        Container ender = target.getEnderChestInventory();
        return new TargetInventoryMenu(containerId, viewerInventory, menuTypeForSize(ender.getContainerSize()), target.server, target.getUUID(),
            target.getGameProfile().getName(), ender, mutable, true, false);
    }

    public static TargetInventoryMenu offline(int containerId, Inventory viewerInventory, MinecraftServer server, UUID id, String name,
                                              PlayerNbt.Contents contents, boolean mutable) {
        SimpleContainer container = new SimpleContainer(45);
        for (int i = 0; i <= 40; i++) {
            container.setItem(i, contents.get(i).copy());
        }
        return new TargetInventoryMenu(containerId, viewerInventory, MenuType.GENERIC_9x5, server, id, name, container, mutable, false, true);
    }

    public static TargetInventoryMenu offlineEnderChest(int containerId, Inventory viewerInventory, MinecraftServer server, UUID id, String name,
                                                        ItemStack[] ender, boolean mutable) {
        SimpleContainer container = new SimpleContainer(PlayerNbt.ENDER);
        for (int i = 0; i < ender.length && i < PlayerNbt.ENDER; i++) {
            container.setItem(i, ender[i].copy());
        }
        return new TargetInventoryMenu(containerId, viewerInventory, MenuType.GENERIC_9x3, server, id, name, container, mutable, true, true);
    }

    public static MenuType<?> menuTypeForSize(int size) {
        return switch (size) {
            case 9 -> MenuType.GENERIC_9x1;
            case 18 -> MenuType.GENERIC_9x2;
            case 27 -> MenuType.GENERIC_9x3;
            case 36 -> MenuType.GENERIC_9x4;
            case 45 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (finished) {
            return false;
        }
        // A live view closes when its target leaves, so nothing is put into a player that was already saved.
        return offline || server.getPlayerList().getPlayer(targetId) != null;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!offline || !mutable || finished) {
            return;
        }
        finished = true;
        List<TargetInventoryMenu> sessions = OFFLINE_SESSIONS.get(targetId);
        if (sessions != null) {
            sessions.remove(this);
            if (sessions.isEmpty()) {
                OFFLINE_SESSIONS.remove(targetId);
            }
        }
        if (!changed()) {
            return;
        }
        ServerPlayer live = server.getPlayerList().getPlayer(targetId);
        if (live != null) {
            applyTo(live);
            return;
        }
        BackupService.snapshotOffline(server, targetId, targetName, SnapshotType.STAFF_EDIT,
            player instanceof ServerPlayer staff ? "edit by " + staff.getGameProfile().getName() : "staff edit");
        OfflinePlayerAccess.edit(server, targetId, tag -> {
            write(tag);
            return tag;
        });
        if (player instanceof ServerPlayer staff && SuServer.get() != null) {
            SuServer.get().log(staff, enderChest ? "inventory.edit_ender_offline" : "inventory.edit_offline", targetId, targetName, "");
        }
    }

    /** The target logged in while an offline copy was open: give them the edited copy and close the view. */
    public static void onTargetLogin(ServerPlayer target) {
        List<TargetInventoryMenu> sessions = OFFLINE_SESSIONS.remove(target.getUUID());
        if (sessions == null) {
            return;
        }
        for (TargetInventoryMenu menu : new ArrayList<>(sessions)) {
            if (menu.finished) {
                continue;
            }
            menu.finished = true;
            if (menu.changed()) {
                menu.applyTo(target);
            }
        }
        for (ServerPlayer staff : target.server.getPlayerList().getPlayers()) {
            if (staff.containerMenu instanceof TargetInventoryMenu menu && sessions.contains(menu)) {
                staff.closeContainer();
            }
        }
    }

    private void applyTo(ServerPlayer live) {
        if (enderChest) {
            for (int i = 0; i < live.getEnderChestInventory().getContainerSize() && i < targetContainer.getContainerSize(); i++) {
                live.getEnderChestInventory().setItem(i, targetContainer.getItem(i).copy());
            }
        } else {
            for (int i = 0; i < 36; i++) {
                live.getInventory().items.set(i, targetContainer.getItem(i).copy());
            }
            for (int i = 0; i < 4; i++) {
                live.getInventory().armor.set(i, targetContainer.getItem(36 + i).copy());
            }
            live.getInventory().offhand.set(0, targetContainer.getItem(40).copy());
        }
        live.getInventory().setChanged();
        live.inventoryMenu.broadcastChanges();
        live.containerMenu.broadcastChanges();
    }

    private void write(CompoundTag tag) {
        if (enderChest) {
            ItemStack[] ender = new ItemStack[PlayerNbt.ENDER];
            for (int i = 0; i < PlayerNbt.ENDER; i++) {
                ender[i] = targetContainer.getItem(i).copy();
            }
            tag.put("EnderItems", PlayerNbt.writeEnder(ender, server.registryAccess()));
        } else {
            PlayerNbt.Contents contents = PlayerNbt.Contents.empty();
            for (int i = 0; i < 36; i++) {
                contents.main()[i] = targetContainer.getItem(i).copy();
            }
            for (int i = 0; i < 4; i++) {
                contents.armor()[i] = targetContainer.getItem(36 + i).copy();
            }
            tag.put("Inventory", PlayerNbt.writeInventory(new PlayerNbt.Contents(contents.main(), contents.armor(),
                targetContainer.getItem(40).copy()), server.registryAccess()));
        }
    }

    private boolean changed() {
        for (int i = 0; i < original.length; i++) {
            if (!ItemStack.matches(original[i], targetContainer.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack[] snapshot(Container container) {
        ItemStack[] stacks = new ItemStack[container.getContainerSize()];
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = container.getItem(i).copy();
        }
        return stacks;
    }
}
