package com.arthou.smputilities.neoforge;

import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.client.ClientNetwork;
import com.arthou.smputilities.client.SuClient;
import com.arthou.smputilities.client.bubble.BubbleRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
//#if MC>=1.20.6
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
//#else
//$$ import net.neoforged.neoforge.client.event.RegisterGuiOverlaysEvent;
//$$ import net.neoforged.neoforge.event.TickEvent;
//$$ import net.neoforged.neoforge.network.PacketDistributor;
//#endif
//#if MC>=1.21.6
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
//#endif

/** NeoForge client glue. Only loaded on a physical client. */
public final class NeoForgeClient {
    private NeoForgeClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(SuClient.PANEL_KEY));
        //#if MC>=1.20.6
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(SmpUtilities.id("vanish_indicator"),
            (graphics, delta) -> SuClient.renderHud(graphics)));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> SuClient.tick());
        //#else
        //$$ modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerAboveAll(SmpUtilities.id("vanish_indicator"),
        //$$     (gui, graphics, partialTick, width, height) -> SuClient.renderHud(graphics)));
        //$$ NeoForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
        //$$     if (event.phase == TickEvent.Phase.END) {
        //$$         SuClient.tick();
        //$$     }
        //$$ });
        //#endif
        //#if MC>=1.21.6
        modBus.addListener((RegisterClientPayloadHandlersEvent event) -> event.register(NeoForgeNetwork.SuPayload.TYPE,
            (payload, context) -> receive(payload.data())));
        //#endif
        NeoForge.EVENT_BUS.addListener((PlayerEvent.NameFormat event) -> {
            if (event.getEntity().level().isClientSide()) {
                event.setDisplayname(SuClient.nametag(event.getEntity(), event.getDisplayname()));
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Post event) ->
            BubbleRenderer.render(event.getEntity(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> SuClient.loggedOut());
    }

    static void receive(byte[] bytes) {
        ClientNetwork.receive(bytes);
    }

    static boolean canSendToServer() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        //#if MC>=1.20.6
        return connection != null && connection.hasChannel(NeoForgeNetwork.SuPayload.TYPE);
        //#else
        //$$ return connection != null && connection.isConnected(NeoForgeNetwork.SuPayload.ID);
        //#endif
    }

    static void sendToServer(byte[] bytes) {
        //#if MC>=1.20.6
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new NeoForgeNetwork.SuPayload(bytes));
        }
        //#else
        //$$ PacketDistributor.SERVER.noArg().send(new NeoForgeNetwork.SuPayload(bytes));
        //#endif
    }
}
