package com.arthou.smputilities.core.teleport;

/** A position in a dimension, stored by dimension id ("minecraft:overworld"). */
public record Location(String dimension, double x, double y, double z, float yaw, float pitch) {
    public double distanceSquared(Location other) {
        if (other == null || !dimension.equals(other.dimension)) {
            return Double.MAX_VALUE;
        }
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public String shortText() {
        return String.format(java.util.Locale.ROOT, "%s %.0f %.0f %.0f", dimension, x, y, z);
    }
}
