package com.arthou.smputilities.backup;

import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.inventory.PlayerNbt;
import com.arthou.smputilities.inventory.PlayerState;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.util.ButtonMenu;
import com.arthou.smputilities.util.ConfirmMenu;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The backup browser from Inventory Rollback Plus: categories, a paged list
 * per category, a preview with restore buttons, and transfer to another
 * player. Every restore goes through a confirmation screen.
 */
public final class BackupMenus {
    public enum Mode { VANILLA, CURIOS, COSMETIC }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final SnapshotType[] TYPES = SnapshotType.values();

    private BackupMenus() {
    }

    public record Target(UUID id, String name) {
        GameProfile profile() {
            return new GameProfile(id, name);
        }
    }

    public static void openCategories(ServerPlayer viewer, Target target, Mode mode) {
        ButtonMenu.open(viewer, Component.literal(Lang.tr(viewer, "backup.title.categories", shown(viewer, target))),
            (id, inventory) -> new Categories(id, inventory, viewer, target, mode));
    }

    static String shown(ServerPlayer viewer, Target target) {
        return Names.seesRealNames(viewer) ? target.name() : Names.plainDisplay(target.id(), target.name());
    }

    static String time(long millis) {
        return TIME.format(Instant.ofEpochMilli(millis));
    }

    static String typeName(ServerPlayer viewer, SnapshotType type) {
        return Lang.tr(viewer, type.langKey());
    }

    // ------------------------------------------------------------ categories

    private static final class Categories extends ButtonMenu {
        private final Target target;
        private final Mode mode;

        Categories(int containerId, Inventory inventory, ServerPlayer viewer, Target target, Mode mode) {
            super(containerId, inventory, viewer, 2);
            this.target = target;
            this.mode = mode;
            List<PlayerSnapshot> snapshots = BackupService.list(target.id());
            set(4, head(target.profile(), shown(viewer, target), Lang.tr(viewer, "backup.total", snapshots.size())));
            for (int i = 0; i < TYPES.length && i < 9; i++) {
                SnapshotType type = TYPES[i];
                long count = snapshots.stream().filter(snapshot -> snapshot.type() == type).count();
                set(9 + i, named(type.icon(), typeName(viewer, type) + " (" + count + ")"));
            }
            fill(Items.BLACK_STAINED_GLASS_PANE);
        }

        @Override
        protected void onButton(int slot, ClickType clickType, int button) {
            int index = slot - 9;
            if (index >= 0 && index < TYPES.length) {
                SnapshotType type = TYPES[index];
                ButtonMenu.open(viewer, Component.literal(typeName(viewer, type) + " · " + shown(viewer, target)),
                    (id, inventory) -> new Entries(id, inventory, viewer, target, type, 0, mode));
            }
        }
    }

    // --------------------------------------------------------------- entries

    private static final class Entries extends ButtonMenu {
        private final Target target;
        private final SnapshotType type;
        private final int page;
        private final Mode mode;
        private final List<String> ids = new ArrayList<>();
        private final int pages;

        Entries(int containerId, Inventory inventory, ServerPlayer viewer, Target target, SnapshotType type, int page, Mode mode) {
            super(containerId, inventory, viewer, 6);
            this.target = target;
            this.type = type;
            this.page = page;
            this.mode = mode;
            List<PlayerSnapshot> matching = BackupService.list(target.id()).stream().filter(snapshot -> snapshot.type() == type).toList();
            this.pages = Math.max(1, (matching.size() + 44) / 45);
            for (int i = page * 45; i < Math.min(matching.size(), page * 45 + 45); i++) {
                PlayerSnapshot snapshot = matching.get(i);
                ids.add(snapshot.id());
                List<String> lore = new ArrayList<>();
                lore.add(Lang.tr(viewer, "backup.lore.id", snapshot.id()));
                if (!snapshot.detail().isEmpty()) {
                    lore.add(Lang.tr(viewer, "backup.lore.detail", snapshot.detail()));
                }
                if (!snapshot.createdBy().isEmpty()) {
                    lore.add(Lang.tr(viewer, "backup.lore.by", snapshot.createdBy()));
                }
                lore.add(Lang.tr(viewer, "backup.lore.click"));
                set(i - page * 45, named(Items.CHEST, "#" + (i + 1) + " · " + time(snapshot.timestamp()), lore.toArray(String[]::new)));
            }
            if (page > 0) {
                set(45, named(Items.ARROW, "← " + Lang.tr(viewer, "gui.previous")));
            }
            set(49, named(Items.BARRIER, Lang.tr(viewer, "gui.back"), Lang.tr(viewer, "gui.page", page + 1, pages)));
            if (page + 1 < pages) {
                set(53, named(Items.ARROW, Lang.tr(viewer, "gui.next") + " →"));
            }
        }

