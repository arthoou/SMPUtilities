package com.arthou.smputilities.inventory;

import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Nbt;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Reads and rewrites {@code playerdata/<uuid>.dat} for players who are not
 * online. Callers must check that the player is offline; when they come online
 * the live entity is the only source of truth.
 */
public final class OfflinePlayerAccess {
    private OfflinePlayerAccess() {
    }

    public static boolean exists(MinecraftServer server, UUID id) {
        return Files.exists(file(server, id));
    }

    /** The saved player, upgraded to the current data version. */
    public static Optional<CompoundTag> load(MinecraftServer server, UUID id) {
        Path path = file(server, id);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            CompoundTag tag = Nbt.readCompressed(path);
            int version = Nbt.dataVersion(tag, -1);
            if (version >= 0 && version < Mc.dataVersion()) {
                tag = Mc.upgradePlayer(server, tag, version);
            }
            return Optional.of(tag);
        } catch (IOException | RuntimeException e) {
            SuLog.warn("Could not read offline player data of " + id, e);
            return Optional.empty();
        }
    }

    /**
     * Loads, edits and writes the player file in one step (temp file, then
     * replace with a .dat_old backup, the same way vanilla saves players).
     */
    public static boolean edit(MinecraftServer server, UUID id, UnaryOperator<CompoundTag> change) {
        if (server.getPlayerList().getPlayer(id) != null) {
            return false;
        }
        Optional<CompoundTag> loaded = load(server, id);
        if (loaded.isEmpty()) {
            return false;
        }
        return write(server, id, change.apply(loaded.get()));
    }

    public static boolean write(MinecraftServer server, UUID id, CompoundTag tag) {
        Path dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        try {
            Files.createDirectories(dir);
            Nbt.addCurrentDataVersion(tag);
            Path temp = Files.createTempFile(dir, id + "-", ".dat");
            try (OutputStream out = Files.newOutputStream(temp)) {
                Nbt.writeCompressed(tag, out);
            }
            Util.safeReplaceFile(file(server, id), temp, dir.resolve(id + ".dat_old"));
            return true;
        } catch (IOException | RuntimeException e) {
            SuLog.error("Could not write offline player data of " + id, e);
            return false;
        }
    }

    private static Path file(MinecraftServer server, UUID id) {
        return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat");
    }
}
