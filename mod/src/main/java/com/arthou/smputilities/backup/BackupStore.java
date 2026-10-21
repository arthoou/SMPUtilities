package com.arthou.smputilities.backup;

import com.arthou.smputilities.mc.Nbt;
import com.arthou.smputilities.core.util.AtomicFiles;
import com.arthou.smputilities.core.util.SuLog;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Snapshots per player in {@code <world>/smputilities/backups/<uuid>.dat}.
 * Lists are kept newest first; disk writes run on a background thread so
 * the tick is not blocked by large saves.
 */
public final class BackupStore {
    private final Path directory;
    private final Map<UUID, List<PlayerSnapshot>> cache = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SMPutilities-backups");
        thread.setDaemon(true);
        return thread;
    });

    public BackupStore(Path directory) {
        this.directory = directory;
    }

    public List<PlayerSnapshot> list(UUID player) {
        return Collections.unmodifiableList(snapshots(player));
    }

    public Optional<PlayerSnapshot> find(UUID player, String idOrIndex) {
        List<PlayerSnapshot> snapshots = snapshots(player);
        for (PlayerSnapshot snapshot : snapshots) {
            if (snapshot.id().equalsIgnoreCase(idOrIndex)) {
                return Optional.of(snapshot);
            }
        }
        try {
            int index = Integer.parseInt(idOrIndex);
            if (index >= 1 && index <= snapshots.size()) {
                return Optional.of(snapshots.get(index - 1));
            }
        } catch (NumberFormatException ignored) {
            // not an index
        }
        return Optional.empty();
    }

    /** Adds a snapshot and trims old ones of the same type. */
    public synchronized void add(UUID player, PlayerSnapshot snapshot, int limitForType) {
        List<PlayerSnapshot> snapshots = new ArrayList<>(snapshots(player));
        snapshots.add(0, snapshot);
        int count = 0;
        for (int i = 0; i < snapshots.size(); i++) {
            if (snapshots.get(i).type() == snapshot.type() && ++count > limitForType) {
                snapshots.remove(i--);
            }
        }
        cache.put(player, snapshots);
        persist(player, snapshots);
    }

    /** Imports snapshots from Inventory Rollback Plus, skipping duplicates by timestamp. */
    public synchronized int importLegacy(UUID player, List<PlayerSnapshot> imported) {
        List<PlayerSnapshot> snapshots = new ArrayList<>(snapshots(player));
        int added = 0;
        for (PlayerSnapshot snapshot : imported) {
            boolean exists = snapshots.stream().anyMatch(existing -> existing.timestamp() == snapshot.timestamp() && existing.type() == snapshot.type());
            if (!exists) {
                snapshots.add(snapshot);
                added++;
            }
        }
        snapshots.sort(Comparator.comparingLong(PlayerSnapshot::timestamp).reversed());
        cache.put(player, snapshots);
        persist(player, snapshots);
        return added;
    }

    public synchronized boolean delete(UUID player, String id) {
        List<PlayerSnapshot> snapshots = new ArrayList<>(snapshots(player));
        boolean removed = snapshots.removeIf(snapshot -> snapshot.id().equals(id));
        if (removed) {
            cache.put(player, snapshots);
            persist(player, snapshots);
        }
        return removed;
    }

    public String newId(UUID player) {
        String alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
        List<PlayerSnapshot> snapshots = snapshots(player);
        while (true) {
            StringBuilder builder = new StringBuilder(5);
            for (int i = 0; i < 5; i++) {
                builder.append(alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length())));
            }
            String id = builder.toString();
            if (snapshots.stream().noneMatch(snapshot -> snapshot.id().equals(id))) {
                return id;
            }
        }
    }

    public void close() {
        io.shutdown();
        try {
            io.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<PlayerSnapshot> snapshots(UUID player) {
        return cache.computeIfAbsent(player, this::read);
    }

    private List<PlayerSnapshot> read(UUID player) {
        Path file = file(player);
        List<PlayerSnapshot> result = new ArrayList<>();
        if (!Files.exists(file)) {
            return result;
        }
        try {
            CompoundTag root = Nbt.readCompressed(file);
            int index = 0;
            for (CompoundTag entry : Nbt.compounds(Nbt.getList(root, "snapshots"))) {
                result.add(PlayerSnapshot.load(entry, "legacy" + index++));
            }
        } catch (IOException | RuntimeException e) {
            SuLog.error("Could not read backups of " + player, e);
        }
        result.sort(Comparator.comparingLong(PlayerSnapshot::timestamp).reversed());
        return result;
    }

    private void persist(UUID player, List<PlayerSnapshot> snapshots) {
        List<PlayerSnapshot> copy = List.copyOf(snapshots);
        io.execute(() -> {
            CompoundTag root = new CompoundTag();
            ListTag list = new ListTag();
            for (PlayerSnapshot snapshot : copy) {
                list.add(snapshot.save());
            }
            root.put("snapshots", list);
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                Nbt.writeCompressed(root, bytes);
                AtomicFiles.writeBytes(file(player), bytes.toByteArray());
            } catch (IOException | RuntimeException e) {
                SuLog.error("Could not save backups of " + player, e);
            }
        });
    }

    private Path file(UUID player) {
        return directory.resolve(player + ".dat");
    }
}
