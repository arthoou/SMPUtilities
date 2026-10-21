package com.arthou.smputilities.network;

import com.arthou.smputilities.core.modsync.ModEntry;
import com.arthou.smputilities.core.modsync.ModManifest;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every message SMPutilities sends. All travel on one optional channel
 * ({@link SuNetwork#CHANNEL}) as a message id followed by the fields, so each
 * loader only has to move bytes. Vanilla clients never receive them.
 */
public final class Payloads {
    private Payloads() {
    }

    /** A message: its id on the channel and how it writes itself. */
    public interface Message {
        int id();

        void write(FriendlyByteBuf buf);
    }

    // Ids. Never reuse a number: old clients would misread it.
    public static final int STATE = 1;
    public static final int NICKNAMES = 2;
    public static final int OPEN_PANEL = 3;
    public static final int DASHBOARD = 4;
    public static final int DETAIL = 5;
    public static final int TABLE = 6;
    public static final int SETTINGS = 7;
    public static final int FEEDBACK = 8;
    public static final int TTS_SPEAK = 9;
    public static final int TTS_MENU = 10;
    public static final int BUBBLE = 11;
    public static final int VOICE_RESTRICTION = 12;
    public static final int MANIFEST = 13;
    public static final int BUBBLE_TYPING = 14;
    public static final int REQUEST = 64;
    public static final int ACTION = 65;
    public static final int TTS_SELECT = 66;
    public static final int TYPING = 67;

    /** Client side: decodes a message sent by the server, or null for unknown ids. */
    public static Message readClientbound(int id, FriendlyByteBuf buf) {
        return switch (id) {
            case STATE -> State.read(buf);
            case NICKNAMES -> Nicknames.read(buf);
            case OPEN_PANEL -> OpenPanel.read(buf);
            case DASHBOARD -> DashboardData.read(buf);
            case DETAIL -> DetailData.read(buf);
            case TABLE -> TableData.read(buf);
            case SETTINGS -> SettingsData.read(buf);
            case FEEDBACK -> Feedback.read(buf);
            case TTS_SPEAK -> TtsSpeak.read(buf);
            case TTS_MENU -> TtsMenu.read(buf);
            case BUBBLE -> Bubble.read(buf);
            case VOICE_RESTRICTION -> VoiceRestriction.read(buf);
            case MANIFEST -> Manifest.read(buf);
            case BUBBLE_TYPING -> BubbleTyping.read(buf);
            default -> null;
        };
    }

    /** Server side: decodes a message sent by a client, or null for unknown ids. */
    public static Message readServerbound(int id, FriendlyByteBuf buf) {
        return switch (id) {
            case REQUEST -> Request.read(buf);
            case ACTION -> Action.read(buf);
            case TTS_SELECT -> TtsSelect.read(buf);
            case TYPING -> Typing.read(buf);
            default -> null;
        };
    }

    // =========================================================== to client

    /** Which panel features this player may use, the real-names toggle and who is vanished. */
    /** @param permissions bit {@code Perm.ordinal()} set for every node the player has */
    public record State(BitSet permissions, boolean realNames, List<UUID> vanished, boolean modSyncEnabled) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            long[] words = permissions.toLongArray();
            buf.writeVarInt(words.length);
            for (long word : words) {
                buf.writeLong(word);
            }
            buf.writeBoolean(realNames);
            buf.writeVarInt(vanished.size());
            vanished.forEach(buf::writeUUID);
            buf.writeBoolean(modSyncEnabled);
        }

        public static State read(FriendlyByteBuf buf) {
            long[] words = new long[Math.min(buf.readVarInt(), 16)];
            for (int i = 0; i < words.length; i++) {
                words[i] = buf.readLong();
            }
            BitSet permissions = BitSet.valueOf(words);
            boolean realNames = buf.readBoolean();
            int size = buf.readVarInt();
            List<UUID> vanished = new ArrayList<>(Math.min(size, 256));
            for (int i = 0; i < size; i++) {
                vanished.add(buf.readUUID());
            }
            return new State(permissions, realNames, vanished, buf.readBoolean());
        }

        @Override
        public int id() {
            return STATE;
        }
    }

    /**
     * Nicknames for nametags; real names only for staff who turned real names on.
     *
     * @param animations nameplate animation ids (HiddenNames' set) of players who have one
     * @param blocksHide nameplates are hidden behind blocks
     * @param teamOverride a team color replaces the nickname's own color
     */
    public record Nicknames(Map<UUID, String> nicknames, Map<UUID, String> realNames, boolean showAboveHead,
                            Map<UUID, String> animations, boolean blocksHide, boolean teamOverride) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            Dto.writeUuidMap(buf, nicknames);
            Dto.writeUuidMap(buf, realNames);
            buf.writeBoolean(showAboveHead);
            Dto.writeUuidMap(buf, animations);
            buf.writeBoolean(blocksHide);
            buf.writeBoolean(teamOverride);
        }

        public static Nicknames read(FriendlyByteBuf buf) {
            return new Nicknames(Dto.readUuidMap(buf), Dto.readUuidMap(buf), buf.readBoolean(), Dto.readUuidMap(buf), buf.readBoolean(),
                buf.readBoolean());
        }

        @Override
        public int id() {
            return NICKNAMES;
        }
    }

    public record OpenPanel(String tab, String target) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(tab);
            buf.writeUtf(target);
        }

        public static OpenPanel read(FriendlyByteBuf buf) {
            return new OpenPanel(buf.readUtf(), buf.readUtf());
        }

        @Override
        public int id() {
            return OPEN_PANEL;
        }
    }

    public record DashboardData(Dto.Dashboard data) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            data.write(buf);
        }

        public static DashboardData read(FriendlyByteBuf buf) {
            return new DashboardData(Dto.Dashboard.read(buf));
        }

        @Override
        public int id() {
            return DASHBOARD;
        }
    }

    public record DetailData(Dto.PlayerDetail data) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            data.write(buf);
        }

        public static DetailData read(FriendlyByteBuf buf) {
            return new DetailData(Dto.PlayerDetail.read(buf));
        }

        @Override
        public int id() {
            return DETAIL;
        }
    }

    public record TableData(Dto.Table data) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            data.write(buf);
        }

        public static TableData read(FriendlyByteBuf buf) {
            return new TableData(Dto.Table.read(buf));
        }

        @Override
        public int id() {
            return TABLE;
        }
    }

    public record SettingsData(List<Dto.Setting> settings) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            Dto.writeSettings(buf, settings);
        }

        public static SettingsData read(FriendlyByteBuf buf) {
            return new SettingsData(Dto.readSettings(buf));
        }

        @Override
        public int id() {
            return SETTINGS;
        }
    }

    /** Result of a panel action, shown as a toast inside the panel. */
    public record Feedback(String message, boolean error) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(message);
            buf.writeBoolean(error);
        }

        public static Feedback read(FriendlyByteBuf buf) {
            return new Feedback(buf.readUtf(), buf.readBoolean());
        }

        @Override
        public int id() {
            return FEEDBACK;
        }
    }

    public record TtsSpeak(String text, String voice, String pitch) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(text);
            buf.writeUtf(voice);
            buf.writeUtf(pitch);
        }

        public static TtsSpeak read(FriendlyByteBuf buf) {
            return new TtsSpeak(buf.readUtf(), buf.readUtf(), buf.readUtf());
        }

        @Override
        public int id() {
            return TTS_SPEAK;
        }
    }

    public record TtsMenu(int mode, int language) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(mode);
            buf.writeVarInt(language);
        }

        public static TtsMenu read(FriendlyByteBuf buf) {
            return new TtsMenu(buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public int id() {
            return TTS_MENU;
        }
    }

    /** A speech bubble with the server's look settings, so every client draws it the same way. */
    public record Bubble(UUID speaker, String text, boolean tts, float seconds, float fadeSeconds, int maxWidth, int textColor,
                         int fillColor, int borderColor, String style) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(speaker);
            buf.writeUtf(text);
            buf.writeBoolean(tts);
            buf.writeFloat(seconds);
            buf.writeFloat(fadeSeconds);
            buf.writeVarInt(maxWidth);
            buf.writeInt(textColor);
            buf.writeInt(fillColor);
            buf.writeInt(borderColor);
            buf.writeUtf(style);
        }

        public static Bubble read(FriendlyByteBuf buf) {
            return new Bubble(buf.readUUID(), buf.readUtf(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readVarInt(),
            buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf());
        }

        @Override
        public int id() {
            return BUBBLE;
        }
    }

    /** A player started or stopped typing (Nexel's "..." bubble); repeated while they type. */
    public record BubbleTyping(UUID speaker, boolean active) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(speaker);
            buf.writeBoolean(active);
        }

        public static BubbleTyping read(FriendlyByteBuf buf) {
            return new BubbleTyping(buf.readUUID(), buf.readBoolean());
        }

        @Override
        public int id() {
            return BUBBLE_TYPING;
        }
    }

    public record VoiceRestriction(boolean forceMute) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(forceMute);
        }

        public static VoiceRestriction read(FriendlyByteBuf buf) {
            return new VoiceRestriction(buf.readBoolean());
        }

        @Override
        public int id() {
            return VOICE_RESTRICTION;
        }
    }

    public record Manifest(ModManifest manifest, List<String> allowedHosts, long maxBytes) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(manifest.hostBase());
            buf.writeUtf(manifest.token());
            buf.writeVarInt(manifest.entries().size());
            for (ModEntry entry : manifest.entries()) {
                buf.writeUtf(entry.fileName());
                buf.writeUtf(entry.sha512());
                buf.writeLong(entry.size());
                buf.writeUtf(entry.url() == null ? "" : entry.url());
                buf.writeUtf(entry.credential() == null ? "" : entry.credential());
                buf.writeBoolean(entry.required());
            }
            Dto.writeStrings(buf, allowedHosts);
            buf.writeLong(maxBytes);
        }

        public static Manifest read(FriendlyByteBuf buf) {
            String host = buf.readUtf();
            String token = buf.readUtf();
            int size = Math.min(buf.readVarInt(), 4096);
            List<ModEntry> entries = new ArrayList<>(Math.min(size, 256));
            for (int i = 0; i < size; i++) {
                entries.add(new ModEntry(buf.readUtf(), buf.readUtf(), buf.readLong(), buf.readUtf(), buf.readUtf(), buf.readBoolean()));
            }
            return new Manifest(new ModManifest(host, token, entries), Dto.readStrings(buf), buf.readLong());
        }

        @Override
        public int id() {
            return MANIFEST;
        }
    }

    // =========================================================== to server

    /** Panel data request: "dashboard", "player" (uuid), "table" (kind|context), "settings". */
    public record Request(String kind, String argument) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(kind, 64);
            buf.writeUtf(argument, 256);
        }

        public static Request read(FriendlyByteBuf buf) {
            return new Request(buf.readUtf(64), buf.readUtf(256));
        }

        @Override
        public int id() {
            return REQUEST;
        }
    }

    /** A panel action; the server checks the permission for every one. */
    public record Action(String action, String target, List<String> arguments) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(action, 64);
            buf.writeUtf(target, 64);
            buf.writeVarInt(arguments.size());
            for (String argument : arguments) {
                buf.writeUtf(argument, 4096);
            }
        }

        public static Action read(FriendlyByteBuf buf) {
            String action = buf.readUtf(64);
            String target = buf.readUtf(64);
            int size = Math.min(buf.readVarInt(), 16);
            List<String> arguments = new ArrayList<>(Math.min(size, 256));
            for (int i = 0; i < size; i++) {
                arguments.add(buf.readUtf(4096));
            }
            return new Action(action, target, arguments);
        }

        @Override
        public int id() {
            return ACTION;
        }
    }

    public record TtsSelect(int mode, int language) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(mode);
            buf.writeVarInt(language);
        }

        public static TtsSelect read(FriendlyByteBuf buf) {
            return new TtsSelect(buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public int id() {
            return TTS_SELECT;
        }
    }

    /** The chat box holds a message (not a command): sent on change and as a heartbeat while typing. */
    public record Typing(boolean active) implements Message {
        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(active);
        }

        public static Typing read(FriendlyByteBuf buf) {
            return new Typing(buf.readBoolean());
        }

        @Override
        public int id() {
            return TYPING;
        }
    }
}
