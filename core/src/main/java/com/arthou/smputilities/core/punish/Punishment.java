package com.arthou.smputilities.core.punish;

import java.util.UUID;

/**
 * One moderation record. Mutable only for revocation; everything else is set
 * once when the punishment is issued.
 */
public final class Punishment {
    public String id = "";
    public PunishmentType type = PunishmentType.WARN;
    public UUID target;
    public String targetName = "";
    public UUID actor;
    public String actorName = "Console";
    public String reason = "";
    public long createdAt;
    /** -1 for permanent. */
    public long expiresAt = -1;
    public long revokedAt = 0;
    public String revokedBy = "";
    public String revokeReason = "";

    public boolean isPermanent() {
        return expiresAt < 0;
    }

    public boolean isActive(long now) {
        if (!type.lasting() || revokedAt > 0) {
            return false;
        }
        return isPermanent() || now < expiresAt;
    }

    public long remaining(long now) {
        return isPermanent() ? -1 : Math.max(0, expiresAt - now);
    }
}
