package com.arthou.smputilities.client;

import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.network.Payloads;
import com.arthou.smputilities.network.SuNetwork;
import com.arthou.smputilities.platform.Platform;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

/** Client half of SMPutilities' channel: decoding server messages and sending requests. */
public final class ClientNetwork {
    private ClientNetwork() {
    }

    /** Called by the loader when a server message arrives, on any thread. */
    public static void receive(byte[] bytes) {
        Minecraft.getInstance().execute(() -> {
            Payloads.Message message;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
            try {
                message = Payloads.readClientbound(buf.readVarInt(), buf);
            } catch (RuntimeException e) {
                SuLog.warn("Dropped a malformed SMPutilities message from the server", e);
                return;
            }
            if (message != null) {
                ClientPayloads.handle(message);
            }
        });
    }

    /** Whether the server we are connected to runs SMPutilities. */
    public static boolean available() {
        return Minecraft.getInstance().getConnection() != null && Platform.get().canSendToServer();
    }

    public static void send(Payloads.Message message) {
        if (available()) {
            Platform.get().sendToServer(SuNetwork.encode(message));
        }
    }
}
