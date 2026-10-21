package com.arthou.smputilities.stats;

import com.arthou.smputilities.core.util.AtomicFiles;
import com.arthou.smputilities.core.util.Json;
import com.arthou.smputilities.core.util.SuLog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Vanilla statistics for online players, or read from the stats file for offline ones. */
public final class PlayerStats {
    public enum Kind {
        DEATHS(Stats.DEATHS),
        PLAYER_KILLS(Stats.PLAYER_KILLS),
        MOB_KILLS(Stats.MOB_KILLS),
        PLAY_TIME(Stats.PLAY_TIME);

        private final ResourceLocation id;

        Kind(ResourceLocation id) {
            this.id = id;
        }

        public ResourceLocation id() {
            return id;
        }
    }

    public record Values(int deaths, int playerKills, int mobKills, long playTicks) {
    }

    private PlayerStats() {
    }

    public static Values read(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return new Values(get(online, Kind.DEATHS), get(online, Kind.PLAYER_KILLS), get(online, Kind.MOB_KILLS), get(online, Kind.PLAY_TIME));
        }
        JsonObject custom = customSection(file(server, id));
        return new Values(value(custom, Kind.DEATHS), value(custom, Kind.PLAYER_KILLS), value(custom, Kind.MOB_KILLS), value(custom, Kind.PLAY_TIME));
    }

    private static int get(ServerPlayer player, Kind kind) {
        Stat<ResourceLocation> stat = Stats.CUSTOM.get(kind.id());
        return player.getStats().getValue(stat);
    }

    /** Sets a statistic, online or offline. */
    public static boolean set(MinecraftServer server, UUID id, Kind kind, int value) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            online.getStats().setValue(online, Stats.CUSTOM.get(kind.id()), value);
            online.getStats().markAllDirty();
            online.getStats().save();
            return true;
        }
        Path file = file(server, id);
        try {
            JsonObject root = Files.exists(file) ? JsonParser.parseString(AtomicFiles.readString(file)).getAsJsonObject() : new JsonObject();
            JsonObject stats = root.has("stats") ? root.getAsJsonObject("stats") : new JsonObject();
            JsonObject custom = stats.has("minecraft:custom") ? stats.getAsJsonObject("minecraft:custom") : new JsonObject();
            custom.addProperty(kind.id().toString(), value);
            stats.add("minecraft:custom", custom);
            root.add("stats", stats);
            AtomicFiles.writeString(file, Json.GSON.toJson(root));
            return true;
        } catch (IOException | RuntimeException e) {
            SuLog.warn("Could not edit stats of " + id, e);
            return false;
        }
    }

    private static Path file(MinecraftServer server, UUID id) {
        return server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(id + ".json");
    }

    private static JsonObject customSection(Path file) {
        if (!Files.exists(file)) {
            return new JsonObject();
        }
        try {
            JsonObject root = JsonParser.parseString(AtomicFiles.readString(file)).getAsJsonObject();
            JsonObject stats = root.has("stats") ? root.getAsJsonObject("stats") : new JsonObject();
            return stats.has("minecraft:custom") ? stats.getAsJsonObject("minecraft:custom") : new JsonObject();
        } catch (IOException | RuntimeException e) {
            return new JsonObject();
        }
    }

    private static int value(JsonObject custom, Kind kind) {
        String key = kind.id().toString();
        return custom.has(key) ? custom.get(key).getAsInt() : 0;
    }
}
