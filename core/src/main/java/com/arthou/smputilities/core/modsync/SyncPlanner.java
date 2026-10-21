package com.arthou.smputilities.core.modsync;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Compares a manifest with the local mods folder. Never modifies anything. */
public final class SyncPlanner {
    private SyncPlanner() {
    }

    /**
     * @param managed      file names previously installed by mod sync (only these may be removed)
     * @param allowedHosts hosts external URLs may point to
     */
    public static SyncPlan plan(ModManifest manifest, Path modsDir, Collection<String> managed, Collection<String> allowedHosts) throws IOException {
        Map<String, String> localHashes = new HashMap<>();
        Map<String, String> byHash = new HashMap<>();
        if (Files.isDirectory(modsDir)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(modsDir, "*.jar")) {
                for (Path file : files) {
                    String hash = Hashing.sha512(file);
                    localHashes.put(file.getFileName().toString(), hash);
                    byHash.put(hash, file.getFileName().toString());
                }
            }
        }

        List<ModEntry> download = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        Set<String> wanted = new HashSet<>();
        for (ModEntry entry : manifest.entries()) {
            String problem = problem(entry, manifest, allowedHosts);
            if (problem != null) {
                rejected.add(entry.fileName() + ": " + problem);
                continue;
            }
            String hash = entry.sha512().toLowerCase(Locale.ROOT);
            wanted.add(entry.fileName());
            if (byHash.containsKey(hash)) {
                // Same content already present, maybe under another name.
                wanted.add(byHash.get(hash));
                continue;
            }
            download.add(entry);
        }

        List<String> remove = new ArrayList<>();
        for (String name : managed) {
            if (!wanted.contains(name) && localHashes.containsKey(name)) {
                remove.add(name);
            }
        }
        // An outdated managed file with the same name is replaced by its download.
        return new SyncPlan(download, remove, rejected);
    }

    static String problem(ModEntry entry, ModManifest manifest, Collection<String> allowedHosts) {
        if (!SafeNames.isSafeJarName(entry.fileName())) {
            return "unsafe file name";
        }
        if (!SafeNames.isSha512(entry.sha512() == null ? null : entry.sha512().toLowerCase(Locale.ROOT))) {
            return "missing or invalid sha512";
        }
        if (entry.hosted()) {
            return manifest.hostBase() == null || manifest.hostBase().isEmpty() ? "server host disabled" : null;
        }
        try {
            URI uri = URI.create(entry.url());
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return "external downloads must use https";
            }
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            for (String allowed : allowedHosts) {
                String value = allowed.toLowerCase(Locale.ROOT);
                if (host.equals(value) || host.endsWith("." + value)) {
                    return null;
                }
            }
            return "host not allowed: " + host;
        } catch (IllegalArgumentException e) {
            return "invalid url";
        }
    }
}
