package com.arthou.smputilities.chat;

import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.server.SuServer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.Channel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
//#if MC>=1.19
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
//#else
//$$ import net.minecraft.network.chat.TranslatableComponent;
//$$ import net.minecraft.network.protocol.game.ClientboundChatPacket;
//#endif
//#if MC>=1.19.3
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
//#endif

/**
 * Drops the vanilla join/leave lines on their way to each client, so
 * SMPutilities can send its own (custom format, disabled, or hidden while
 * vanished). Our replacements wrap the vanilla translation in an empty
 * component, which is how this filter tells them apart.
 */
public final class PresenceFilter extends ChannelOutboundHandlerAdapter {
    private static final String NAME = "smputilities_presence_filter";

    public static void install(ServerPlayer player) {
        Channel channel = Mc.channel(player);
        if (channel != null && channel.pipeline().get(NAME) == null && channel.pipeline().get("packet_handler") != null) {
            channel.pipeline().addBefore("packet_handler", NAME, new PresenceFilter());
        }
    }

    /** True while SMPutilities replaces vanilla presence messages. */
    public static boolean managed() {
        return SuServer.config().features.chat || SuServer.config().features.vanish;
    }

    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
        if (managed() && isVanillaPresence(message)) {
            promise.setSuccess();
            return;
        }
        super.write(context, message, promise);
    }

    private static boolean isVanillaPresence(Object packet) {
        //#if MC>=1.19.3
        if (packet instanceof ClientboundDisguisedChatPacket disguised) {
            return isVanillaPresence(disguised.message());
        }
        //#endif
        //#if MC>=1.19
        return packet instanceof ClientboundSystemChatPacket system && !system.overlay() && isVanillaPresence(system.content());
        //#else
        //$$ return packet instanceof ClientboundChatPacket chat && isVanillaPresence(chat.getMessage());
        //#endif
    }

    static boolean isVanillaPresence(Component message) {
        //#if MC>=1.19
        if (!(message.getContents() instanceof TranslatableContents contents)) {
            return false;
        }
        //#else
        //$$ if (!(message instanceof TranslatableComponent contents)) {
        //$$     return false;
        //$$ }
        //#endif
        String key = contents.getKey();
        return key.equals("multiplayer.player.left")
            || key.equals("multiplayer.player.joined")
            || key.equals("multiplayer.player.joined.renamed");
    }
}
