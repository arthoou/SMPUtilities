package com.arthou.smputilities.migrate;

import com.arthou.smputilities.mc.Stacks;
import com.arthou.smputilities.mc.Nbt;
import com.arthou.smputilities.backup.BackupService;
import com.arthou.smputilities.backup.PlayerSnapshot;
import com.arthou.smputilities.backup.SnapshotType;
import com.arthou.smputilities.core.punish.PunishmentType;
import com.arthou.smputilities.core.teleport.Location;
import com.arthou.smputilities.core.util.Json;
import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.profile.ProfileService;
import com.arthou.smputilities.profile.ProfileStore;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One-time import of data saved by the standalone mods SMPutilities replaces:
 * Inv Profile, Inventory Rollback Plus, TPA, DUSMP and DuTTS. Their files are
 * only read, never modified, so the old mods keep working if reinstalled.
 */
public final class LegacyImport {
    private static final class Marker {
        Set<String> done = new LinkedHashSet<>();
    }

    private LegacyImport() {
    }

    public static void run(MinecraftServer server, SuServer context) {
        Path markerFile = context.dataDir().resolve("imported.json");
        Marker marker = Json.read(markerFile, Marker.class, new Marker());
        boolean changed = false;
        changed |= once(marker, "invprofile", () -> SuServer.config().profiles.importInvProfile && importInvProfile(server));
        changed |= once(marker, "inventoryrollbackplus", () -> importRollback(server));
        changed |= once(marker, "tpa", () -> SuServer.config().teleport.importTpa && importTpa(server, context));
        changed |= once(marker, "dusmp", () -> importDusmp(server, context));
        changed |= once(marker, "dutts", () -> importDuTts(server, context));
        if (changed) {
            try {
                Json.write(markerFile, marker);
            } catch (IOException e) {
                SuLog.error("Could not save import marker", e);
            }
            context.players().flush();
        }
    }

    private interface Step {
        boolean run() throws Exception;
    }

    private static boolean once(Marker marker, String name, Step step) {
        if (marker.done.contains(name)) {
            return false;
        }
        try {
            if (step.run()) {
                marker.done.add(name);
                return true;
            }
        } catch (Exception e) {
            SuLog.error("Import of " + name + " data failed; it will be retried on the next start", e);
        }
        return false;
    }

