package com.arthou.smputilities.network;

import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.core.modsync.ModEntry;
import com.arthou.smputilities.core.modsync.ModManifest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Every custom packet SMPutilities sends. All are optional: vanilla clients never receive them. */
public final class Payloads {
    private Payloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(SmpUtilities.id(path));
    }

    // =========================================================== to client

    /** Which panel features this player may use, the real-names toggle and who is vanished. */
    public record State(long permissions, boolean realNames, List<UUID> vanished, boolean modSyncEnabled) implements CustomPacketPayload {
        public static final Type<State> TYPE = payloadType("state");
        public static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeLong(value.permissions);
            buf.writeBoolean(value.realNames);
            buf.writeVarInt(value.vanished.size());
            value.vanished.forEach(buf::writeUUID);
            buf.writeBoolean(value.modSyncEnabled);
        }, buf -> {
            long permissions = buf.readLong();
            boolean realNames = buf.readBoolean();
            int size = buf.readVarInt();
            List<UUID> vanished = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                vanished.add(buf.readUUID());
            }
            return new State(permissions, realNames, vanished, buf.readBoolean());
        });

        @Override
        public Type<State> type() {
            return TYPE;
        }
    }

    /** Nicknames for nametags; real names only for staff who turned real names on. */
    public record Nicknames(Map<UUID, String> nicknames, Map<UUID, String> realNames, boolean showAboveHead) implements CustomPacketPayload {
        public static final Type<Nicknames> TYPE = payloadType("nicknames");
        public static final StreamCodec<FriendlyByteBuf, Nicknames> CODEC = StreamCodec.of((buf, value) -> {
            Dto.writeUuidMap(buf, value.nicknames);
            Dto.writeUuidMap(buf, value.realNames);
            buf.writeBoolean(value.showAboveHead);
        }, buf -> new Nicknames(Dto.readUuidMap(buf), Dto.readUuidMap(buf), buf.readBoolean()));

        @Override
        public Type<Nicknames> type() {
            return TYPE;
        }
    }

    public record OpenPanel(String tab, String target) implements CustomPacketPayload {
        public static final Type<OpenPanel> TYPE = payloadType("open_panel");
        public static final StreamCodec<FriendlyByteBuf, OpenPanel> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeUtf(value.tab);
            buf.writeUtf(value.target);
        }, buf -> new OpenPanel(buf.readUtf(), buf.readUtf()));

        @Override
        public Type<OpenPanel> type() {
            return TYPE;
        }
    }

    public record DashboardData(Dto.Dashboard data) implements CustomPacketPayload {
        public static final Type<DashboardData> TYPE = payloadType("dashboard");
        public static final StreamCodec<FriendlyByteBuf, DashboardData> CODEC = StreamCodec.of((buf, value) -> value.data.write(buf),
            buf -> new DashboardData(Dto.Dashboard.read(buf)));

        @Override
        public Type<DashboardData> type() {
            return TYPE;
        }
    }

    public record DetailData(Dto.PlayerDetail data) implements CustomPacketPayload {
        public static final Type<DetailData> TYPE = payloadType("player_detail");
        public static final StreamCodec<FriendlyByteBuf, DetailData> CODEC = StreamCodec.of((buf, value) -> value.data.write(buf),
            buf -> new DetailData(Dto.PlayerDetail.read(buf)));

        @Override
        public Type<DetailData> type() {
            return TYPE;
        }
    }

    public record TableData(Dto.Table data) implements CustomPacketPayload {
        public static final Type<TableData> TYPE = payloadType("table");
        public static final StreamCodec<FriendlyByteBuf, TableData> CODEC = StreamCodec.of((buf, value) -> value.data.write(buf),
            buf -> new TableData(Dto.Table.read(buf)));

        @Override
        public Type<TableData> type() {
            return TYPE;
        }
    }

    public record SettingsData(List<Dto.Setting> settings) implements CustomPacketPayload {
        public static final Type<SettingsData> TYPE = payloadType("settings");
        public static final StreamCodec<FriendlyByteBuf, SettingsData> CODEC = StreamCodec.of((buf, value) -> Dto.writeSettings(buf, value.settings),
            buf -> new SettingsData(Dto.readSettings(buf)));

        @Override
        public Type<SettingsData> type() {
            return TYPE;
        }
    }

    /** Result of a panel action, shown as a toast inside the panel. */
    public record Feedback(String message, boolean error) implements CustomPacketPayload {
        public static final Type<Feedback> TYPE = payloadType("feedback");
        public static final StreamCodec<FriendlyByteBuf, Feedback> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeUtf(value.message);
            buf.writeBoolean(value.error);
        }, buf -> new Feedback(buf.readUtf(), buf.readBoolean()));

        @Override
        public Type<Feedback> type() {
            return TYPE;
        }
    }

    public record TtsSpeak(String text, String voice, String pitch) implements CustomPacketPayload {
        public static final Type<TtsSpeak> TYPE = payloadType("tts_speak");
        public static final StreamCodec<FriendlyByteBuf, TtsSpeak> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeUtf(value.text);
            buf.writeUtf(value.voice);
            buf.writeUtf(value.pitch);
        }, buf -> new TtsSpeak(buf.readUtf(), buf.readUtf(), buf.readUtf()));

        @Override
        public Type<TtsSpeak> type() {
            return TYPE;
        }
    }

    public record TtsMenu(int mode, int language) implements CustomPacketPayload {
        public static final Type<TtsMenu> TYPE = payloadType("tts_menu");
        public static final StreamCodec<FriendlyByteBuf, TtsMenu> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeVarInt(value.mode);
            buf.writeVarInt(value.language);
        }, buf -> new TtsMenu(buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<TtsMenu> type() {
            return TYPE;
        }
    }

    /** A speech bubble with the server's look settings, so every client draws it the same way. */
    public record Bubble(UUID speaker, String text, boolean tts, float seconds, float fadeSeconds, int maxWidth, int textColor,
                         int fillColor, int borderColor, String style) implements CustomPacketPayload {
        public static final Type<Bubble> TYPE = payloadType("bubble");
        public static final StreamCodec<FriendlyByteBuf, Bubble> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeUUID(value.speaker);
            buf.writeUtf(value.text);
            buf.writeBoolean(value.tts);
            buf.writeFloat(value.seconds);
            buf.writeFloat(value.fadeSeconds);
            buf.writeVarInt(value.maxWidth);
            buf.writeInt(value.textColor);
            buf.writeInt(value.fillColor);
            buf.writeInt(value.borderColor);
            buf.writeUtf(value.style);
        }, buf -> new Bubble(buf.readUUID(), buf.readUtf(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readVarInt(),
            buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf()));

        @Override
        public Type<Bubble> type() {
            return TYPE;
        }
    }

    public record VoiceRestriction(boolean forceMute) implements CustomPacketPayload {
        public static final Type<VoiceRestriction> TYPE = payloadType("voice_restriction");
        public static final StreamCodec<FriendlyByteBuf, VoiceRestriction> CODEC = StreamCodec.of((buf, value) -> buf.writeBoolean(value.forceMute),
            buf -> new VoiceRestriction(buf.readBoolean()));

        @Override
        public Type<VoiceRestriction> type() {
            return TYPE;
        }
    }

    public record Manifest(ModManifest manifest, List<String> allowedHosts, long maxBytes) implements CustomPacketPayload {
        public static final Type<Manifest> TYPE = payloadType("modsync_manifest");
        public static final StreamCodec<FriendlyByteBuf, Manifest> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeUtf(value.manifest.hostBase());
            buf.writeUtf(value.manifest.token());
            buf.writeVarInt(value.manifest.entries().size());
            for (ModEntry entry : value.manifest.entries()) {
                buf.writeUtf(entry.fileName());
                buf.writeUtf(entry.sha512());
                buf.writeLong(entry.size());
                buf.writeUtf(entry.url() == null ? "" : entry.url());
                buf.writeUtf(entry.credential() == null ? "" : entry.credential());
                buf.writeBoolean(entry.required());
            }
            Dto.writeStrings(buf, value.allowedHosts);
            buf.writeLong(value.maxBytes);
        }, buf -> {
            String host = buf.readUtf();
            String token = buf.readUtf();
            int size = Math.min(buf.readVarInt(), 4096);
            List<ModEntry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                entries.add(new ModEntry(buf.readUtf(), buf.readUtf(), buf.readLong(), buf.readUtf(), buf.readUtf(), buf.readBoolean()));
            }
            return new Manifest(new ModManifest(host, token, entries), Dto.readStrings(buf), buf.readLong());
        });

        @Override
        public Type<Manifest> type() {
            return TYPE;
        }
    }

    // =========================================================== to server

    /** Panel data request: "dashboard", "player" (uuid), "table" (kind|context), "settings". */
    public record Request(String kind, String argument) implements CustomPacketPayload {
        public static final Type<Request> TYPE = payloadType("request");
        public static final StreamCodec<FriendlyByteBuf, Request> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeUtf(value.kind, 64);
            buf.writeUtf(value.argument, 256);
        }, buf -> new Request(buf.readUtf(64), buf.readUtf(256)));

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /** A panel action; the server checks the permission for every one. */
    public record Action(String action, String target, List<String> arguments) implements CustomPacketPayload {
        public static final Type<Action> TYPE = payloadType("action");
        public static final StreamCodec<FriendlyByteBuf, Action> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeUtf(value.action, 64);
            buf.writeUtf(value.target, 64);
            buf.writeVarInt(value.arguments.size());
            for (String argument : value.arguments) {
                buf.writeUtf(argument, 4096);
            }
        }, buf -> {
            String action = buf.readUtf(64);
            String target = buf.readUtf(64);
            int size = Math.min(buf.readVarInt(), 16);
            List<String> arguments = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                arguments.add(buf.readUtf(4096));
            }
            return new Action(action, target, arguments);
        });

        @Override
        public Type<Action> type() {
            return TYPE;
        }
    }

    public record TtsSelect(int mode, int language) implements CustomPacketPayload {
        public static final Type<TtsSelect> TYPE = payloadType("tts_select");
        public static final StreamCodec<FriendlyByteBuf, TtsSelect> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeVarInt(value.mode);
            buf.writeVarInt(value.language);
        }, buf -> new TtsSelect(buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<TtsSelect> type() {
            return TYPE;
        }
    }
}
