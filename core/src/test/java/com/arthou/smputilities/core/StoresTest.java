package com.arthou.smputilities.core;

import com.arthou.smputilities.core.modsync.Hashing;
import com.arthou.smputilities.core.modsync.ModEntry;
import com.arthou.smputilities.core.modsync.ModManifest;
import com.arthou.smputilities.core.modsync.SafeNames;
import com.arthou.smputilities.core.modsync.SyncPlan;
import com.arthou.smputilities.core.modsync.SyncPlanner;
import com.arthou.smputilities.core.player.PlayerStore;
import com.arthou.smputilities.core.portal.PortalRegistry;
import com.arthou.smputilities.core.punish.Punishment;
import com.arthou.smputilities.core.punish.PunishmentStore;
import com.arthou.smputilities.core.punish.PunishmentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoresTest {
    @Test
    void punishments(@TempDir Path dir) {
        Path file = dir.resolve("punishments.json");
        PunishmentStore store = new PunishmentStore(file);
        UUID target = UUID.randomUUID();
        store.issue(PunishmentType.MUTE, target, "Bob", null, "Console", "spam", 60_000, 1000);
        assertTrue(store.active(PunishmentType.MUTE, target, 2000).isPresent());
        assertFalse(store.active(PunishmentType.MUTE, target, 62_000).isPresent());
        store.issue(PunishmentType.BAN, target, "Bob", null, "Console", "grief", -1, 1000);
        store.issue(PunishmentType.KICK, target, "Bob", null, "Console", "", 0, 1500);

        PunishmentStore reloaded = new PunishmentStore(file);
        reloaded.load();
        assertEquals(3, reloaded.history(target).size());
        assertTrue(reloaded.active(PunishmentType.BAN, target, Long.MAX_VALUE - 1).isPresent());
        assertFalse(reloaded.active(PunishmentType.KICK, target, 1600).isPresent());
        Punishment revoked = reloaded.revoke(PunishmentType.BAN, target, "Admin", "appeal", 5000).orElseThrow();
        assertEquals("Admin", revoked.revokedBy);
        assertFalse(reloaded.active(PunishmentType.BAN, target, 6000).isPresent());
        assertTrue(reloaded.revoke(PunishmentType.BAN, target, "Admin", "", 7000).isEmpty());
    }

    @Test
    void portals(@TempDir Path dir) {
        PortalRegistry registry = new PortalRegistry(dir.resolve("portals.json"));
        Map<String, Integer> limits = Map.of("nether", 1);
        assertTrue(registry.canCreate(limits, "nether"));
        registry.register("nether", "minecraft:overworld", 0, 64, 0, "minecraft:nether_portal", null, "", 0);
        assertFalse(registry.canCreate(limits, "Nether"));
        assertTrue(registry.canCreate(limits, "end"));
        assertTrue(registry.near("minecraft:overworld", 2, 65, -1, 3).isPresent());
        assertFalse(registry.near("minecraft:the_nether", 2, 65, -1, 3).isPresent());

        PortalRegistry reloaded = new PortalRegistry(dir.resolve("portals.json"));
        reloaded.load();
        assertEquals(1, reloaded.count("nether"));
        assertTrue(reloaded.remove(reloaded.list("nether").get(0).id));
        assertEquals(0, reloaded.count("nether"));
    }

    @Test
    void playerStoreRoundTrip(@TempDir Path dir) {
        PlayerStore store = new PlayerStore(dir);
        UUID id = UUID.randomUUID();
        store.update(id, record -> {
            record.lastName = "Steve";
            record.nickname = "&6Capitão";
        });
        store.flush();
        PlayerStore reloaded = new PlayerStore(dir);
        reloaded.loadAll();
        assertEquals("&6Capitão", reloaded.get(id).nickname);
        assertEquals(id, reloaded.idByName("steve").orElseThrow());
    }

    @Test
    void syncPlanner(@TempDir Path dir) throws Exception {
        Path mods = Files.createDirectories(dir.resolve("mods"));
        Path present = mods.resolve("present-1.0.jar");
        Files.writeString(present, "present");
        Path stale = mods.resolve("old-managed.jar");
        Files.writeString(stale, "old");
        String presentHash = Hashing.sha512(present);
        String missingHash = "a".repeat(128);

        ModManifest manifest = new ModManifest("http://host:1234", "token", List.of(
            new ModEntry("present-1.0.jar", presentHash, 7, "", "", true),
            new ModEntry("missing-2.0.jar", missingHash, 10, "", "", true),
            new ModEntry("../evil.jar", missingHash, 10, "", "", true),
            new ModEntry("ext.jar", missingHash, 10, "http://cdn.modrinth.com/x.jar", "", true),
            new ModEntry("ext2.jar", missingHash, 10, "https://evil.example/x.jar", "", true),
            new ModEntry("ext3.jar", missingHash, 10, "https://cdn.modrinth.com/data/x.jar", "", true)));
        SyncPlan plan = SyncPlanner.plan(manifest, mods, List.of("old-managed.jar", "present-1.0.jar"), List.of("cdn.modrinth.com"));
        assertEquals(List.of("missing-2.0.jar", "ext3.jar"), plan.download().stream().map(ModEntry::fileName).toList());
        assertEquals(List.of("old-managed.jar"), plan.remove());
        assertEquals(3, plan.rejected().size());
        assertFalse(SafeNames.isSafeJarName("a/b.jar"));
        assertFalse(SafeNames.isSafeJarName("run.exe"));
        assertTrue(SafeNames.isSafeJarName("sodium-fabric-0.5.11+mc1.21.jar"));
    }
}