        @Override
        protected void onButton(int slot, ClickType clickType, int button) {
            if (slot < ids.size()) {
                String snapshotId = ids.get(slot);
                openDetail(viewer, target, snapshotId, false, type, page, mode);
            } else if (slot == 45 && page > 0) {
                ButtonMenu.open(viewer, Component.literal(typeName(viewer, type) + " · " + shown(viewer, target)),
                    (id, inventory) -> new Entries(id, inventory, viewer, target, type, page - 1, mode));
            } else if (slot == 53 && page + 1 < pages) {
                ButtonMenu.open(viewer, Component.literal(typeName(viewer, type) + " · " + shown(viewer, target)),
                    (id, inventory) -> new Entries(id, inventory, viewer, target, type, page + 1, mode));
            } else if (slot == 49) {
                openCategories(viewer, target, mode);
            }
        }
    }

    static void openDetail(ServerPlayer viewer, Target target, String snapshotId, boolean ender, SnapshotType type, int page, Mode mode) {
        Optional<PlayerSnapshot> snapshot = BackupService.store().find(target.id(), snapshotId);
        if (snapshot.isEmpty()) {
            Msg.error(viewer, "backup.not_found", snapshotId);
            viewer.closeContainer();
            return;
        }
        String title = Lang.tr(viewer, ender ? "backup.title.ender" : "backup.title.detail", snapshot.get().id(), shown(viewer, target));
        ButtonMenu.open(viewer, Component.literal(title),
            (id, inventory) -> new Detail(id, inventory, viewer, target, snapshot.get(), ender, type, page, mode));
    }

    // ---------------------------------------------------------------- detail

    private static final class Detail extends ButtonMenu {
        private final Target target;
        private final PlayerSnapshot snapshot;
        private final boolean ender;
        private final SnapshotType type;
        private final int page;
        private final Mode mode;

        Detail(int containerId, Inventory inventory, ServerPlayer viewer, Target target, PlayerSnapshot snapshot, boolean ender,
               SnapshotType type, int page, Mode mode) {
            super(containerId, inventory, viewer, 6);
            this.target = target;
            this.snapshot = snapshot;
            this.ender = ender;
            this.type = type;
            this.page = page;
            this.mode = mode;
            MinecraftServer server = viewer.server;
            CompoundTag data = BackupService.upgraded(server, snapshot.playerData());
            switch (mode) {
                case VANILLA -> {
                    if (ender) {
                        ItemStack[] stacks = PlayerNbt.readEnder(data, server.registryAccess());
                        for (int i = 0; i < stacks.length && i < 45; i++) {
                            set(i, stacks[i].copy());
                        }
                    } else {
                        PlayerNbt.Contents contents = PlayerNbt.readInventory(data, server.registryAccess());
                        for (int i = 0; i <= 40; i++) {
                            set(i, contents.get(i).copy());
                        }
                    }
                    set(45, named(Items.LIME_WOOL, Lang.tr(viewer, "backup.button.full"), Lang.tr(viewer, "backup.button.full_lore")));
                    set(46, named(Items.CHEST, Lang.tr(viewer, "backup.button.inventory")));
                    set(47, named(Items.ENDER_CHEST, Lang.tr(viewer, "backup.button.ender")));
                    set(48, named(Items.COMPASS, Lang.tr(viewer, "backup.button.teleport"),
                        BackupService.location(snapshot).map(location -> location.shortText()).orElse("-")));
                    set(49, named(ender ? Items.CHEST : Items.ENDER_CHEST, Lang.tr(viewer, ender ? "backup.button.show_inventory" : "backup.button.show_ender")));
                }
                case CURIOS, COSMETIC -> {
                    CompatInventorySupport.CompatPreviewData preview = CompatInventorySupport.preview(server, snapshot.compatData());
                    ItemStack[] stacks = mode == Mode.CURIOS ? preview.curios() : preview.cosmeticArmor();
                    for (int i = 0; i < stacks.length && i < 45; i++) {
                        set(i, stacks[i].copy());
                    }
                    set(45, named(Items.LIME_WOOL, Lang.tr(viewer, mode == Mode.CURIOS ? "backup.button.curios" : "backup.button.cosmetic")));
                }
            }
            set(50, named(Items.LAVA_BUCKET, Lang.tr(viewer, "backup.button.delete")));
            set(51, named(Items.HOPPER, Lang.tr(viewer, "backup.button.transfer")));
            set(52, named(Items.PAPER, "ID " + snapshot.id(), time(snapshot.timestamp()), typeName(viewer, snapshot.type()),
                snapshot.detail()));
            set(53, named(Items.BARRIER, Lang.tr(viewer, "gui.back")));
        }

