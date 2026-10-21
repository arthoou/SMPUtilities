package com.arthou.smputilities.fabric;

import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.network.SuNetwork;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
//#if MC>=1.20.5
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#else
//$$ import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
//$$ import net.minecraft.resources.ResourceLocation;
//#endif

/** SMPutilities' channel on Fabric: one optional payload that carries the message bytes. */
public final class FabricNetwork {
    private FabricNetwork() {
    }

    static byte[] readAll(FriendlyByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    //#if MC>=1.20.5
    public record SuPayload(byte[] data) implements CustomPacketPayload {
        public static final Type<SuPayload> TYPE = new Type<>(SmpUtilities.id(SuNetwork.CHANNEL_PATH));
        public static final StreamCodec<FriendlyByteBuf, SuPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.data()), buf -> new SuPayload(readAll(buf)));

        @Override
        public Type<SuPayload> type() {
            return TYPE;
        }
    }

    static void register() {
        PayloadTypeRegistry.playC2S().register(SuPayload.TYPE, SuPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SuPayload.TYPE, SuPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SuPayload.TYPE, (payload, context) -> SuNetwork.receive(context.player(), payload.data()));
    }

    static boolean canSend(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, SuPayload.TYPE);
    }

    static void send(ServerPlayer player, byte[] bytes) {
        ServerPlayNetworking.send(player, new SuPayload(bytes));
    }
    //#else
    //$$ static final ResourceLocation ID = SmpUtilities.id(SuNetwork.CHANNEL_PATH);
    //$$
    //$$ static void register() {
    //$$     ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, sender) -> SuNetwork.receive(player, readAll(buf)));
    //$$ }
    //$$
    //$$ static boolean canSend(ServerPlayer player) {
    //$$     return ServerPlayNetworking.canSend(player, ID);
    //$$ }
    //$$
    //$$ static void send(ServerPlayer player, byte[] bytes) {
    //$$     FriendlyByteBuf buf = PacketByteBufs.create();
    //$$     buf.writeBytes(bytes);
    //$$     ServerPlayNetworking.send(player, ID, buf);
    //$$ }
    //#endif
}
