package com.arthou.smputilities.chat;

import com.arthou.smputilities.server.SuServer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Drops the vanilla join/leave lines on their way to each client, so
 * SMPutilities can send its own (custom format, disabled, or hidden while
 * vanished). Our replacements wrap the vanilla translation in an empty
 * component, which is how this filter tells them apart.
 */
public final class PresenceFilter extends ChannelOutboundHandlerAdapter {
    private static final String NAME = "smputilities_presence_filter";

    public static void install(ServerPlayer player) {
        Connection connection = player.connection.getConnection();
        if (connection.channel() != null && connection.channel().pipeline().get(NAME) == null
            && connection.channel().pipeline().get("packet_handler") != null) {
            connection.channel().pipeline().addBefore("packet_handler", NAME, new PresenceFilter());
        }
    }

    /** True while SMPutilities replaces vanilla presence messages. */
    public static boolean managed() {
        return SuServer.config().features.chat || SuServer.config().features.vanish;
    }

    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
        if (managed() && (message instanceof ClientboundSystemChatPacket packet && !packet.overlay() && isVanillaPresence(packet.content())
            || message instanceof ClientboundDisguisedChatPacket disguised && isVanillaPresence(disguised.message()))) {
            promise.setSuccess();
            return;
        }
        super.write(context, message, promise);
    }

    static boolean isVanillaPresence(Component message) {
        if (!(message.getContents() instanceof TranslatableContents contents)) {
            return false;
        }
        String key = contents.getKey();
        return key.equals("multiplayer.player.left")
            || key.equals("multiplayer.player.joined")
            || key.equals("multiplayer.player.joined.renamed");
    }
}
