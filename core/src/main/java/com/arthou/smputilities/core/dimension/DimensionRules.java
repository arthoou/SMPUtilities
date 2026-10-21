package com.arthou.smputilities.core.dimension;

import com.arthou.smputilities.core.config.SuConfig;

import java.util.Locale;
import java.util.Map;

/**
 * Access control for dimensions. Blocking only stops new travel: the
 * dimension stays loaded, its data and builds untouched.
 */
public final class DimensionRules {
    private DimensionRules() {
    }

    /** "nether" -> "minecraft:the_nether"; ids without a namespace get "minecraft:". */
    public static String resolve(SuConfig.Dimensions config, String input) {
        String value = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        String alias = config.aliases.get(value);
        if (alias != null) {
            return alias;
        }
        return value.contains(":") ? value : "minecraft:" + value;
    }

    /** Reverse of {@link #resolve} for display: "minecraft:the_nether" -> "nether". */
    public static String shortName(SuConfig.Dimensions config, String id) {
        for (Map.Entry<String, String> entry : config.aliases.entrySet()) {
            if (entry.getValue().equals(id)) {
                return entry.getKey();
            }
        }
        return id;
    }

    public static boolean isBlocked(SuConfig.Dimensions config, String dimensionId) {
        return config.blocked.contains(dimensionId);
    }

    public static boolean setBlocked(SuConfig.Dimensions config, String dimensionId, boolean blocked) {
        if (blocked) {
            if (config.blocked.contains(dimensionId)) {
                return false;
            }
            config.blocked.add(dimensionId);
            return true;
        }
        return config.blocked.remove(dimensionId);
    }

    /** Limit key a portal leading to or standing in this dimension counts against. */
    public static String portalKey(SuConfig.Dimensions config, String dimensionId) {
        return shortName(config, dimensionId);
    }
}
