package com.arthou.smputilities.core.modsync;

import com.arthou.smputilities.core.util.AtomicFiles;
import com.arthou.smputilities.core.util.SuLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes a {@link SyncPlan} on a background thread. Every file is written to
 * a temporary name, checked against its SHA-512 and only then moved into the
 * mods folder. Nothing downloaded is ever loaded or run by this class; the game
 * picks the files up on its next start.
 */
public final class ModDownloader {
    public interface Listener {
        void progress(String fileName, long done, long total, int fileIndex, int fileCount);

        void finished(Result result);
    }

    public record Result(boolean cancelled, List<String> installed, List<String> removed, List<String> failed, List<String> manual) {
        public boolean restartRequired() {
            return !installed.isEmpty() || !removed.isEmpty();
        }
    }

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public void cancel() {
        cancelled.set(true);
    }

    /**
     * @param credentials client-side credentials by name ("Bearer abc", "Basic ..."), never received from servers
     * @param managed     the client's set of files installed by mod sync; updated in place
     */
    public Thread start(ModManifest manifest, SyncPlan plan, Path modsDir, Path stateDir, Map<String, String> credentials,
                        Set<String> managed, long maxBytes, Listener listener) {
        Thread thread = new Thread(() -> listener.finished(run(manifest, plan, modsDir, stateDir, credentials, managed, maxBytes, listener)),
            "SMPutilities-modsync");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    Result run(ModManifest manifest, SyncPlan plan, Path modsDir, Path stateDir, Map<String, String> credentials,
               Set<String> managed, long maxBytes, Listener listener) {
        List<String> installed = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<String> manual = new ArrayList<>();
        Path temp = stateDir.resolve("download");
        Path removedDir = stateDir.resolve("removed");
        try {
            Files.createDirectories(temp);
            Files.createDirectories(modsDir);
        } catch (IOException e) {
            failed.add("mods folder: " + e.getMessage());
            return new Result(false, installed, removed, failed, manual);
        }

        int index = 0;
        for (ModEntry entry : plan.download()) {
            if (cancelled.get()) {
                return new Result(true, installed, removed, failed, manual);
            }
            index++;
            Path part = temp.resolve(entry.fileName() + ".part");
            try {
                download(manifest, entry, part, credentials, maxBytes, listener, index, plan.download().size());
                Path target = modsDir.resolve(entry.fileName());
                try {
                    AtomicFiles.move(part, target);
                    installed.add(entry.fileName());
                    managed.add(entry.fileName());
                } catch (IOException locked) {
                    // A jar the running game holds open (Windows) cannot be replaced now.
                    Path pending = stateDir.resolve("pending").resolve(entry.fileName());
                    Files.createDirectories(pending.getParent());
                    Files.move(part, pending, StandardCopyOption.REPLACE_EXISTING);
                    manual.add(entry.fileName() + " -> " + pending);
                }
            } catch (CancelledException e) {
                deleteQuietly(part);
                return new Result(true, installed, removed, failed, manual);
            } catch (Exception e) {
                deleteQuietly(part);
                failed.add(entry.fileName() + ": " + e.getMessage());
                SuLog.warn("Mod sync download failed for " + entry.fileName(), e);
            }
        }

        for (String name : plan.remove()) {
            if (cancelled.get()) {
                return new Result(true, installed, removed, failed, manual);
            }
            if (!SafeNames.isSafeJarName(name)) {
                continue;
            }
            try {
                Files.createDirectories(removedDir);
                Files.move(modsDir.resolve(name), removedDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                managed.remove(name);
                removed.add(name);
            } catch (IOException e) {
                manual.add("remove " + name);
            }
        }
        return new Result(false, installed, removed, failed, manual);
    }

    private void download(ModManifest manifest, ModEntry entry, Path part, Map<String, String> credentials, long maxBytes,
                          Listener listener, int index, int count) throws Exception {
        URI uri = entry.hosted()
            ? URI.create(manifest.hostBase() + "/modsync/" + entry.sha512().toLowerCase(Locale.ROOT))
            : URI.create(entry.url());
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMinutes(5))
            .header("User-Agent", "SMPutilities-ModSync")
            .GET();
        if (entry.hosted()) {
            request.header("X-SMPutilities-Token", manifest.token());
        } else if (entry.credential() != null && !entry.credential().isEmpty()) {
            String credential = credentials.get(entry.credential());
            if (credential == null) {
                throw new IOException("missing client credential '" + entry.credential() + "'");
            }
            request.header("Authorization", credential);
        }
        HttpResponse<InputStream> response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("HTTP " + response.statusCode());
        }
        long total = entry.size() > 0 ? entry.size() : response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (total > maxBytes) {
            response.body().close();
            throw new IOException("file larger than the allowed " + maxBytes + " bytes");
        }
        MessageDigest digest = Hashing.digest();
        long done = 0;
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (cancelled.get()) {
                    throw new CancelledException();
                }
                done += read;
                if (done > maxBytes) {
                    throw new IOException("file larger than the allowed " + maxBytes + " bytes");
                }
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
                listener.progress(entry.fileName(), done, total, index, count);
            }
        }
        String hash = Hashing.hex(digest.digest());
        if (!hash.equalsIgnoreCase(entry.sha512())) {
            throw new IOException("checksum mismatch");
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // leftover temp file, removed next time
        }
    }

    private static final class CancelledException extends Exception {
        CancelledException() {
            super("cancelled");
        }
    }

    /** Builds a new set (for callers that keep the managed list as JSON). */
    public static Set<String> mutableSet(List<String> values) {
        return new LinkedHashSet<>(values == null ? List.of() : values);
    }
}
