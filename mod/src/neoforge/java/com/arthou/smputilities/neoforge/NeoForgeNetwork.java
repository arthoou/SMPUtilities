package com.arthou.smputilities.neoforge;

import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.network.SuNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
//#if MC>=1.20.6
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
//#if MC<1.21.6
//$$ import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
//#endif
//#else
//$$ import net.minecraft.resources.ResourceLocation;
//$$ import net.neoforged.neoforge.network.PacketDistributor;
//$$ import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
//$$ import net.neoforged.neoforge.network.registration.IPayloadRegistrar;
//#endif

/** SMPutilities' channel on NeoForge: one optional payload that carries the message bytes. */
public final class NeoForgeNetwork {
    private NeoForgeNetwork() {
    }

    static byte[] readAll(FriendlyByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    //#if MC>=1.20.6
    public record SuPayload(byte[] data) implements CustomPacketPayload {
        public static final Type<SuPayload> TYPE = new Type<>(SmpUtilities.id(SuNetwork.CHANNEL_PATH));
        public static final StreamCodec<FriendlyByteBuf, SuPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.data()), buf -> new SuPayload(readAll(buf)));

        @Override
        public Type<SuPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(SuNetwork.PROTOCOL).optional();
        //#if MC>=1.21.6
        // The client handler is registered by NeoForgeClient (RegisterClientPayloadHandlersEvent).
        registrar.playBidirectional(SuPayload.TYPE, SuPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                SuNetwork.receive(player, payload.data());
            }
        });
        //#else
        //$$ registrar.playBidirectional(SuPayload.TYPE, SuPayload.CODEC, new DirectionalPayloadHandler<>(
        //$$     (payload, context) -> NeoForgeClient.receive(payload.data()),
        //$$     (payload, context) -> {
        //$$         if (context.player() instanceof ServerPlayer player) {
        //$$             SuNetwork.receive(player, payload.data());
        //$$         }
        //$$     }));
        //#endif
    }

    static boolean canSend(ServerPlayer player) {
        return player.connection.hasChannel(SuPayload.TYPE);
    }

    static void send(ServerPlayer player, byte[] bytes) {
        player.connection.send(new SuPayload(bytes));
    }
    //#else
    //$$ public record SuPayload(byte[] data) implements CustomPacketPayload {
    //$$     public static final ResourceLocation ID = SmpUtilities.id(SuNetwork.CHANNEL_PATH);
    //$$
    //$$     static SuPayload read(FriendlyByteBuf buf) {
    //$$         return new SuPayload(readAll(buf));
    //$$     }
    //$$
    //$$     @Override
    //$$     public void write(FriendlyByteBuf buf) {
    //$$         buf.writeBytes(data);
    //$$     }
    //$$
    //$$     @Override
    //$$     public ResourceLocation id() {
    //$$         return ID;
    //$$     }
    //$$ }
    //$$
    //$$ static void register(RegisterPayloadHandlerEvent event) {
    //$$     IPayloadRegistrar registrar = event.registrar(SmpUtilities.MOD_ID).versioned(SuNetwork.PROTOCOL).optional();
    //$$     registrar.play(SuPayload.ID, SuPayload::read, handler -> handler
    //$$         .client((payload, context) -> NeoForgeClient.receive(payload.data()))
    //$$         .server((payload, context) -> context.player().ifPresent(player -> {
    //$$             if (player instanceof ServerPlayer serverPlayer) {
    //$$                 SuNetwork.receive(serverPlayer, payload.data());
    //$$             }
    //$$         })));
    //$$ }
    //$$
    //$$ static boolean canSend(ServerPlayer player) {
    //$$     return player.connection.isConnected(SuPayload.ID);
    //$$ }
    //$$
    //$$ static void send(ServerPlayer player, byte[] bytes) {
    //$$     PacketDistributor.PLAYER.with(player).send(new SuPayload(bytes));
    //$$ }
    //#endif
}
