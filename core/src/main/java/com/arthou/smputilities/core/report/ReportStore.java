package com.arthou.smputilities.core.report;

import com.arthou.smputilities.core.teleport.Location;
import com.arthou.smputilities.core.util.Json;
import com.arthou.smputilities.core.util.SuLog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Player reports. Open ones are kept until staff closes them; only the newest
 * {@code keepClosed} closed reports are kept, so the file stays small.
 */
public final class ReportStore {
    private static final class FileData {
        int nextId = 1;
        List<Report> reports = new ArrayList<>();
    }

    private final Path file;
    private final List<Report> reports = new ArrayList<>();
    private int nextId = 1;

    public ReportStore(Path file) {
        this.file = file;
    }

    public synchronized void load() {
        reports.clear();
        FileData data = Json.read(file, FileData.class, new FileData());
        nextId = Math.max(1, data.nextId);
        if (data.reports != null) {
            for (Report report : data.reports) {
                if (report != null && report.target != null) {
                    reports.add(report);
                    nextId = Math.max(nextId, report.id + 1);
                }
            }
        }
    }

    public synchronized void save() {
        FileData data = new FileData();
        data.nextId = nextId;
        data.reports = new ArrayList<>(reports);
        try {
            Json.write(file, data);
        } catch (IOException e) {
            SuLog.error("Could not save reports", e);
        }
    }

    public synchronized Report create(UUID reporter, String reporterName, UUID target, String targetName, String reason, Location where, long now) {
        Report report = new Report();
        report.id = nextId++;
        report.reporter = reporter;
        report.reporterName = reporterName == null ? "Console" : reporterName;
        report.target = target;
        report.targetName = targetName == null ? "" : targetName;
        report.reason = reason == null ? "" : reason;
        report.where = where;
        report.createdAt = now;
        reports.add(report);
        save();
        return report;
    }

    /** Open reports, oldest first. */
    public synchronized List<Report> open() {
        List<Report> result = new ArrayList<>();
        for (Report report : reports) {
            if (!report.closed) {
                result.add(report);
            }
        }
        return result;
    }

    public synchronized Optional<Report> get(int id) {
        for (Report report : reports) {
            if (report.id == id) {
                return Optional.of(report);
            }
        }
        return Optional.empty();
    }

    /** Open reports against one player. */
    public synchronized long openAgainst(UUID target) {
        return reports.stream().filter(report -> !report.closed && target.equals(report.target)).count();
    }

    /** @return false when there is no open report with that id */
    public synchronized boolean close(int id, String actorName, long now, int keepClosed) {
        for (Report report : reports) {
            if (report.id == id && !report.closed) {
                report.closed = true;
                report.closedBy = actorName == null ? "Console" : actorName;
                report.closedAt = now;
                prune(keepClosed);
                save();
                return true;
            }
        }
        return false;
    }

    private void prune(int keepClosed) {
        int closed = 0;
        for (int i = reports.size() - 1; i >= 0; i--) {
            if (reports.get(i).closed && ++closed > Math.max(0, keepClosed)) {
                reports.remove(i);
            }
        }
    }
}
