package com.arthou.smputilities.core.teleport;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pending /tpa and /tpahere requests. Each target keeps its requests in
 * arrival order; /tpaccept without a name takes the newest one.
 */
public final class TpaRequests {
    public enum Kind { TO_TARGET, HERE }

    public record Request(UUID requester, String requesterName, UUID target, Kind kind, long createdAt) {
    }

    private final Map<UUID, LinkedHashMap<UUID, Request>> byTarget = new LinkedHashMap<>();

    public synchronized void add(Request request) {
        byTarget.computeIfAbsent(request.target(), ignored -> new LinkedHashMap<>()).remove(request.requester());
        byTarget.get(request.target()).put(request.requester(), request);
    }

    public synchronized Optional<Request> take(UUID target, UUID requester, long now, long timeoutMillis) {
        prune(now, timeoutMillis);
        LinkedHashMap<UUID, Request> requests = byTarget.get(target);
        if (requests == null || requests.isEmpty()) {
            return Optional.empty();
        }
        Request request;
        if (requester != null) {
            request = requests.remove(requester);
        } else {
            List<UUID> keys = new ArrayList<>(requests.keySet());
            request = requests.remove(keys.get(keys.size() - 1));
        }
        return Optional.ofNullable(request);
    }

    public synchronized List<Request> pending(UUID target, long now, long timeoutMillis) {
        prune(now, timeoutMillis);
        LinkedHashMap<UUID, Request> requests = byTarget.get(target);
        return requests == null ? List.of() : new ArrayList<>(requests.values());
    }

    public synchronized boolean hasPending(UUID requester, UUID target, long now, long timeoutMillis) {
        prune(now, timeoutMillis);
        LinkedHashMap<UUID, Request> requests = byTarget.get(target);
        return requests != null && requests.containsKey(requester);
    }

    /** /tpacancel: withdraws every pending request the player sent. @return the requests withdrawn */
    public synchronized List<Request> cancel(UUID requester, long now, long timeoutMillis) {
        prune(now, timeoutMillis);
        List<Request> cancelled = new ArrayList<>();
        Iterator<LinkedHashMap<UUID, Request>> outer = byTarget.values().iterator();
        while (outer.hasNext()) {
            LinkedHashMap<UUID, Request> requests = outer.next();
            Request removed = requests.remove(requester);
            if (removed != null) {
                cancelled.add(removed);
            }
            if (requests.isEmpty()) {
                outer.remove();
            }
        }
        return cancelled;
    }

    /** Drops everything involving a player who left. */
    public synchronized void forget(UUID player) {
        byTarget.remove(player);
        for (LinkedHashMap<UUID, Request> requests : byTarget.values()) {
            requests.remove(player);
        }
    }

    private void prune(long now, long timeoutMillis) {
        Iterator<LinkedHashMap<UUID, Request>> outer = byTarget.values().iterator();
        while (outer.hasNext()) {
            LinkedHashMap<UUID, Request> requests = outer.next();
            requests.values().removeIf(request -> now - request.createdAt() > timeoutMillis);
            if (requests.isEmpty()) {
                outer.remove();
            }
        }
    }
}
