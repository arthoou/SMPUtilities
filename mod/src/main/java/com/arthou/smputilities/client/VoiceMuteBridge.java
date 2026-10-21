package com.arthou.smputilities.client;

import java.lang.reflect.Method;

/**
 * Mirrors a server-side voice mute in the Simple Voice Chat client (from
 * DUSMP), so the player sees they are muted. The server drops their audio
 * either way; this is only feedback.
 */
public final class VoiceMuteBridge {
    private static Method getPlayerStateManager;
    private static Method isMuted;
    private static Method setMuted;
    private static boolean lookupFailed;
    private static boolean forced;
    private static boolean applied;
    private static boolean mutedBefore;

    private VoiceMuteBridge() {
    }

    static void setForced(boolean value) {
        forced = value;
        apply();
    }

    /** Re-applied every second in case the player unmutes from the voice chat UI. */
    static void tick() {
        if (forced) {
            apply();
        }
    }

    private static void apply() {
        if (lookupFailed || !resolve()) {
            return;
        }
        try {
            Object manager = getPlayerStateManager.invoke(null);
            if (manager == null) {
                return;
            }
            boolean muted = (boolean) isMuted.invoke(manager);
            if (forced) {
                if (!applied) {
                    mutedBefore = muted;
                    applied = true;
                }
                if (!muted) {
                    setMuted.invoke(manager, true);
                }
            } else if (applied) {
                applied = false;
                if (!mutedBefore && muted) {
                    setMuted.invoke(manager, false);
                }
            }
        } catch (ReflectiveOperationException e) {
            lookupFailed = true;
        }
    }

    private static boolean resolve() {
        if (getPlayerStateManager != null) {
            return true;
        }
        try {
            Class<?> clientManager = Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager");
            getPlayerStateManager = clientManager.getMethod("getPlayerStateManager");
            Class<?> stateManager = Class.forName("de.maxhenkel.voicechat.voice.client.ClientPlayerStateManager");
            isMuted = stateManager.getMethod("isMuted");
            setMuted = stateManager.getMethod("setMuted", boolean.class);
            return true;
        } catch (ReflectiveOperationException e) {
            lookupFailed = true;
            return false;
        }
    }
}
