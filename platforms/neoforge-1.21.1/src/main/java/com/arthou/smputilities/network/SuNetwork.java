package com.arthou.smputilities.network;

import com.arthou.smputilities.client.ClientPayloads;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.modsync.ModManifest;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.nick.NicknameService;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import com.arthou.smputilities.staff.StaffService;
import com.arthou.smputilities.tts.TtsService;
import com.arthou.smputilities.vanish.VanishService;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payload registration and the send helpers used by the server systems.
 * Every payload is optional, and nothing is sent to a client that did not
 * announce the channel, so players without the mod connect normally.
 */
public final class SuNetwork {
    private static final String PROTOCOL = "1";

    private SuNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL).optional();

        // Client handlers live in ClientPayloads; the lambdas only reach it on the client.
        registrar.playToClient(Payloads.State.TYPE, Payloads.State.CODEC, (payload, context) -> context.enqueueWork(() -> ClientPayloads.state(payload)));
        registrar.playToClient(Payloads.Nicknames.TYPE, Payloads.Nicknames.CODEC, (payload, context) -> context.enqueueWork(() -> ClientPayloads.nicknames(payload)));
        registrar.playToClient(Payloads.OpenPanel.TYPE, Payloads.OpenPanel.CODEC, (payload, context) -> context.enqueueWork(() -> ClientPayloads.openPanel(payload)));
        registrar.playToClient(Payloads.DashboardData.TYPE, Payloads.DashboardData.CODEC, (payload, context) -> context.enqueueWork(() -> ClientPayloads.dashboard(payload)));
        registrar.playToClient(Payloads.DetailData.TYPE, Payloads.DetailData.CODEC, (payload, context) -> context.enqueueWork(() -> ClientPayloads.detail(payload)));
        registrar.playToClient(Payloads.TableData.TYPE, Payloads.TableData.CODEC, (payload, context) -> context.enqueueWork(() -> ClientPayloads.table(payload)));
        registrar.playToClient(Payloads.SettingsData.TYPE, Payloads.SettingsData.CODEC, (payload, context) -> context.enqueueWork(() -> ClientPayloads.settings(payload)));
        registrar.playToClient(Payloads.Feedback.TYPE, Payloads.Feedback.CODEC, (payload, context) -> context.enqueueWork(() -> ClientPayloads.feedback(payload)));
        registrar.playToClient(Payloads.TtsSpeak.TYPE, Payloads.TtsSpeak.CODEC, (payload, context) -> context.enqueueWork(() -> ClientPayloads.ttsSpeak(payload)));
        registrar.playToClient(Payloads.TtsMenu.TYPE, Payloads.TtsMenu.CODEC, (payload, context) -> context.enqueueWork(() -> ClientPayloads.ttsMenu(payload)));
        registrar.playToClient(Payloads.Bubble.TYPE, Payloads.Bubble.CODEC, (payload, context) -> context.enqueueWork(() -> ClientPayloads.bubble(payload)));
        registrar.playToClient(Payloads.VoiceRestriction.TYPE, Payloads.VoiceRestriction.CODEC, (payload, context) -> context.enqueueWork(() -> ClientPayloads.voiceRestriction(payload)));
        registrar.playToClient(Payloads.Manifest.TYPE, Payloads.Manifest.CODEC, (payload, context) -> context.enqueueWork(() -> ClientPayloads.manifest(payload)));

        registrar.playToServer(Payloads.Request.TYPE, Payloads.Request.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                StaffService.handleRequest(player, payload);
            }
        }));
        registrar.playToServer(Payloads.Action.TYPE, Payloads.Action.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                StaffService.handleAction(player, payload);
            }
        }));
        registrar.playToServer(Payloads.TtsSelect.TYPE, Payloads.TtsSelect.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                TtsService.select(player, payload.mode(), payload.language());
            }
        }));
    }

    public static boolean has(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection != null && player.connection.hasChannel(type);
    }

    /** Sends when the client understands the payload. @return whether it was sent */
    public static boolean send(ServerPlayer player, CustomPacketPayload payload) {
        if (!has(player, payload.type())) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, payload);
        return true;
    }

    public static boolean hasMod(ServerPlayer player) {
        return has(player, Payloads.State.TYPE);
    }

    // ------------------------------------------------------------ state sync

    public static long permissionMask(ServerPlayer player) {
        long mask = 0;
        for (Perm perm : Perm.values()) {
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

    public static void syncNicknames(ServerPlayer player) {
        SuServer context = SuServer.get();
        if (context == null || !has(player, Payloads.Nicknames.TYPE)) {
            return;
        }
        SuConfig config = SuServer.config();
        Map<UUID, String> nicks = NicknameService.snapshot();
        Map<UUID, String> real = new HashMap<>();
        boolean showReal = context.players().get(player.getUUID()).realNames && Perms.has(player, Perm.STAFF_REALNAMES);
        if (showReal || !config.nicknames.hideRealNames) {
            for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
                if (nicks.containsKey(online.getUUID())) {
                    real.put(online.getUUID(), online.getGameProfile().getName());
                }
            }
        }
        boolean aboveHead = config.features.nicknames && config.nicknames.showAboveHead;
        send(player, new Payloads.Nicknames(aboveHead ? nicks : Map.of(), showReal ? real : Map.of(), aboveHead));
    }

    public static void syncNicknamesToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncNicknames(player);
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
