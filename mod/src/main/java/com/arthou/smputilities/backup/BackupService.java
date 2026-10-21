package com.arthou.smputilities.backup;

import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.teleport.Location;
import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.inventory.OfflinePlayerAccess;
import com.arthou.smputilities.inventory.PlayerState;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.teleport.Teleports;
import com.arthou.smputilities.inventory.TargetInventoryMenu;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Nbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Inventory backups (Inventory Rollback Plus, integrated): automatic snapshots
 * on join, quit, death, dimension change and on a timer, manual snapshots,
 * and restores that always take a backup of the current state first.
 */
public final class BackupService {
    private static BackupStore store;
    private static long lastPeriodic;

    private BackupService() {
    }

    public static void onServerStart(SuServer context) {
        store = new BackupStore(context.dataDir().resolve("backups"));
        lastPeriodic = System.currentTimeMillis();
    }

    public static void onServerStop() {
        if (store != null) {
            store.close();
            store = null;
        }
    }

    public static BackupStore store() {
        return store;
    }

    private static boolean enabled() {
        return store != null && SuServer.config().features.inventory;
    }

    // ------------------------------------------------------------ capture

    /**
     * Takes a snapshot of everything a restore can bring back (inventory, ender
     * chest, XP, health, hunger, effects and position) instead of the whole
     * player save: snapshots stay small and nothing else (recipe book,
     * attributes, other mods' data) is kept around.
     */
    public static PlayerSnapshot snapshot(ServerPlayer player, SnapshotType type, String detail, String createdBy) {
        if (store == null) {
            return null;
        }
        CompoundTag data = PlayerState.capture(player, PlayerState.ALL);
        CompoundTag compat = CompatInventorySupport.capture(player);
        PlayerSnapshot snapshot = new PlayerSnapshot(store.newId(player.getUUID()), System.currentTimeMillis(), type, detail, createdBy, data, compat);
        store.add(player.getUUID(), snapshot, limitFor(type));
        return snapshot;
    }

    /** Snapshot of an offline player's saved file. */
    public static PlayerSnapshot snapshotOffline(MinecraftServer server, UUID id, String name, SnapshotType type, String detail) {
        if (store == null) {
            return null;
        }
        Optional<CompoundTag> data = OfflinePlayerAccess.load(server, id);
        if (data.isEmpty()) {
            return null;
        }
        Nbt.addCurrentDataVersion(data.get());
        PlayerSnapshot snapshot = new PlayerSnapshot(store.newId(id), System.currentTimeMillis(), type, detail, "", data.get(), new CompoundTag());
        store.add(id, snapshot, limitFor(type));
        return snapshot;
    }

    public static int limitFor(SnapshotType type) {
        SuConfig.Inventory config = SuServer.config().inventory;
        return switch (type) {
            case DEATH -> config.maxDeathBackups;
            case MANUAL, PRE_RESTORE, STAFF_EDIT -> config.maxManualBackups;
            default -> config.maxBackupsPerType;
        };
    }

    // ------------------------------------------------------------ events

    public static void onJoin(ServerPlayer player) {
        if (enabled() && SuServer.config().inventory.backupOnJoin) {
            snapshot(player, SnapshotType.JOIN, "join", "");
        }
    }

    public static void onQuit(ServerPlayer player) {
        if (enabled() && SuServer.config().inventory.backupOnQuit) {
            snapshot(player, SnapshotType.QUIT, "quit", "");
        }
    }

    public static void onDeath(ServerPlayer player, String cause) {
        if (enabled() && SuServer.config().inventory.backupOnDeath) {
            snapshot(player, SnapshotType.DEATH, cause, "");
        }
    }

    public static void onDimensionChange(ServerPlayer player, String to) {
        if (enabled() && SuServer.config().inventory.backupOnDimensionChange) {
            snapshot(player, SnapshotType.DIMENSION_CHANGE, to, "");
        }
    }

