package com.arthou.smputilities.core.config;

import com.arthou.smputilities.core.util.Json;
import com.arthou.smputilities.core.util.SuLog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Loads, saves and hot-reloads {@link SuConfig}. */
public final class ConfigManager {
    private final Path file;
    private final List<Consumer<SuConfig>> listeners = new CopyOnWriteArrayList<>();
    private volatile SuConfig config = new SuConfig();

    public ConfigManager(Path file) {
        this.file = file;
    }

    public SuConfig get() {
        return config;
    }

    public Path file() {
        return file;
    }

    /** Reads the file (creating it with defaults when missing) and notifies listeners. */
    public synchronized SuConfig load() {
        config = Json.read(file, SuConfig.class, new SuConfig());
        save();
        listeners.forEach(listener -> SuLog.guard("config listener", config, listener));
        return config;
    }

    public synchronized void save() {
        try {
            Json.write(file, config);
        } catch (IOException e) {
            SuLog.error("Could not save " + file, e);
        }
    }

    /** Called after every load or reload, e.g. to re-sync settings to clients. */
    public void onChange(Consumer<SuConfig> listener) {
        listeners.add(listener);
    }

    /** Applies an edit made at runtime (panel or command), saves and notifies. */
    public synchronized void edit(Consumer<SuConfig> change) {
        change.accept(config);
        save();
        listeners.forEach(listener -> SuLog.guard("config listener", config, listener));
    }
}
