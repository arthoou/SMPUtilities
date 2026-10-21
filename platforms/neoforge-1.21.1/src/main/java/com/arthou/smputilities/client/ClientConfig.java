package com.arthou.smputilities.client;

import com.arthou.smputilities.core.util.Json;
import com.arthou.smputilities.core.util.SuLog;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-only settings ({@code config/smputilities/client.json}). Never sent to servers. */
public final class ClientConfig {
    public Bubbles bubbles = new Bubbles();
    public ModSync modSync = new ModSync();
    public Tts tts = new Tts();

    public static final class Bubbles {
        public boolean enabled = true;
        public boolean showOwn = true;
    }

    public static final class ModSync {
        /** Show the update screen when a server offers files. */
        public boolean prompt = true;
        /** Extra hosts trusted for external downloads (the server's list is also shown in the screen). */
        public List<String> allowedHosts = new ArrayList<>();
        /** Credential name -> Authorization header value, for private repositories. */
        public Map<String, String> credentials = new LinkedHashMap<>();
        /** Files installed by mod sync (the only ones it may remove later). */
        public List<String> managed = new ArrayList<>();
    }

    public static final class Tts {
        /** Start the voice bridge automatically when a TTS line has to be spoken. */
        public boolean autoStartBridge = true;
    }

    private static ClientConfig instance;

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("smputilities").resolve("client.json");
    }

    public static synchronized ClientConfig get() {
        if (instance == null) {
            instance = Json.read(file(), ClientConfig.class, new ClientConfig());
            save();
        }
        return instance;
    }

    public static synchronized void save() {
        try {
            Json.write(file(), instance);
        } catch (IOException e) {
            SuLog.warn("Could not save client config", e);
        }
    }
}
