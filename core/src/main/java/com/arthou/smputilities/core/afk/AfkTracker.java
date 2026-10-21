package com.arthou.smputilities.core.afk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is away from keyboard. Activity is chat (from any thread) or looking
 * around / walking, sampled once per second on the server thread. A water
 * stream or a minecart moves a player without them being there, so only
 * walking on the ground counts as movement.
 */
public final class AfkTracker {
    public enum Change { NONE, BECAME_AFK, RETURNED, KICK }

    /** @param autoMillis idle time before becoming AFK (0 = only /afk); @param kickMillis idle time before a kick (0 = never) */
    public record Settings(long autoMillis, long kickMillis) {
    }

    private static final float LOOK_EPSILON = 0.5F;
    private static final double WALK_EPSILON_SQR = 0.01;

    private static final class State {
        volatile long lastActivity;
        boolean afk;
        boolean manual;
        long since;
        String reason = "";
        boolean sampled;
        float yaw;
        float pitch;
        double x;
        double z;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    private State state(UUID id, long now) {
        return states.computeIfAbsent(id, ignored -> {
            State created = new State();
            created.lastActivity = now;
            return created;
        });
    }

    public void join(UUID id, long now) {
        states.remove(id);
        state(id, now);
    }

    public void forget(UUID id) {
        states.remove(id);
    }

    /** Chat or a command: thread-safe, the next sample turns it into RETURNED. */
    public void activity(UUID id, long now) {
        state(id, now).lastActivity = now;
    }

    /**
     * One sample of the player's look and position.
     *
     * @param walking whether horizontal movement may count (on the ground, not riding)
     */
    public synchronized Change sample(UUID id, float yaw, float pitch, double x, double z, boolean walking, long now, Settings settings) {
        State state = state(id, now);
        boolean moved = false;
        if (state.sampled) {
            double dx = x - state.x;
            double dz = z - state.z;
            moved = Math.abs(yaw - state.yaw) > LOOK_EPSILON || Math.abs(pitch - state.pitch) > LOOK_EPSILON
                || walking && dx * dx + dz * dz > WALK_EPSILON_SQR;
        }
        state.sampled = true;
        state.yaw = yaw;
        state.pitch = pitch;
        state.x = x;
        state.z = z;
        if (moved) {
            state.lastActivity = now;
        }
        if (state.afk && state.lastActivity > state.since) {
            state.afk = false;
            state.manual = false;
            state.reason = "";
            return Change.RETURNED;
        }
        long idle = now - state.lastActivity;
        if (!state.afk && settings.autoMillis() > 0 && idle >= settings.autoMillis()) {
            state.afk = true;
            state.manual = false;
            state.since = now;
            state.reason = "";
            return Change.BECAME_AFK;
        }
        if (state.afk && settings.kickMillis() > 0 && idle >= settings.kickMillis()) {
            return Change.KICK;
        }
        return Change.NONE;
    }

    /** /afk. @return true when the state changed */
    public synchronized boolean set(UUID id, boolean afk, String reason, long now) {
        State state = state(id, now);
        if (state.afk == afk) {
            if (afk) {
                state.reason = reason == null ? "" : reason;
            }
            return false;
        }
        state.afk = afk;
        state.manual = afk;
        state.since = now;
        state.reason = afk && reason != null ? reason : "";
        if (!afk) {
            state.lastActivity = now;
        }
        return true;
    }

    public boolean isAfk(UUID id) {
        State state = states.get(id);
        return state != null && state.afk;
    }

    public boolean isManual(UUID id) {
        State state = states.get(id);
        return state != null && state.afk && state.manual;
    }

    /** When the player became AFK, or 0. */
    public long since(UUID id) {
        State state = states.get(id);
        return state != null && state.afk ? state.since : 0;
    }

    public String reason(UUID id) {
        State state = states.get(id);
        return state != null && state.afk ? state.reason : "";
    }
}
