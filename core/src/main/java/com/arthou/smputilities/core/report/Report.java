package com.arthou.smputilities.core.report;

import com.arthou.smputilities.core.teleport.Location;

import java.util.UUID;

/** A player report (/report), stored in {@code <world>/smputilities/reports.json}. */
public final class Report {
    public int id;
    public UUID reporter;
    public String reporterName = "";
    public UUID target;
    public String targetName = "";
    public String reason = "";
    /** Where the reporter stood when reporting. */
    public Location where;
    public long createdAt;
    public boolean closed;
    public String closedBy = "";
    public long closedAt;
}
