package com.arthou.smputilities.core.nick;

import java.util.Locale;

/**
 * Nameplate animations, the same set and keys as the HiddenNames mod
 * (hiddennames:null, hidden, breathe, rainbow, cycle, random). Clients with
 * SMPutilities draw them above the player's head; HIDDEN hides the nameplate.
 */
public enum NameAnimation {
    NONE("none"),
    HIDDEN("hidden"),
    BREATHE("breathe"),
    RAINBOW("rainbow"),
    CYCLE("cycle"),
    RANDOM("random");

    private final String id;

    NameAnimation(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Accepts our ids and HiddenNames' keys ("hiddennames:rainbow", "null"). @return null when unknown */
    public static NameAnimation parse(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim().toLowerCase(Locale.ROOT);
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        if (value.equals("null") || value.equals("no_animation") || value.isEmpty()) {
            return NONE;
        }
        for (NameAnimation animation : values()) {
            if (animation.id.equals(value)) {
                return animation;
            }
        }
        return null;
    }
}
