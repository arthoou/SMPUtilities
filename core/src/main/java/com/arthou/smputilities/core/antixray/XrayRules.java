package com.arthou.smputilities.core.antixray;

/**
 * What the anti-xray does in one dimension, after DrexHD's AntiXray (MIT),
 * itself a port of Paper's anti-xray: which block states hide others, which
 * are hidden and what they are shown as.
 */
public final class XrayRules {
    /** AntiXray's engine modes. */
    public enum Mode {
        /** Hidden blocks that touch no transparent block are shown as stone (deepslate, netherrack, end stone). */
        HIDE(1),
        /** Every hidden or replacement block that touches no transparent block is shown as a random hidden block. */
        OBFUSCATE(2),
        /** Like OBFUSCATE, with one random block per layer of a section. */
        OBFUSCATE_LAYER(3);

        public final int id;

        Mode(int id) {
            this.id = id;
        }

        public static Mode byId(int id) {
            for (Mode mode : values()) {
                if (mode.id == id) {
                    return mode;
                }
            }
            return null;
        }
    }

    public final Mode mode;
    /** Sections starting at or above this height are sent as they are; a multiple of 16. */
    public final int maxBlockHeight;
    /** How far around a broken or uncovered block the real blocks are sent again. */
    public final int updateRadius;
    private final boolean[] solid;
    private final boolean[] obfuscate;
    private final int[] presetsBelowZero;
    private final int[] presetsAboveZero;
    private final int[][] updateOffsets;

    /**
     * @param solid       by block state id: hides what is behind it (a full, occluding block)
     * @param obfuscate   by block state id: is replaced when nothing transparent touches it
     * @param presetsBelowZero ids shown instead in sections below y 0
     * @param presetsAboveZero ids shown instead in sections from y 0
     */
    public XrayRules(Mode mode, int maxBlockHeight, int updateRadius, boolean[] solid, boolean[] obfuscate,
                     int[] presetsBelowZero, int[] presetsAboveZero) {
        if (presetsBelowZero.length == 0 || presetsAboveZero.length == 0) {
            throw new IllegalArgumentException("no blocks to show instead");
        }
        this.mode = mode;
        this.maxBlockHeight = maxBlockHeight >> 4 << 4;
        this.updateRadius = Math.max(0, updateRadius);
        this.solid = solid;
        this.obfuscate = obfuscate;
        this.presetsBelowZero = presetsBelowZero;
        this.presetsAboveZero = presetsAboveZero;
        this.updateOffsets = offsets(this.updateRadius);
    }

    public boolean solid(int id) {
        return id >= 0 && id < solid.length && solid[id];
    }

    public boolean obfuscates(int id) {
        return id >= 0 && id < obfuscate.length && obfuscate[id];
    }

    /** What a section starting at this height shows instead, or null when it is sent unchanged. */
    public int[] presets(int bottomBlockY) {
        if (bottomBlockY >= maxBlockHeight) {
            return null;
        }
        return bottomBlockY >= 0 ? presetsAboveZero : presetsBelowZero;
    }

    /** Changes at or below this height can uncover hidden blocks. */
    public int maxUpdateY() {
        return maxBlockHeight + updateRadius - 1;
    }

    /** The offsets AntiXray sends again around a change: every block within {@code updateRadius} steps. */
    public int[][] updateOffsets() {
        return updateOffsets;
    }

    private static int[][] offsets(int r) {
        java.util.List<int[]> offsets = new java.util.ArrayList<>();
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= r) {
                        offsets.add(new int[] {dx, dy, dz});
                    }
                }
            }
        }
        return offsets.toArray(new int[0][]);
    }
}