    /** Called once per second. */
    public static void tick(MinecraftServer server) {
        int minutes = SuServer.config().inventory.periodicBackupMinutes;
        if (!enabled() || minutes <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPeriodic < minutes * 60_000L) {
            return;
        }
        lastPeriodic = now;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            snapshot(player, SnapshotType.PERIODIC, "periodic", "");
        }
    }

    // ------------------------------------------------------------ restore

    /** Parts restored by the "full" button: everything but the location. */
    public static final Set<PlayerState.Part> FULL = PlayerState.ALL_BUT_LOCATION;
    public static final Set<PlayerState.Part> INVENTORY_ONLY = EnumSet.of(PlayerState.Part.INVENTORY);
    public static final Set<PlayerState.Part> ENDER_ONLY = EnumSet.of(PlayerState.Part.ENDER_CHEST);

    /**
     * Restores {@code parts} of a snapshot onto its owner, online or offline,
     * after saving the current state as a PRE_RESTORE backup.
     */
    public static boolean restore(MinecraftServer server, ServerPlayer actor, UUID targetId, String targetName, PlayerSnapshot snapshot,
                                  Set<PlayerState.Part> parts, boolean includeCompat) {
        String actorName = actor == null ? "Console" : Mc.name(actor);
        CompoundTag data = upgraded(server, snapshot.playerData());
        ServerPlayer online = server.getPlayerList().getPlayer(targetId);
        if (online == null && TargetInventoryMenu.isOfflineEditOpen(targetId)) {
            // A staff member has the offline inventory open; their save would undo this restore.
            return false;
        }
        if (SuServer.config().inventory.backupBeforeRestore) {
            if (online != null) {
                snapshot(online, SnapshotType.PRE_RESTORE, "before restoring " + snapshot.id(), actorName);
            } else {
                snapshotOffline(server, targetId, targetName, SnapshotType.PRE_RESTORE, "before restoring " + snapshot.id());
            }
        }
        boolean ok;
        if (online != null) {
            PlayerState.apply(online, data, parts);
            if (includeCompat) {
                CompatInventorySupport.apply(online, snapshot.compatData());
            }
            Msg.info(online, "backup.restored_you");
            ok = true;
        } else {
            ok = OfflinePlayerAccess.edit(server, targetId, saved -> {
                PlayerState.applyOffline(saved, data, parts, server.registryAccess());
                return saved;
            });
        }
        if (ok && SuServer.get() != null) {
            SuServer.get().log(actor, "backup.restore", targetId, targetName, snapshot.id() + " " + parts + (online == null ? " offline" : ""));
        }
        return ok;
    }

    /** Copies a snapshot onto another online player (Inventory Rollback Plus "Transfer To Player"). */
    public static void transfer(ServerPlayer actor, String sourceName, PlayerSnapshot snapshot, ServerPlayer destination) {
        String actorName = actor == null ? "Console" : Mc.name(actor);
        if (SuServer.config().inventory.backupBeforeRestore) {
            snapshot(destination, SnapshotType.PRE_RESTORE, "before transfer of " + snapshot.id(), actorName);
        }
        PlayerState.apply(destination, upgraded(Mc.server(destination), snapshot.playerData()),
            EnumSet.of(PlayerState.Part.INVENTORY, PlayerState.Part.ENDER_CHEST));
        CompatInventorySupport.apply(destination, snapshot.compatData());
        Msg.info(destination, "backup.transferred_you");
        if (SuServer.get() != null) {
            SuServer.get().log(actor, "backup.transfer", destination.getUUID(), Mc.name(destination),
                "from " + sourceName + " " + snapshot.id());
        }
    }

    /** Where the player stood when the snapshot was taken (own snapshots and full player saves). */
    public static Optional<Location> location(PlayerSnapshot snapshot) {
        CompoundTag data = snapshot.playerData();
        if (Nbt.hasCompound(data, "SuLocation")) {
            CompoundTag loc = Nbt.getCompound(data, "SuLocation");
            return Optional.of(new Location(Nbt.getString(loc, "dimension", "minecraft:overworld"), Nbt.getDouble(loc, "x", 0),
                Nbt.getDouble(loc, "y", 0), Nbt.getDouble(loc, "z", 0), Nbt.getFloat(loc, "yaw", 0), Nbt.getFloat(loc, "pitch", 0)));
        }
        if (!Nbt.hasList(data, "Pos")) {
            return Optional.empty();
        }
        ListTag pos = Nbt.getList(data, "Pos");
        ListTag rotation = Nbt.getList(data, "Rotation");
        String dimension = Nbt.getString(data, "Dimension", "minecraft:overworld");
        return Optional.of(new Location(dimension, Nbt.listDouble(pos, 0, 0), Nbt.listDouble(pos, 1, 0), Nbt.listDouble(pos, 2, 0),
            Nbt.listFloat(rotation, 0, 0), Nbt.listFloat(rotation, 1, 0)));
    }

    public static boolean teleportTo(ServerPlayer staff, PlayerSnapshot snapshot) {
        return location(snapshot).map(location -> Teleports.teleport(staff, location, true)).orElse(false);
    }

    /** A copy of snapshot data, upgraded by the game's data fixers when it was taken on an older version. */
    public static CompoundTag upgraded(MinecraftServer server, CompoundTag data) {
        CompoundTag copy = data.copy();
        int version = Nbt.dataVersion(copy, -1);
        if (version < 0 || version >= Mc.dataVersion()) {
            return copy;
        }
        try {
            return Mc.upgradePlayer(server, copy, version);
        } catch (RuntimeException e) {
            SuLog.warn("Could not upgrade backup data from version " + version, e);
            return copy;
        }
    }

    public static List<PlayerSnapshot> list(UUID player) {
        return store == null ? List.of() : store.list(player);
    }
}
