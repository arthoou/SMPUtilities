package com.arthou.smputilities.core.portal;

import com.arthou.smputilities.core.util.Json;
import com.arthou.smputilities.core.util.SuLog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Registered portals ({@code <world>/smputilities/portals.json}). Limits live
 * in the config; this class only counts and stores portals.
 */
public final class PortalRegistry {
    public static final int UNLIMITED = -1;

    private static final class FileData {
        List<PortalRecord> portals = new ArrayList<>();
        int nextId = 1;
    }

    private final Path file;
    private FileData data = new FileData();

    public PortalRegistry(Path file) {
        this.file = file;
    }

    public synchronized void load() {
        data = Json.read(file, FileData.class, new FileData());
        if (data.portals == null) {
            data.portals = new ArrayList<>();
        }
    }

    public synchronized void save() {
        try {
            Json.write(file, data);
        } catch (IOException e) {
            SuLog.error("Could not save portals", e);
        }
    }

    public static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    public static int limitFor(Map<String, Integer> limits, String key) {
        Integer limit = limits.get(normalizeKey(key));
        return limit == null ? UNLIMITED : limit;
    }

    public synchronized int count(String key) {
        String normalized = normalizeKey(key);
        int count = 0;
        for (PortalRecord portal : data.portals) {
            if (portal.key.equals(normalized)) {
                count++;
            }
        }
        return count;
    }

    public synchronized boolean canCreate(Map<String, Integer> limits, String key) {
        int limit = limitFor(limits, key);
        return limit < 0 || count(key) < limit;
    }

    public synchronized PortalRecord register(String key, String dimension, int x, int y, int z, String block, UUID creator, String creatorName, long now) {
        PortalRecord record = new PortalRecord();
        record.id = String.valueOf(data.nextId++);
        record.key = normalizeKey(key);
        record.dimension = dimension;
        record.x = x;
        record.y = y;
        record.z = z;
        record.block = block;
        record.creator = creator;
        record.creatorName = creatorName == null ? "" : creatorName;
        record.createdAt = now;
        data.portals.add(record);
        save();
        return record;
    }

    public synchronized Optional<PortalRecord> near(String dimension, int x, int y, int z, int radius) {
        for (PortalRecord portal : data.portals) {
            if (portal.isNear(dimension, x, y, z, radius)) {
                return Optional.of(portal);
            }
        }
        return Optional.empty();
    }

    public synchronized boolean remove(String id) {
        boolean removed = data.portals.removeIf(portal -> portal.id.equals(id));
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized int clear(String key) {
        String normalized = normalizeKey(key);
        int before = data.portals.size();
        data.portals.removeIf(portal -> portal.key.equals(normalized));
        int removed = before - data.portals.size();
        if (removed > 0) {
            save();
        }
        return removed;
    }

    public synchronized List<PortalRecord> list(String key) {
        String normalized = key == null ? null : normalizeKey(key);
        List<PortalRecord> result = new ArrayList<>();
        for (PortalRecord portal : data.portals) {
            if (normalized == null || portal.key.equals(normalized)) {
                result.add(portal);
            }
        }
        return result;
    }
}
