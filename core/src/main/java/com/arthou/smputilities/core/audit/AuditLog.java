package com.arthou.smputilities.core.audit;

import com.arthou.smputilities.core.util.Json;
import com.arthou.smputilities.core.util.SuLog;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Append-only administrative log, one JSON object per line in
 * {@code <world>/smputilities/logs/audit-YYYY-MM.jsonl}. Writing happens on a
 * background thread so the server tick never waits on disk.
 */
public final class AuditLog {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);
    private static final int MEMORY = 500;
    private static final Gson COMPACT = new Gson();

    private final Path directory;
    private final Deque<AuditEntry> recent = new ArrayDeque<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SMPutilities-audit");
        thread.setDaemon(true);
        return thread;
    });

    public AuditLog(Path directory) {
        this.directory = directory;
        loadRecent();
    }

    public void record(UUID actor, String actorName, String action, UUID target, String targetName, String details) {
        AuditEntry entry = new AuditEntry(System.currentTimeMillis(), actor, actorName, action, target, targetName, details);
        synchronized (recent) {
            recent.addFirst(entry);
            while (recent.size() > MEMORY) {
                recent.removeLast();
            }
        }
        SuLog.info("[audit] " + entry.actorName + " " + action + (entry.targetName.isEmpty() ? "" : " -> " + entry.targetName)
            + (entry.details.isEmpty() ? "" : " (" + entry.details + ")"));
        writer.execute(() -> append(entry));
    }

    public List<AuditEntry> recent(int limit, UUID filterTarget) {
        List<AuditEntry> result = new ArrayList<>();
        synchronized (recent) {
            for (AuditEntry entry : recent) {
                if (filterTarget == null || filterTarget.equals(entry.target) || filterTarget.equals(entry.actor)) {
                    result.add(entry);
                    if (result.size() >= limit) {
                        break;
                    }
                }
            }
        }
        return result;
    }

    /** Deletes monthly files older than the retention period. */
    public void prune(int retentionDays) {
        if (retentionDays <= 0 || !Files.isDirectory(directory)) {
            return;
        }
        String oldest = MONTH.format(Instant.now().minusSeconds(retentionDays * 86_400L));
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "audit-*.jsonl")) {
            for (Path file : files) {
                String month = file.getFileName().toString().substring(6, 13);
                if (month.compareTo(oldest) < 0) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException | StringIndexOutOfBoundsException e) {
            SuLog.warn("Could not prune audit logs", e);
        }
    }

    public void close() {
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void append(AuditEntry entry) {
        Path file = directory.resolve("audit-" + MONTH.format(Instant.ofEpochMilli(entry.time)) + ".jsonl");
        try {
            Files.createDirectories(directory);
            try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                out.write(COMPACT.toJson(entry));
                out.newLine();
            }
        } catch (IOException e) {
            SuLog.error("Could not write audit log", e);
        }
    }

    private void loadRecent() {
        Path file = directory.resolve("audit-" + MONTH.format(Instant.now()) + ".jsonl");
        if (!Files.exists(file)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = Math.max(0, lines.size() - MEMORY); i < lines.size(); i++) {
                try {
                    AuditEntry entry = Json.GSON.fromJson(lines.get(i), AuditEntry.class);
                    if (entry != null) {
                        recent.addFirst(entry);
                    }
                } catch (RuntimeException ignored) {
                    // skip a damaged line
                }
            }
        } catch (IOException e) {
            SuLog.warn("Could not read audit log", e);
        }
    }
}
