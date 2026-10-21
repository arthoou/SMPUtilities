package com.arthou.smputilities.network;

import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.modsync.ModManifest;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.server.Names;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.staff.StaffService;
import com.arthou.smputilities.tts.TtsService;
import com.arthou.smputilities.vanish.VanishService;
import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.platform.Platform;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SMPutilities' channel and the send helpers used by the server systems.
 * The channel is optional, and nothing is sent to a client that did not
 * announce it, so players without the mod connect normally. Each loader moves
 * the bytes; framing and dispatch are shared.
 */
public final class SuNetwork {
    /** The channel id, "smputilities:main". */
    public static final String CHANNEL_PATH = "main";
    /** Clients reject messages from a server speaking another protocol version. */
    public static final String PROTOCOL = "2";
    /** Serverbound messages larger than this are dropped (vanilla allows 32 KiB). */
    private static final int MAX_SERVERBOUND = 32 * 1024;

    private SuNetwork() {
    }

    /** Encodes a message: varint id, then the fields. */
    public static byte[] encode(Payloads.Message message) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(message.id());
            message.write(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            buf.release();
        }
    }

    /** Called by the loader when a client message arrives, on any thread. */
    public static void receive(ServerPlayer player, byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_SERVERBOUND) {
            return;
        }
        MinecraftServer server = Mc.server(player);
        server.execute(() -> {
            Payloads.Message message;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
            try {
                message = Payloads.readServerbound(buf.readVarInt(), buf);
            } catch (RuntimeException e) {
                SuLog.warn("Dropped a malformed SMPutilities message from " + Mc.name(player), e);
                return;
            }
            if (message == null || player.hasDisconnected()) {
                return;
            }
            if (message instanceof Payloads.Request request) {
                StaffService.handleRequest(player, request);
            } else if (message instanceof Payloads.Action action) {
                StaffService.handleAction(player, action);
            } else if (message instanceof Payloads.TtsSelect select) {
                TtsService.select(player, select.mode(), select.language());
            }
        });
    }

    /** Sends when the client understands SMPutilities' channel. @return whether it was sent */
    public static boolean send(ServerPlayer player, Payloads.Message message) {
        if (!hasMod(player)) {
            return false;
        }
        Platform.get().send(player, encode(message));
        return true;
    }

    public static boolean hasMod(ServerPlayer player) {
        return player.connection != null && Platform.get().canSend(player);
    }

    // ------------------------------------------------------------ state sync

    public static long permissionMask(ServerPlayer player) {
        long mask = 0;
        for (Perm perm : Perm.values()) {
            // The mask has room for 64 nodes; Perm stays below that (checked in CoreLogicTest).
            if (perm.ordinal() < 64 && Perms.has(player, perm)) {
                mask |= 1L << perm.ordinal();
            }
        }
        return mask;
    }

    public static void syncState(ServerPlayer player) {
        SuServer context = SuServer.get();
        if (context == null || !hasMod(player)) {
            return;
        }
        boolean seesVanished = Perms.has(player, Perm.STAFF_VANISH_SEE);
        List<UUID> vanished = seesVanished ? new ArrayList<>(VanishService.vanished()) : List.of();
        boolean realNames = context.players().get(player.getUUID()).realNames && Perms.has(player, Perm.STAFF_REALNAMES);
        send(player, new Payloads.State(permissionMask(player), realNames, vanished, SuServer.config().features.modSync));
    }

    public static void syncStateToStaff(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncState(player);
        }
    }

    /**
     * Nicknames of the players online: clients only draw names above players
     * they can see, so offline records are never sent.
     */
    private static Map<UUID, String> onlineNicknames(MinecraftServer server) {
        Map<UUID, String> nicks = new HashMap<>();
        SuServer context = SuServer.get();
        if (context == null || !SuServer.config().features.nicknames) {
            return nicks;
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            String nick = Names.nickname(online.getUUID());
            if (!nick.isEmpty()) {
                nicks.put(online.getUUID(), nick);
            }
        }
        return nicks;
    }

    public static void syncNicknames(ServerPlayer player) {
        syncNicknames(player, onlineNicknames(Mc.server(player)));
    }

    private static void syncNicknames(ServerPlayer player, Map<UUID, String> nicks) {
        SuServer context = SuServer.get();
        if (context == null || !hasMod(player)) {
            return;
        }
        SuConfig config = SuServer.config();
        Map<UUID, String> real = new HashMap<>();
        boolean showReal = context.players().get(player.getUUID()).realNames && Perms.has(player, Perm.STAFF_REALNAMES);
        if (showReal) {
            for (ServerPlayer online : Mc.server(player).getPlayerList().getPlayers()) {
                if (nicks.containsKey(online.getUUID())) {
                    real.put(online.getUUID(), Mc.name(online));
                }
            }
        }
        boolean aboveHead = config.features.nicknames && config.nicknames.showAboveHead;
        send(player, new Payloads.Nicknames(aboveHead ? nicks : Map.of(), real, aboveHead));
    }

    public static void syncNicknamesToAll(MinecraftServer server) {
        Map<UUID, String> nicks = onlineNicknames(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncNicknames(player, nicks);
        }
    }

    // --------------------------------------------------------------- helpers

    public static void feedback(ServerPlayer player, String message, boolean error) {
        send(player, new Payloads.Feedback(message, error));
    }

    public static boolean speak(ServerPlayer player, String text, String voice, String pitch) {
        return send(player, new Payloads.TtsSpeak(text, voice, pitch));
    }

    public static boolean openTtsMenu(ServerPlayer player, int mode, int language) {
        return send(player, new Payloads.TtsMenu(mode, language));
    }

    public static void bubble(ServerPlayer viewer, UUID speaker, String text, boolean tts) {
        SuConfig.Tts config = SuServer.config().tts;
        send(viewer, new Payloads.Bubble(speaker, text, tts, (float) config.bubbleSeconds, (float) config.bubbleFadeSeconds,
            config.bubbleMaxWidth, config.bubbleTextColor, config.bubbleFillColor, config.bubbleBorderColor, config.bubbleStyle));
    }

    public static void sendVoiceRestriction(ServerPlayer player, boolean forceMute) {
        send(player, new Payloads.VoiceRestriction(forceMute));
    }

    public static void sendManifest(ServerPlayer player, ModManifest manifest, List<String> allowedHosts, long maxBytes) {
        send(player, new Payloads.Manifest(manifest, allowedHosts, maxBytes));
    }
}
