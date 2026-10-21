package com.arthou.smputilities.core.perm;

import com.arthou.smputilities.core.config.SuConfig;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The built-in fallback used when no permission plugin decides a node:
 * explicit grants in the config, then the configured or default op level.
 */
public final class PermissionRules {
    private PermissionRules() {
    }

    public static int requiredLevel(SuConfig config, Perm perm) {
        Integer override = config.permissions.levelOverrides.get(perm.node());
        if (override == null) {
            return perm.defaultLevel();
        }
        return Math.max(0, Math.min(4, override));
    }

    public static boolean isGranted(SuConfig config, Perm perm, UUID playerId, String playerName) {
        List<String> grants = config.permissions.grants.get(perm.node());
        if (grants == null || grants.isEmpty()) {
            return false;
        }
        String id = playerId == null ? "" : playerId.toString();
        String name = playerName == null ? "" : playerName.toLowerCase(Locale.ROOT);
        for (String grant : grants) {
            String value = grant.trim();
            if (value.equalsIgnoreCase(id) || value.toLowerCase(Locale.ROOT).equals(name) || value.equals("*")) {
                return true;
            }
        }
        return false;
    }

    public static boolean fallback(SuConfig config, Perm perm, UUID playerId, String playerName, int opLevel) {
        return isGranted(config, perm, playerId, playerName) || opLevel >= requiredLevel(config, perm);
    }
}
