package com.arthou.smputilities.modsync;

import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.modsync.Hashing;
import com.arthou.smputilities.core.modsync.ModEntry;
import com.arthou.smputilities.core.modsync.ModManifest;
import com.arthou.smputilities.core.modsync.ModSyncHost;
import com.arthou.smputilities.core.modsync.SafeNames;
import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.network.SuNetwork;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server half of mod sync: lists the files in {@code config/smputilities/modsync}
 * (hashed in the background) plus configured external downloads, and sends
 * that manifest to joining clients with the mod. The optional built-in host
 * serves only those files, only to connected players.
 */
public final class ModSyncServer {
    private static final ModSyncHost HOST = new ModSyncHost();
    private static final Map<UUID, String> TOKENS = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile List<ModEntry> hosted = List.of();
    private static boolean hostRunning;

    private ModSyncServer() {
    }

    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve("smputilities").resolve("modsync");
    }

    public static void onServerStart() {
        if (!SuServer.config().features.modSync) {
            return;
        }
        Thread scanner = new Thread(ModSyncServer::rescan, "SMPutilities-modsync-scan");
        scanner.setDaemon(true);
        scanner.start();
        int port = SuServer.config().modSync.hostPort;
        if (port > 0) {
            try {
                HOST.start(port);
                hostRunning = true;
            } catch (IOException e) {
                SuLog.error("Mod sync host could not listen on port " + port, e);
            }
        }
    }

    public static void onServerStop() {
        HOST.stop();
        hostRunning = false;
        TOKENS.clear();
    }

    /** Hashes every jar in the mod sync folder. Runs off the main thread. */
    public static synchronized void rescan() {
        Path dir = directory();
        List<ModEntry> entries = new ArrayList<>();
        Map<String, Path> byHash = new HashMap<>();
        try {
            Files.createDirectories(dir);
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.jar")) {
                for (Path file : files) {
                    String name = file.getFileName().toString();
                    if (!SafeNames.isSafeJarName(name)) {
                        SuLog.warn("Mod sync skips " + name + ": unsupported file name");
                        continue;
                    }
                    String hash = Hashing.sha512(file);
                    entries.add(new ModEntry(name, hash, Files.size(file), "", "", true));
                    byHash.put(hash, file);
                }
            }
        } catch (IOException e) {
            SuLog.error("Could not scan " + dir, e);
        }
        hosted = List.copyOf(entries);
        HOST.setFiles(byHash);
        SuLog.info("Mod sync: " + entries.size() + " hosted file(s), " + SuServer.config().modSync.external.size() + " external");
    }

    public static ModManifest manifestFor(ServerPlayer player) {
        SuConfig.ModSync config = SuServer.config().modSync;
        List<ModEntry> entries = new ArrayList<>();
        String hostBase = "";
        String token = "";
        if (hostRunning && !hosted.isEmpty()) {
            entries.addAll(hosted);
            token = TOKENS.computeIfAbsent(player.getUUID(), ignored -> {
                byte[] bytes = new byte[24];
                RANDOM.nextBytes(bytes);
                String value = HexFormat.of().formatHex(bytes);
                HOST.addToken(value);
                return value;
            });
            // "@" means "the address you used to join"; the client fills it in.
            String host = config.publicHost == null || config.publicHost.isBlank() ? "@" : config.publicHost.trim();
            hostBase = "http://" + host + ":" + config.hostPort;
        }
        for (SuConfig.ModSync.External external : config.external) {
            entries.add(new ModEntry(external.fileName, external.sha512 == null ? "" : external.sha512.toLowerCase(Locale.ROOT),
                external.size, external.url, external.credential == null ? "" : external.credential, external.required));
        }
        return new ModManifest(hostBase, token, entries);
    }

    public static void onLogin(ServerPlayer player) {
        if (!SuServer.config().features.modSync) {
            return;
        }
        ModManifest manifest = manifestFor(player);
        if (!manifest.entries().isEmpty()) {
            SuNetwork.sendManifest(player, manifest, SuServer.config().modSync.allowedHosts, SuServer.config().modSync.maxFileBytes);
        }
    }

    public static void onLogout(ServerPlayer player) {
        String token = TOKENS.remove(player.getUUID());
        if (token != null) {
            HOST.removeToken(token);
        }
    }

    public static int hostedCount() {
        return hosted.size();
    }
}
