package com.arthou.smputilities.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Plain data sent to the staff panel. Shared by server and client. */
public final class Dto {
    private Dto() {
    }

    // -------------------------------------------------------------- helpers

    public static void writeStrings(FriendlyByteBuf buf, List<String> values) {
        buf.writeVarInt(values.size());
        for (String value : values) {
            buf.writeUtf(value);
        }
    }

    public static List<String> readStrings(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buf.readUtf());
        }
        return values;
    }

    public static void writeMap(FriendlyByteBuf buf, Map<String, String> values) {
        buf.writeVarInt(values.size());
        values.forEach((key, value) -> {
            buf.writeUtf(key);
            buf.writeUtf(value);
        });
    }

    public static Map<String, String> readMap(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            values.put(buf.readUtf(), buf.readUtf());
        }
        return values;
    }

    public static void writeUuidMap(FriendlyByteBuf buf, Map<UUID, String> values) {
        buf.writeVarInt(values.size());
        values.forEach((key, value) -> {
            buf.writeUUID(key);
            buf.writeUtf(value);
        });
    }

    public static Map<UUID, String> readUuidMap(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<UUID, String> values = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            values.put(buf.readUUID(), buf.readUtf());
        }
        return values;
    }

    // ------------------------------------------------------------- dashboard

    public record PlayerRow(UUID id, String name, String nick, int ping, String dimension, String gameMode, float health,
                            boolean vanished, boolean muted, boolean staff) {
        void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name);
            buf.writeUtf(nick);
            buf.writeVarInt(ping);
            buf.writeUtf(dimension);
            buf.writeUtf(gameMode);
            buf.writeFloat(health);
            buf.writeBoolean(vanished);
            buf.writeBoolean(muted);
            buf.writeBoolean(staff);
        }

        static PlayerRow read(FriendlyByteBuf buf) {
            return new PlayerRow(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readUtf(),
                buf.readFloat(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        }
    }

    public record Dashboard(int online, int max, double tps, double mspt, long memoryUsed, long memoryMax, long uptimeMillis,
                            String version, int activeBans, int activeMutes, Map<String, String> flags, List<PlayerRow> players) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(online);
            buf.writeVarInt(max);
            buf.writeDouble(tps);
            buf.writeDouble(mspt);
            buf.writeLong(memoryUsed);
            buf.writeLong(memoryMax);
            buf.writeLong(uptimeMillis);
            buf.writeUtf(version);
            buf.writeVarInt(activeBans);
            buf.writeVarInt(activeMutes);
            writeMap(buf, flags);
            buf.writeVarInt(players.size());
            players.forEach(row -> row.write(buf));
        }

        public static Dashboard read(FriendlyByteBuf buf) {
            int online = buf.readVarInt();
            int max = buf.readVarInt();
            double tps = buf.readDouble();
            double mspt = buf.readDouble();
            long used = buf.readLong();
            long maxMemory = buf.readLong();
            long uptime = buf.readLong();
            String version = buf.readUtf();
            int bans = buf.readVarInt();
            int mutes = buf.readVarInt();
            Map<String, String> flags = readMap(buf);
            int size = buf.readVarInt();
            List<PlayerRow> players = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                players.add(PlayerRow.read(buf));
            }
            return new Dashboard(online, max, tps, mspt, used, maxMemory, uptime, version, bans, mutes, flags, players);
        }
    }

    // ---------------------------------------------------------------- detail

    /**
     * Everything the player screen shows. {@code values} holds the simple
     * fields by key (see StaffService#detail) so new fields do not need a
     * protocol change.
     */
    public record PlayerDetail(UUID id, String name, boolean online, Map<String, String> values, List<String> notes,
                               Map<String, String> customStats, List<String> profiles) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name);
            buf.writeBoolean(online);
            writeMap(buf, values);
            writeStrings(buf, notes);
            writeMap(buf, customStats);
            writeStrings(buf, profiles);
        }

        public static PlayerDetail read(FriendlyByteBuf buf) {
            return new PlayerDetail(buf.readUUID(), buf.readUtf(), buf.readBoolean(), readMap(buf), readStrings(buf), readMap(buf), readStrings(buf));
        }

        public String get(String key) {
            return values.getOrDefault(key, "");
        }
    }

    // ----------------------------------------------------------------- table

    public record Row(String id, List<String> cells) {
    }

    /** Generic list for history, bans, mutes, logs, backups, profiles, portals, dimensions and warps. */
    public record Table(String kind, String context, List<String> columns, List<Row> rows) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(kind);
            buf.writeUtf(context);
            writeStrings(buf, columns);
            buf.writeVarInt(rows.size());
            for (Row row : rows) {
                buf.writeUtf(row.id());
                writeStrings(buf, row.cells());
            }
        }

        public static Table read(FriendlyByteBuf buf) {
            String kind = buf.readUtf();
            String context = buf.readUtf();
            List<String> columns = readStrings(buf);
            int size = buf.readVarInt();
            List<Row> rows = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                rows.add(new Row(buf.readUtf(), readStrings(buf)));
            }
            return new Table(kind, context, columns, rows);
        }
    }

    // -------------------------------------------------------------- settings

    public record Setting(String path, String kind, String value) {
    }

    public static void writeSettings(FriendlyByteBuf buf, List<Setting> settings) {
        buf.writeVarInt(settings.size());
        for (Setting setting : settings) {
            buf.writeUtf(setting.path());
            buf.writeUtf(setting.kind());
            buf.writeUtf(setting.value());
        }
    }

    public static List<Setting> readSettings(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Setting> settings = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            settings.add(new Setting(buf.readUtf(), buf.readUtf(), buf.readUtf()));
        }
        return settings;
    }
}