        @Override
        protected void onButton(int slot, ClickType clickType, int button) {
            switch (slot) {
                case 45 -> {
                    if (mode == Mode.VANILLA) {
                        confirmRestore(BackupService.FULL, false, "backup.confirm.full");
                    } else {
                        confirmCompat();
                    }
                }
                case 46 -> {
                    if (mode == Mode.VANILLA) {
                        confirmRestore(BackupService.INVENTORY_ONLY, false, "backup.confirm.inventory");
                    }
                }
                case 47 -> {
                    if (mode == Mode.VANILLA) {
                        confirmRestore(BackupService.ENDER_ONLY, false, "backup.confirm.ender");
                    }
                }
                case 48 -> {
                    if (mode == Mode.VANILLA && Perms.has(viewer, Perm.STAFF_TELEPORT)) {
                        viewer.closeContainer();
                        BackupService.teleportTo(viewer, snapshot);
                    }
                }
                case 49 -> {
                    if (mode == Mode.VANILLA) {
                        openDetail(viewer, target, snapshot.id(), !ender, type, page, mode);
                    }
                }
                case 50 -> {
                    if (!Perms.has(viewer, Perm.STAFF_INVENTORY_RESTORE)) {
                        Msg.error(viewer, "error.no_permission");
                        return;
                    }
                    ConfirmMenu.open(viewer, named(Items.LAVA_BUCKET, "ID " + snapshot.id()), Lang.tr(viewer, "backup.confirm.delete"), () -> {
                        BackupService.store().delete(target.id(), snapshot.id());
                        SuServer.get().log(viewer, "backup.delete", target.id(), target.name(), snapshot.id());
                        Msg.ok(viewer, "backup.deleted", snapshot.id());
                        openCategories(viewer, target, mode);
                    }, () -> openDetail(viewer, target, snapshot.id(), ender, type, page, mode));
                }
                case 51 -> {
                    if (!Perms.has(viewer, Perm.STAFF_INVENTORY_RESTORE)) {
                        Msg.error(viewer, "error.no_permission");
                        return;
                    }
                    ButtonMenu.open(viewer, Component.literal(Lang.tr(viewer, "backup.title.transfer")),
                        (id, inventory) -> new TransferTarget(id, inventory, viewer, target, snapshot, () ->
                            openDetail(viewer, target, snapshot.id(), ender, type, page, mode)));
                }
                case 53 -> ButtonMenu.open(viewer, Component.literal(typeName(viewer, type) + " · " + shown(viewer, target)),
                    (id, inventory) -> new Entries(id, inventory, viewer, target, type, page, mode));
                default -> {
                }
            }
        }

