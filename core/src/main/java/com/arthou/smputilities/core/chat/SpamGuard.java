package com.arthou.smputilities.core.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player cooldown, rate limit and duplicate detection for chat. */
public final class SpamGuard {
    public enum Verdict { OK, COOLDOWN, RATE_LIMIT, DUPLICATE }

    public record Result(Verdict verdict, long waitMillis) {
        public boolean allowed() {
            return verdict == Verdict.OK;
        }
    }

    public record Settings(long cooldownMillis, boolean spamEnabled, int maxMessages, long windowMillis, long duplicateMillis) {
    }

    private static final class State {
        final Deque<Long> times = new ArrayDeque<>();
        boolean hasLast;
        long last;
        String lastText = "";
        long lastTextAt;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public Result check(UUID player, String message, Settings settings, long now) {
        State state = states.computeIfAbsent(player, ignored -> new State());
        synchronized (state) {
            if (settings.cooldownMillis() > 0 && state.hasLast) {
                long wait = state.last + settings.cooldownMillis() - now;
                if (wait > 0) {
                    return new Result(Verdict.COOLDOWN, wait);
                }
            }
            if (settings.spamEnabled()) {
                String normalized = normalize(message);
                if (settings.duplicateMillis() > 0 && normalized.equals(state.lastText) && now - state.lastTextAt < settings.duplicateMillis()) {
                    return new Result(Verdict.DUPLICATE, state.lastTextAt + settings.duplicateMillis() - now);
                }
                while (!state.times.isEmpty() && now - state.times.peekFirst() > settings.windowMillis()) {
                    state.times.pollFirst();
                }
                if (settings.maxMessages() > 0 && state.times.size() >= settings.maxMessages()) {
                    return new Result(Verdict.RATE_LIMIT, state.times.peekFirst() + settings.windowMillis() - now);
                }
                state.times.addLast(now);
                state.lastText = normalized;
                state.lastTextAt = now;
            }
            state.hasLast = true;
            state.last = now;
            return new Result(Verdict.OK, 0);
        }
    }

    public void forget(UUID player) {
        states.remove(player);
    }

    private static String normalize(String message) {
        return message == null ? "" : message.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Lowercases a message whose letters are mostly uppercase. */
    public static String limitCaps(String message, double ratio) {
        if (ratio <= 0 || message == null || message.length() < 6) {
            return message;
        }
        int letters = 0;
        int upper = 0;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    upper++;
                }
            }
        }
        return letters >= 6 && (double) upper / letters > ratio ? message.toLowerCase(Locale.ROOT) : message;
    }
}
