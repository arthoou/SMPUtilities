package com.arthou.smputilities.server;

import com.arthou.smputilities.core.perm.Perm;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Permission answers per player, kept for one second. Hot paths (entity
 * tracking for vanish, chat fan-out) ask the same question many times per
 * tick; permission plugins are only consulted once per second per node.
 */
public final class PermCache {
    private static final Map<UUID, Map<Perm, Boolean>> CACHE = new ConcurrentHashMap<>();

    private PermCache() {
    }

    static boolean get(UUID player, Perm perm, BooleanSupplier compute) {
        Map<Perm, Boolean> answers = CACHE.computeIfAbsent(player, ignored -> new EnumMap<>(Perm.class));
        synchronized (answers) {
            Boolean cached = answers.get(perm);
            if (cached != null) {
                return cached;
            }
        }
        boolean value = compute.getAsBoolean();
        synchronized (answers) {
            answers.put(perm, value);
        }
        return value;
    }

    /** Called every second, so permission changes apply within a second. */
    public static void clear() {
        CACHE.clear();
    }

    public static void forget(UUID player) {
        CACHE.remove(player);
    }
}
