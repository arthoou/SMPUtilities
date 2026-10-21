package com.arthou.smputilities.core.player;

import com.arthou.smputilities.core.util.Json;
import com.arthou.smputilities.core.util.SuLog;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazily loaded, cached {@link PlayerRecord}s. Changes are marked dirty and
 * flushed periodically, on logout and on server stop.
 */
public final class PlayerStore {
    private final Path directory;
    private final Map<UUID, PlayerRecord> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    /** Lower-case last known name -> UUID, for offline lookups. */
    private final Map<String, UUID> names = new ConcurrentHashMap<>();

    public PlayerStore(Path directory) {
        this.directory = directory;
    }

    /** Loads every record once, so offline players can be searched by name or nickname. */
    public void loadAll() {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                try {
                    UUID id = UUID.fromString(name.substring(0, name.length() - 5));
                    get(id);
                } catch (IllegalArgumentException ignored) {
                    // not a player file
                }
            }
        } catch (IOException e) {
            SuLog.warn("Could not list player records", e);
        }
    }

    public PlayerRecord get(UUID id) {
        return cache.computeIfAbsent(id, this::read);
    }

    public Optional<PlayerRecord> find(UUID id) {
        PlayerRecord record = cache.get(id);
        if (record != null) {
            return Optional.of(record);
        }
        return Files.exists(file(id)) ? Optional.of(get(id)) : Optional.empty();
    }

    public Optional<UUID> idByName(String name) {
        return Optional.ofNullable(names.get(name.toLowerCase(Locale.ROOT)));
    }

    public Collection<PlayerRecord> all() {
        return cache.values();
    }

    public void markDirty(UUID id) {
        dirty.add(id);
    }

    public void update(UUID id, java.util.function.Consumer<PlayerRecord> change) {
        PlayerRecord record = get(id);
        synchronized (record) {
            change.accept(record);
        }
        if (record.lastName != null && !record.lastName.isEmpty()) {
            names.put(record.lastName.toLowerCase(Locale.ROOT), id);
        }
        markDirty(id);
    }

    public void flush() {
        List<UUID> pending = new ArrayList<>(dirty);
        for (UUID id : pending) {
            dirty.remove(id);
            PlayerRecord record = cache.get(id);
            if (record == null) {
                continue;
            }
            try {
                synchronized (record) {
                    Json.write(file(id), record);
                }
            } catch (IOException e) {
                dirty.add(id);
                SuLog.error("Could not save player record " + id, e);
            }
        }
    }

    public void flush(UUID id) {
        if (dirty.remove(id)) {
            PlayerRecord record = cache.get(id);
            if (record != null) {
                try {
                    synchronized (record) {
                        Json.write(file(id), record);
                    }
                } catch (IOException e) {
                    dirty.add(id);
                    SuLog.error("Could not save player record " + id, e);
                }
            }
        }
    }

    /** Players whose nickname, stripped and lower-cased, equals {@code key}. */
    public Set<UUID> withNicknameKey(String key, java.util.function.Function<String, String> normalizer) {
        Set<UUID> result = new HashSet<>();
        for (PlayerRecord record : cache.values()) {
            if (record.hasNickname() && normalizer.apply(record.nickname).equals(key)) {
                result.add(record.uuid);
            }
        }
        return result;
    }

    private PlayerRecord read(UUID id) {
        PlayerRecord fresh = new PlayerRecord();
        fresh.uuid = id;
        PlayerRecord record = Json.read(file(id), PlayerRecord.class, fresh);
        record.uuid = id;
        if (record.lastName != null && !record.lastName.isEmpty()) {
            names.put(record.lastName.toLowerCase(Locale.ROOT), id);
        }
        return record;
    }

    private Path file(UUID id) {
        return directory.resolve(id + ".json");
    }
}