        private void confirmRestore(Set<PlayerState.Part> parts, boolean compat, String questionKey) {
            if (!Perms.has(viewer, Perm.STAFF_INVENTORY_RESTORE)) {
                Msg.error(viewer, "error.no_permission");
                return;
            }
            Runnable back = () -> openDetail(viewer, target, snapshot.id(), ender, type, page, mode);
            ConfirmMenu.open(viewer, named(Items.CHEST, "ID " + snapshot.id(), time(snapshot.timestamp())),
                Lang.tr(viewer, questionKey, shown(viewer, target)), () -> {
                    boolean ok = BackupService.restore(viewer.server, viewer, target.id(), target.name(), snapshot, parts, compat || parts == BackupService.FULL);
                    if (ok) {
                        Msg.ok(viewer, "backup.restored", snapshot.id(), shown(viewer, target));
                    } else {
                        Msg.error(viewer, "backup.restore_failed");
                    }
                    viewer.closeContainer();
                }, back);
        }

        private void confirmCompat() {
            if (!Perms.has(viewer, Perm.STAFF_INVENTORY_RESTORE)) {
                Msg.error(viewer, "error.no_permission");
                return;
            }
            ServerPlayer online = viewer.server.getPlayerList().getPlayer(target.id());
            if (online == null) {
                Msg.error(viewer, "inventory.online_only", target.name());
                return;
            }
            ConfirmMenu.open(viewer, named(Items.CHEST, "ID " + snapshot.id()), Lang.tr(viewer, "backup.confirm.compat", shown(viewer, target)), () -> {
                if (SuServer.config().inventory.backupBeforeRestore) {
                    BackupService.snapshot(online, SnapshotType.PRE_RESTORE, "before restoring " + snapshot.id(), viewer.getGameProfile().getName());
                }
                if (mode == Mode.CURIOS) {
                    CompatInventorySupport.applyCuriosOnly(online, snapshot.compatData());
                } else {
                    CompatInventorySupport.applyCosmeticOnly(online, snapshot.compatData());
                }
                SuServer.get().log(viewer, "backup.restore_" + mode.name().toLowerCase(java.util.Locale.ROOT), target.id(), target.name(), snapshot.id());
                Msg.ok(viewer, "backup.restored", snapshot.id(), shown(viewer, target));
                viewer.closeContainer();
            }, () -> openDetail(viewer, target, snapshot.id(), ender, type, page, mode));
        }
    }

    // -------------------------------------------------------------- transfer

    private static final class TransferTarget extends ButtonMenu {
        private final Target source;
        private final PlayerSnapshot snapshot;
        private final Runnable back;
        private final List<UUID> players = new ArrayList<>();

        TransferTarget(int containerId, Inventory inventory, ServerPlayer viewer, Target source, PlayerSnapshot snapshot, Runnable back) {
            super(containerId, inventory, viewer, 6);
            this.source = source;
            this.snapshot = snapshot;
            this.back = back;
            for (ServerPlayer player : viewer.server.getPlayerList().getPlayers()) {
                if (players.size() >= 45) {
                    break;
                }
                players.add(player.getUUID());
                set(players.size() - 1, head(player.getGameProfile(), Names.seesRealNames(viewer) ? Names.realName(player) : Names.plainDisplay(player)));
            }
            set(49, named(Items.BARRIER, Lang.tr(viewer, "gui.back")));
        }

        @Override
        protected void onButton(int slot, ClickType clickType, int button) {
            if (slot == 49) {
                back.run();
                return;
            }
            if (slot >= players.size()) {
                return;
            }
            ServerPlayer destination = viewer.server.getPlayerList().getPlayer(players.get(slot));
            if (destination == null) {
                Msg.error(viewer, "player.not_found_online", "?");
                return;
            }
            ConfirmMenu.open(viewer, head(destination.getGameProfile(), Names.realName(destination)),
                Lang.tr(viewer, "backup.confirm.transfer", snapshot.id(), Names.realName(destination)), () -> {
                    BackupService.transfer(viewer, source.name(), snapshot, destination);
                    Msg.ok(viewer, "backup.transferred", snapshot.id(), Names.realName(destination));
                    viewer.closeContainer();
                }, back);
        }
    }
}