    private static Optional<CompoundTag> savedData(MinecraftServer server, String name) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(name + ".dat");
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            CompoundTag root = Nbt.readCompressed(file);
            return Optional.of(Nbt.getCompound(root, "data"));
        } catch (IOException e) {
            SuLog.warn("Could not read legacy data " + file, e);
            return Optional.empty();
        }
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ----------------------------------------------------------- Inv Profile

    /** {Profiles: {uuid: {Main: [{Slot, Stack}], Armor: [...], Offhand: {...}}}} -> Quick Change slot. */
    private static boolean importInvProfile(MinecraftServer server) {
        Optional<CompoundTag> data = savedData(server, "arthous_invprofile_state");
        if (data.isEmpty()) {
            return true;
        }
        RegistryAccess registries = server.registryAccess();
        CompoundTag profiles = Nbt.getCompound(data.get(), "Profiles");
        int count = 0;
        for (String key : profiles.getAllKeys()) {
            UUID id = uuid(key);
            if (id == null) {
                continue;
            }
            CompoundTag legacy = Nbt.getCompound(profiles, key);
            ListTag inventory = new ListTag();
            copySlots(Nbt.getList(legacy, "Main"), 0, 36, inventory, registries);
            copySlots(Nbt.getList(legacy, "Armor"), 100, 4, inventory, registries);
            if (Nbt.hasCompound(legacy, "Offhand")) {
                ItemStack stack = Stacks.load(Nbt.getCompound(legacy, "Offhand"), registries);
                if (!stack.isEmpty()) {
                    CompoundTag slot = Stacks.save(stack, registries);
                    slot.putByte("Slot", (byte) 150);
                    inventory.add(slot);
                }
            }
            ProfileStore.PlayerProfiles stored = ProfileService.of(id);
            if (!stored.quick.contains("Inventory")) {
                CompoundTag quick = new CompoundTag();
                quick.put("Inventory", inventory);
                stored.quick = quick;
                ProfileService.store().save(id);
                count++;
            }
        }
        SuLog.info("Imported " + count + " Inv Profile slot(s) as Quick Change data");
        return true;
    }

    private static void copySlots(ListTag list, int base, int size, ListTag out, RegistryAccess registries) {
        for (CompoundTag entry : Nbt.compounds(list)) {
            int slot = Nbt.getInt(entry, "Slot", 0);
            if (slot < 0 || slot >= size || !Nbt.hasCompound(entry, "Stack")) {
                continue;
            }
            ItemStack stack = Stacks.load(Nbt.getCompound(entry, "Stack"), registries);
            if (!stack.isEmpty()) {
                CompoundTag tag = Stacks.save(stack, registries);
                tag.putByte("Slot", (byte) (base + slot));
                out.add(tag);
            }
        }
    }

    // ------------------------------------------------ Inventory Rollback Plus

    private static boolean importRollback(MinecraftServer server) {
        Optional<CompoundTag> data = savedData(server, "inventoryrollbackplus_state");
        if (data.isEmpty() || BackupService.store() == null) {
            return data.isEmpty();
        }
        CompoundTag players = Nbt.getCompound(data.get(), "players");
        int count = 0;
        for (String key : players.getAllKeys()) {
            UUID id = uuid(key);
            if (id == null) {
                continue;
            }
            ListTag list = Nbt.getList(players, key);
            List<PlayerSnapshot> snapshots = new ArrayList<>();
            for (CompoundTag entry : Nbt.compounds(list)) {
                snapshots.add(new PlayerSnapshot(BackupService.store().newId(id), Nbt.getLong(entry, "timestamp", 0),
                    SnapshotType.parse(Nbt.getString(entry, "type")), Nbt.getString(entry, "detail"), "Inventory Rollback Plus",
                    Nbt.getCompound(entry, "playerData"), Nbt.getCompound(entry, "compatData")));
            }
            count += BackupService.store().importLegacy(id, snapshots);
        }
        SuLog.info("Imported " + count + " Inventory Rollback Plus snapshot(s)");
        return true;
    }

    // ------------------------------------------------------------------- TPA

    private static Location location(CompoundTag tag) {
        return new Location(Nbt.getString(tag, "dimension"), Nbt.getDouble(tag, "x", 0), Nbt.getDouble(tag, "y", 0), Nbt.getDouble(tag, "z", 0),
            Nbt.getFloat(tag, "yaw", 0), Nbt.getFloat(tag, "pitch", 0));
    }

    private static boolean importTpa(MinecraftServer server, SuServer context) {
        Optional<CompoundTag> found = savedData(server, "tpa_state");
        if (found.isEmpty()) {
            return true;
        }
        CompoundTag data = found.get();
        if (Nbt.hasCompound(data, "spawn") && context.teleports().spawn() == null) {
            context.teleports().setSpawn(location(Nbt.getCompound(data, "spawn")));
        }
        CompoundTag warps = Nbt.getCompound(data, "warps");
        for (String name : warps.getAllKeys()) {
            if (!context.teleports().warps().containsKey(name)) {
                context.teleports().setWarp(name, location(Nbt.getCompound(warps, name)));
            }
        }
        CompoundTag homes = Nbt.getCompound(data, "homes");
        CompoundTag defaults = Nbt.getCompound(data, "defaultHomes");
        for (String key : homes.getAllKeys()) {
            UUID id = uuid(key);
            if (id == null) {
                continue;
            }
            CompoundTag playerHomes = Nbt.getCompound(homes, key);
            context.players().update(id, record -> {
                for (String home : playerHomes.getAllKeys()) {
                    record.homes.putIfAbsent(home, location(Nbt.getCompound(playerHomes, home)));
                }
                if (record.defaultHome.isEmpty() && defaults.contains(key)) {
                    record.defaultHome = Nbt.getString(defaults, key);
                }
            });
        }
        importLocations(context, Nbt.getCompound(data, "backLocations"), true);
        importLocations(context, Nbt.getCompound(data, "logoutLocations"), false);
        SuLog.info("Imported TPA homes, warps and spawn");
        return true;
    }

    private static void importLocations(SuServer context, CompoundTag tag, boolean back) {
        for (String key : tag.getAllKeys()) {
            UUID id = uuid(key);
            if (id != null) {
                Location location = location(Nbt.getCompound(tag, key));
                context.players().update(id, record -> {
                    if (back && record.back == null) {
                        record.back = location;
                    } else if (!back && record.logout == null) {
                        record.logout = location;
                    }
                });
            }
        }
    }

    // ----------------------------------------------------------------- DUSMP

    private static boolean importDusmp(MinecraftServer server, SuServer context) {
        Optional<CompoundTag> found = savedData(server, "dusmp_state");
        if (found.isEmpty()) {
            return true;
        }
        CompoundTag data = found.get();
        for (UUID id : uuids(Nbt.getList(data, "SilentJoinPlayers"))) {
            context.players().update(id, record -> record.silentJoin = true);
        }
        for (UUID id : uuids(Nbt.getList(data, "SilentQuitPlayers"))) {
            context.players().update(id, record -> record.silentQuit = true);
        }
        for (UUID id : uuids(Nbt.getList(data, "SilentHearPlayers"))) {
            context.players().update(id, record -> record.voiceDeafened = true);
        }
        long now = System.currentTimeMillis();
        for (UUID id : uuids(Nbt.getList(data, "SilentVoiceChatPlayers"))) {
            if (context.punishments().active(PunishmentType.VOICE_MUTE, id, now).isEmpty()) {
                String name = context.players().get(id).lastName;
                context.punishments().issue(PunishmentType.VOICE_MUTE, id, name, null, "DUSMP", "imported from DUSMP /silentvc", -1, now);
            }
        }
        SuLog.info("Imported DUSMP silent join/quit and voice settings");
        return true;
    }

    private static List<UUID> uuids(ListTag list) {
        List<UUID> result = new ArrayList<>();
        for (String value : Nbt.strings(list)) {
            UUID id = uuid(value);
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    // ----------------------------------------------------------------- DuTTS

    private static boolean importDuTts(MinecraftServer server, SuServer context) {
        Optional<CompoundTag> found = savedData(server, "dutts_state");
        if (found.isEmpty()) {
            return true;
        }
        CompoundTag modes = Nbt.getCompound(found.get(), "VoiceModes");
        CompoundTag languages = Nbt.getCompound(found.get(), "VoiceLanguages");
        for (String key : modes.getAllKeys()) {
            UUID id = uuid(key);
            if (id != null) {
                int mode = Nbt.getInt(modes, key, 0);
                String language = Nbt.getString(languages, key);
                context.players().update(id, record -> {
                    if (record.ttsMode == 0) {
                        record.ttsMode = mode;
                        record.ttsLanguage = language;
                    }
                });
            }
        }
        SuLog.info("Imported DuTTS voice settings");
        return true;
    }
}
