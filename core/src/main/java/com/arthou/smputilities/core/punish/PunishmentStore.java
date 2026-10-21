package com.arthou.smputilities.core.punish;

import com.arthou.smputilities.core.util.Json;
import com.arthou.smputilities.core.util.SuLog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** All punishment records, persisted to {@code <world>/smputilities/punishments.json}. */
public final class PunishmentStore {
    private static final class FileData {
        List<Punishment> records = new ArrayList<>();
    }

    private final Path file;
    private final List<Punishment> records = new ArrayList<>();

    public PunishmentStore(Path file) {
        this.file = file;
    }

    public synchronized void load() {
        records.clear();
        FileData data = Json.read(file, FileData.class, new FileData());
        if (data.records != null) {
            for (Punishment record : data.records) {
                if (record != null && record.target != null && record.type != null) {
                    records.add(record);
                }
            }
        }
    }

    public synchronized void save() {
        FileData data = new FileData();
        data.records = new ArrayList<>(records);
        try {
            Json.write(file, data);
        } catch (IOException e) {
            SuLog.error("Could not save punishments", e);
        }
    }

    public synchronized Punishment issue(PunishmentType type, UUID target, String targetName, UUID actor, String actorName,
                                         String reason, long durationMillis, long now) {
        if (type.lasting()) {
            // A new ban/mute replaces the previous active one of the same type.
            for (Punishment existing : records) {
                if (existing.type == type && existing.target.equals(target) && existing.isActive(now)) {
                    existing.revokedAt = now;
                    existing.revokedBy = actorName;
                    existing.revokeReason = "replaced";
                }
            }
        }
        Punishment record = new Punishment();
        record.id = newId();
        record.type = type;
        record.target = target;
        record.targetName = targetName == null ? "" : targetName;
        record.actor = actor;
        record.actorName = actorName == null ? "Console" : actorName;
        record.reason = reason == null ? "" : reason;
        record.createdAt = now;
        record.expiresAt = !type.lasting() || durationMillis < 0 ? -1 : now + durationMillis;
        records.add(record);
        save();
        return record;
    }

    /** @return the revoked record, or empty when nothing of that type was active */
    public synchronized Optional<Punishment> revoke(PunishmentType type, UUID target, String actorName, String reason, long now) {
        Punishment revoked = null;
        for (Punishment record : records) {
            if (record.type == type && record.target.equals(target) && record.isActive(now)) {
                record.revokedAt = now;
                record.revokedBy = actorName == null ? "Console" : actorName;
                record.revokeReason = reason == null ? "" : reason;
                revoked = record;
            }
        }
        if (revoked != null) {
            save();
        }
        return Optional.ofNullable(revoked);
    }

    public synchronized Optional<Punishment> active(PunishmentType type, UUID target, long now) {
        return records.stream()
            .filter(record -> record.type == type && record.target.equals(target) && record.isActive(now))
            .max(Comparator.comparingLong(record -> record.createdAt));
    }

    public synchronized List<Punishment> history(UUID target) {
        List<Punishment> result = new ArrayList<>();
        for (Punishment record : records) {
            if (record.target.equals(target)) {
                result.add(record);
            }
        }
        result.sort(Comparator.comparingLong((Punishment record) -> record.createdAt).reversed());
        return result;
    }

    public synchronized List<Punishment> allActive(PunishmentType type, long now) {
        List<Punishment> result = new ArrayList<>();
        for (Punishment record : records) {
            if (record.type == type && record.isActive(now)) {
                result.add(record);
            }
        }
        result.sort(Comparator.comparingLong((Punishment record) -> record.createdAt).reversed());
        return result;
    }

    public synchronized List<Punishment> recent(int limit) {
        List<Punishment> result = new ArrayList<>(records);
        result.sort(Comparator.comparingLong((Punishment record) -> record.createdAt).reversed());
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    private String newId() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        while (true) {
            StringBuilder builder = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                builder.append(alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length())));
            }
            String id = builder.toString();
            if (records.stream().noneMatch(record -> record.id.equals(id))) {
                return id;
            }
        }
    }
}
