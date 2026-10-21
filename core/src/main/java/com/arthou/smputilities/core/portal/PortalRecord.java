package com.arthou.smputilities.core.portal;

import java.util.UUID;

/** A portal SMPutilities counts against a limit. */
public final class PortalRecord {
    public String id = "";
    /** Limit key: "nether", "end", "aether" or a dimension id. */
    public String key = "";
    /** Dimension the portal stands in. */
    public String dimension = "";
    public int x;
    public int y;
    public int z;
    /** Portal block id checked during validation, e.g. "minecraft:nether_portal". */
    public String block = "";
    public UUID creator;
    public String creatorName = "";
    public long createdAt;

    public boolean isNear(String dimension, int x, int y, int z, int radius) {
        return this.dimension.equals(dimension)
            && Math.abs(this.x - x) <= radius
            && Math.abs(this.y - y) <= radius
            && Math.abs(this.z - z) <= radius;
    }
}
