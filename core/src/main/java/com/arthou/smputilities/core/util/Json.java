package com.arthou.smputilities.core.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Json {
    public static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .serializeNulls()
        .create();

    private Json() {
    }

    /**
     * Reads {@code file} into {@code type}, keeping defaults for missing keys.
     * Returns {@code fallback} when the file is missing; a broken file is moved
     * aside as {@code .broken} and the fallback is used, so startup never fails
     * because of a typo in a config.
     */
    public static <T> T read(Path file, Class<T> type, T fallback) {
        if (!Files.exists(file)) {
            return fallback;
        }
        try {
            String text = AtomicFiles.readString(file);
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw new IOException("expected a JSON object");
            }
            // Gson builds objects through their no-arg constructors, so every
            // field (nested sections included) absent from the file keeps its default.
            T value = GSON.fromJson(parsed, type);
            return value == null ? fallback : value;
        } catch (Exception e) {
            SuLog.warn("Could not read " + file + ", using defaults", e);
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // keep the original file if it cannot be moved
            }
            return fallback;
        }
    }

    public static void write(Path file, Object value) throws IOException {
        AtomicFiles.writeString(file, GSON.toJson(value));
    }
}
