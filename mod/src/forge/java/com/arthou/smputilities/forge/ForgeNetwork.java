package com.arthou.smputilities.forge;

import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.network.SuNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
//#if MC>=1.20.2
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;
import net.minecraftforge.network.PacketDistributor;
//#else
//$$ import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
//$$ import net.minecraftforge.network.NetworkEvent;
//$$ import net.minecraftforge.network.NetworkRegistry;
//$$ import net.minecraftforge.network.event.EventNetworkChannel;
//#endif

/** SMPutilities' optional channel on Forge, carrying the message bytes. */
public final class ForgeNetwork {
    static final ResourceLocation ID = SmpUtilities.id(SuNetwork.CHANNEL_PATH);
    static EventNetworkChannel channel;

    private ForgeNetwork() {
    }

    static byte[] readAll(FriendlyByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    static FriendlyByteBuf wrap(byte[] bytes) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
    }

    static void register() {
        //#if MC>=1.20.2
        channel = ChannelBuilder.named(ID).optional().networkProtocolVersion(Integer.parseInt(SuNetwork.PROTOCOL)).eventNetworkChannel();
        channel.addListener(event -> {
            FriendlyByteBuf payload = event.getPayload();
            if (payload == null) {
                return;
            }
            byte[] bytes = readAll(payload);
            if (event.getSource().isServerSide()) {
                ServerPlayer sender = event.getSource().getSender();
                if (sender != null) {
                    SuNetwork.receive(sender, bytes);
                }
            } else {
                ForgeClient.receive(bytes);
            }
            event.getSource().setPacketHandled(true);
        });
        //#else
        //$$ channel = NetworkRegistry.newEventChannel(ID, () -> SuNetwork.PROTOCOL,
        //$$     NetworkRegistry.acceptMissingOr(SuNetwork.PROTOCOL), NetworkRegistry.acceptMissingOr(SuNetwork.PROTOCOL));
        //$$ channel.addListener((NetworkEvent.ClientCustomPayloadEvent event) -> {
        //$$     ServerPlayer sender = event.getSource().get().getSender();
        //$$     if (sender != null && event.getPayload() != null) {
        //$$         SuNetwork.receive(sender, readAll(event.getPayload()));
        //$$     }
        //$$     event.getSource().get().setPacketHandled(true);
        //$$ });
        //$$ channel.addListener((NetworkEvent.ServerCustomPayloadEvent event) -> {
        //$$     if (event.getPayload() != null) {
        //$$         ForgeClient.receive(readAll(event.getPayload()));
        //$$     }
        //$$     event.getSource().get().setPacketHandled(true);
        //$$ });
        //#endif
    }

    static boolean canSend(ServerPlayer player) {
        Connection connection = ((com.arthou.smputilities.mixin.PacketListenerAccessor) player.connection).smputilities$connection();
        return channel != null && connection != null && channel.isRemotePresent(connection);
    }

    static void send(ServerPlayer player, byte[] bytes) {
        //#if MC>=1.20.2
        channel.send(wrap(bytes), PacketDistributor.PLAYER.with(player));
        //#else
        //$$ player.connection.send(new ClientboundCustomPayloadPacket(ID, wrap(bytes)));
        //#endif
    }
}
