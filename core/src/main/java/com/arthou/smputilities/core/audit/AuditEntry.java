package com.arthou.smputilities.core.audit;

import java.util.UUID;

/** Who did what to whom, and when. */
public final class AuditEntry {
    public long time;
    public UUID actor;
    public String actorName = "Console";
    public String action = "";
    public UUID target;
    public String targetName = "";
    public String details = "";

    public AuditEntry() {
    }

    public AuditEntry(long time, UUID actor, String actorName, String action, UUID target, String targetName, String details) {
        this.time = time;
        this.actor = actor;
        this.actorName = actorName == null ? "Console" : actorName;
        this.action = action;
        this.target = target;
        this.targetName = targetName == null ? "" : targetName;
        this.details = details == null ? "" : details;
    }
}
