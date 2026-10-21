package com.arthou.smputilities.client;

import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.network.Dto;
import com.arthou.smputilities.network.Payloads;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the server told this client. Only used to decide what to draw; the
 * server checks permissions again for every request.
 */
public final class ClientState {
    public interface Listener {
        void changed(String what);
    }

    private static long permissions;
    private static boolean realNames;
    private static final Set<UUID> VANISHED = new HashSet<>();
    private static final Map<UUID, String> NICKNAMES = new HashMap<>();
    private static final Map<UUID, String> REAL_NAMES = new HashMap<>();
    private static boolean showAboveHead;
    private static boolean modSync;

    private static Dto.Dashboard dashboard;
    private static final Map<UUID, Dto.PlayerDetail> DETAILS = new HashMap<>();
    private static final Map<String, Dto.Table> TABLES = new HashMap<>();
    private static final Map<String, Dto.Table> LATEST_BY_KIND = new HashMap<>();
    private static List<Dto.Setting> settings = List.of();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private ClientState() {
    }

    public static void reset() {
        permissions = 0;
        realNames = false;
        VANISHED.clear();
        NICKNAMES.clear();
        REAL_NAMES.clear();
        dashboard = null;
        DETAILS.clear();
        TABLES.clear();
        LATEST_BY_KIND.clear();
        settings = List.of();
    }

    static void apply(Payloads.State state) {
        permissions = state.permissions();
        realNames = state.realNames();
        VANISHED.clear();
        VANISHED.addAll(state.vanished());
        modSync = state.modSyncEnabled();
        fire("state");
    }

    static void apply(Payloads.Nicknames nicknames) {
        NICKNAMES.clear();
        NICKNAMES.putAll(nicknames.nicknames());
        REAL_NAMES.clear();
        REAL_NAMES.putAll(nicknames.realNames());
        showAboveHead = nicknames.showAboveHead();
        fire("nicknames");
    }

    static void dashboard(Dto.Dashboard value) {
        dashboard = value;
        fire("dashboard");
    }

    static void detail(Dto.PlayerDetail value) {
        DETAILS.put(value.id(), value);
        fire("detail");
    }

    static void table(Dto.Table value) {
        TABLES.put(value.kind() + "|" + value.context(), value);
        LATEST_BY_KIND.put(value.kind(), value);
        fire("table:" + value.kind());
    }

    static void settings(List<Dto.Setting> value) {
        settings = value;
        fire("settings");
    }

    public static boolean has(Perm perm) {
        return perm.ordinal() < 64 && (permissions & (1L << perm.ordinal())) != 0;
    }

    public static boolean realNames() {
        return realNames;
    }

    public static boolean isVanished(UUID id) {
        return VANISHED.contains(id);
    }

    public static String nickname(UUID id) {
        return showAboveHead ? NICKNAMES.get(id) : null;
    }

    public static String realName(UUID id) {
        return REAL_NAMES.get(id);
    }

    public static boolean modSync() {
        return modSync;
    }

    public static Dto.Dashboard dashboard() {
        return dashboard;
    }

    public static Dto.PlayerDetail detail(UUID id) {
        return DETAILS.get(id);
    }

    public static Dto.Table table(String kind, String context) {
        return TABLES.get(kind + "|" + context);
    }

    /** The last table of a kind, whatever its context (the portals table puts its limit summary there). */
    public static Dto.Table latestTable(String kind) {
        return LATEST_BY_KIND.get(kind);
    }

    public static List<Dto.Setting> settings() {
        return settings;
    }

    public static void listen(Listener listener) {
        LISTENERS.add(listener);
    }

    public static void unlisten(Listener listener) {
        LISTENERS.remove(listener);
    }

    private static void fire(String what) {
        for (Listener listener : LISTENERS) {
            listener.changed(what);
        }
    }
}
