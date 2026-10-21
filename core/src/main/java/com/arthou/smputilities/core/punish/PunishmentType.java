package com.arthou.smputilities.core.punish;

public enum PunishmentType {
    BAN(true),
    MUTE(true),
    VOICE_MUTE(true),
    KICK(false),
    WARN(false);

    private final boolean lasting;

    PunishmentType(boolean lasting) {
        this.lasting = lasting;
    }

    /** Lasting punishments stay active until they expire or are revoked; others are one-off records. */
    public boolean lasting() {
        return lasting;
    }
}
