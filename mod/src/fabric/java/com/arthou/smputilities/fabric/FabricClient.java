package com.arthou.smputilities.fabric;

import com.arthou.smputilities.client.ClientNetwork;
import com.arthou.smputilities.client.SuClient;
import com.arthou.smputilities.client.bubble.BubbleRenderer;
import com.arthou.smputilities.client.gui.Gfx;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
//#if MC>=1.21.9
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
//#else
//$$ import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
//#endif
//#if MC<1.20.5
//$$ import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
//$$ import net.minecraft.network.FriendlyByteBuf;
//#endif

/** Fabric client glue. Only loaded on a physical client. */
public final class FabricClient {
    private FabricClient() {
    }

    static void init() {
        KeyBindingHelper.registerKeyBinding(SuClient.PANEL_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(client -> SuClient.tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SuClient.loggedOut());
        FabricEvents.installClientNametag(SuClient::nametag);
        //#if MC>=1.20
        HudRenderCallback.EVENT.register((graphics, delta) -> SuClient.renderHud(new Gfx(graphics)));
        //#else
        //$$ HudRenderCallback.EVENT.register((pose, delta) -> SuClient.renderHud(new Gfx(pose, null)));
        //#endif
        //#if MC>=1.21.9
        WorldRenderEvents.AFTER_ENTITIES.register(context -> BubbleRenderer.renderWorld(context.matrices()));
        //#else
        //$$ WorldRenderEvents.AFTER_ENTITIES.register(context -> BubbleRenderer.renderWorld(context.matrixStack()));
        //#endif
        //#if MC>=1.20.5
        ClientPlayNetworking.registerGlobalReceiver(FabricNetwork.SuPayload.TYPE, (payload, context) -> ClientNetwork.receive(payload.data()));
        //#else
        //$$ ClientPlayNetworking.registerGlobalReceiver(FabricNetwork.ID, (client, handler, buf, sender) -> ClientNetwork.receive(FabricNetwork.readAll(buf)));
        //#endif
    }

    static boolean canSendToServer() {
        //#if MC>=1.20.5
        return ClientPlayNetworking.canSend(FabricNetwork.SuPayload.TYPE);
        //#else
        //$$ return ClientPlayNetworking.canSend(FabricNetwork.ID);
        //#endif
    }

    static void sendToServer(byte[] bytes) {
        //#if MC>=1.20.5
        ClientPlayNetworking.send(new FabricNetwork.SuPayload(bytes));
        //#else
        //$$ FriendlyByteBuf buf = PacketByteBufs.create();
        //$$ buf.writeBytes(bytes);
        //$$ ClientPlayNetworking.send(FabricNetwork.ID, buf);
        //#endif
    }
}
