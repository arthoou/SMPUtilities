package com.arthou.smputilities.server;

import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.core.util.SuLog;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;

/**
 * Server-side translation. Messages are translated before they are sent, so
 * players without the client mod still read English or Portuguese instead of
 * raw translation keys. The same files feed the client GUI.
 */
public final class Lang {
    public static final String EN = "en_us";
    public static final String PT = "pt_br";

    private static final Map<String, Map<String, String>> TABLES = new HashMap<>();

    private Lang() {
    }

    private static synchronized Map<String, String> table(String language) {
        return TABLES.computeIfAbsent(language, Lang::load);
    }

    private static Map<String, String> load(String language) {
        Map<String, String> map = new HashMap<>();
        String path = "/assets/" + SmpUtilities.MOD_ID + "/lang/" + language + ".json";
        try (InputStream in = Lang.class.getResourceAsStream(path)) {
            if (in == null) {
                return map;
            }
            JsonObject object = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                map.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception e) {
            SuLog.warn("Could not read language file " + path, e);
        }
        return map;
    }

    /** Language for a player: the configured one, or the client's own when "auto". */
    public static String languageOf(ServerPlayer player) {
        String configured = SuServer.config().language;
        if (configured != null && !configured.equalsIgnoreCase("auto")) {
            return configured.toLowerCase(Locale.ROOT).startsWith("pt") ? PT : EN;
        }
        if (player == null) {
            return EN;
        }
        String client = player.clientInformation().language();
        return client != null && client.toLowerCase(Locale.ROOT).startsWith("pt") ? PT : EN;
    }

    public static String tr(ServerPlayer player, String key, Object... args) {
        return format(languageOf(player), key, args);
    }

    public static String format(String language, String key, Object... args) {
        String pattern = table(language).get(key);
        if (pattern == null) {
            pattern = table(EN).getOrDefault(key, key);
        }
        if (args.length == 0) {
            return pattern;
        }
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (IllegalFormatException e) {
            return pattern;
        }
    }

    public static boolean has(String key) {
        return table(EN).containsKey(key);
    }
}
