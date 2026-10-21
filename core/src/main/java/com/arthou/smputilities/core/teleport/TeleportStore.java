package com.arthou.smputilities.core.teleport;

import com.arthou.smputilities.core.util.Json;
import com.arthou.smputilities.core.util.SuLog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/** Server-wide spawn and warps ({@code <world>/smputilities/teleport.json}). */
public final class TeleportStore {
    private static final class FileData {
        Location spawn;
        Map<String, Location> warps = new TreeMap<>();
        boolean importedTpa;
    }

    private final Path file;
    private FileData data = new FileData();

    public TeleportStore(Path file) {
        this.file = file;
    }

    public synchronized void load() {
        data = Json.read(file, FileData.class, new FileData());
        if (data.warps == null) {
            data.warps = new TreeMap<>();
        }
    }

    public synchronized void save() {
        try {
            Json.write(file, data);
        } catch (IOException e) {
            SuLog.error("Could not save teleport data", e);
        }
    }

    public synchronized Location spawn() {
        return data.spawn;
    }

    public synchronized void setSpawn(Location spawn) {
        data.spawn = spawn;
        save();
    }

    public synchronized Map<String, Location> warps() {
        return new TreeMap<>(data.warps);
    }

    public synchronized void setWarp(String name, Location location) {
        data.warps.put(name, location);
        save();
    }

    public synchronized boolean removeWarp(String name) {
        boolean removed = data.warps.remove(name) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized boolean importedTpa() {
        return data.importedTpa;
    }

    public synchronized void markImportedTpa() {
        data.importedTpa = true;
        save();
    }
}
