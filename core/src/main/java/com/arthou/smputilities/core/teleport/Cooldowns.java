package com.arthou.smputilities.core.teleport;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player, per-key cooldowns in milliseconds. In memory only. */
public final class Cooldowns {
    private final Map<UUID, Map<String, Long>> until = new ConcurrentHashMap<>();

    /** @return milliseconds left, 0 when ready */
    public long remaining(UUID player, String key, long now) {
        Map<String, Long> map = until.get(player);
        if (map == null) {
            return 0;
        }
        Long end = map.get(key);
        return end == null ? 0 : Math.max(0, end - now);
    }

    public void start(UUID player, String key, long durationMillis, long now) {
        if (durationMillis <= 0) {
            return;
        }
        until.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>()).put(key, now + durationMillis);
    }

    public void clear(UUID player) {
        until.remove(player);
    }
}
