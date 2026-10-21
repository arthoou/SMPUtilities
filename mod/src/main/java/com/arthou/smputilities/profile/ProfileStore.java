package com.arthou.smputilities.profile;

import com.arthou.smputilities.mc.Nbt;
import com.arthou.smputilities.core.util.AtomicFiles;
import com.arthou.smputilities.core.util.SuLog;
import net.minecraft.nbt.CompoundTag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inventory profiles per player in {@code <world>/smputilities/profiles/<uuid>.dat}:
 *
 * <pre>
 * { Active: "name", LastSwitch: long,
 *   Quick: {PlayerState tag},                       // Inv Profile "Quick Change" slot
 *   Profiles: { name: { Created, Updated, Nick, Data: {PlayerState tag}, Compat: {...} } } }
 * </pre>
 *
 * For the active profile the live player is the source of truth; its stored
 * Data is only the copy taken when the player last switched away from it.
 * Writes are synchronous so a switch never applies before its save succeeded.
 */
public final class ProfileStore {
    public static final String DEFAULT = "default";

    public static final class Profile {
        public final String name;
        public long created;
        public long updated;
        public String nick = "";
        public CompoundTag data = new CompoundTag();
        public CompoundTag compat = new CompoundTag();

        Profile(String name) {
            this.name = name;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Created", created);
            tag.putLong("Updated", updated);
            tag.putString("Nick", nick);
            tag.put("Data", data.copy());
            tag.put("Compat", compat.copy());
            return tag;
        }

        static Profile load(String name, CompoundTag tag) {
            Profile profile = new Profile(name);
            profile.created = Nbt.getLong(tag, "Created", 0);
            profile.updated = Nbt.getLong(tag, "Updated", 0);
            profile.nick = Nbt.getString(tag, "Nick");
            profile.data = Nbt.getCompound(tag, "Data");
            profile.compat = Nbt.getCompound(tag, "Compat");
            return profile;
        }
    }

    public static final class PlayerProfiles {
        public String active = DEFAULT;
        public long lastSwitch;
        public CompoundTag quick = new CompoundTag();
        public final Map<String, Profile> profiles = new java.util.LinkedHashMap<>();

        public List<String> names() {
            List<String> names = new ArrayList<>();
            names.add(DEFAULT);
            for (String name : profiles.keySet()) {
                if (!name.equals(DEFAULT)) {
                    names.add(name);
                }
            }
            return names;
        }

        public boolean exists(String name) {
            return name.equals(DEFAULT) || profiles.containsKey(name);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Active", active);
            tag.putLong("LastSwitch", lastSwitch);
            tag.put("Quick", quick.copy());
            CompoundTag list = new CompoundTag();
            profiles.forEach((name, profile) -> list.put(name, profile.save()));
            tag.put("Profiles", list);
            return tag;
        }

        static PlayerProfiles load(CompoundTag tag) {
            PlayerProfiles result = new PlayerProfiles();
            String active = Nbt.getString(tag, "Active");
            result.active = active.isEmpty() ? DEFAULT : active;
            result.lastSwitch = Nbt.getLong(tag, "LastSwitch", 0);
            result.quick = Nbt.getCompound(tag, "Quick");
            CompoundTag list = Nbt.getCompound(tag, "Profiles");
            for (String name : list.getAllKeys()) {
                result.profiles.put(name, Profile.load(name, Nbt.getCompound(list, name)));
            }
            return result;
        }
    }

    private final Path directory;
    private final Map<UUID, PlayerProfiles> cache = new ConcurrentHashMap<>();

    public ProfileStore(Path directory) {
        this.directory = directory;
    }

    public static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidName(String name) {
        return name != null && name.matches("[a-z0-9_\\-]{1,24}");
    }

    public PlayerProfiles get(UUID player) {
        return cache.computeIfAbsent(player, this::read);
    }

    /** Writes the player's profiles to disk now. */
    public boolean save(UUID player) {
        PlayerProfiles profiles = cache.get(player);
        if (profiles == null) {
            return true;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Nbt.writeCompressed(profiles.save(), bytes);
            AtomicFiles.writeBytes(file(player), bytes.toByteArray());
            return true;
        } catch (IOException | RuntimeException e) {
            SuLog.error("Could not save profiles of " + player, e);
            return false;
        }
    }

    /** Drops the cached copy so the next access re-reads the file (used to undo a failed change). */
    public void reload(UUID player) {
        cache.remove(player);
    }

    private PlayerProfiles read(UUID player) {
        Path file = file(player);
        if (!Files.exists(file)) {
            return new PlayerProfiles();
        }
        try {
            return PlayerProfiles.load(Nbt.readCompressed(file));
        } catch (IOException | RuntimeException e) {
            SuLog.error("Could not read profiles of " + player + "; keeping the file untouched", e);
            throw new IllegalStateException("profiles of " + player + " are unreadable", e);
        }
    }

    private Path file(UUID player) {
        return directory.resolve(player + ".dat");
    }
}
