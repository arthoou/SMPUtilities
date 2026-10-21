package com.arthou.smputilities.forge;

import com.arthou.smputilities.client.ClientNetwork;
import com.arthou.smputilities.client.SuClient;
import com.arthou.smputilities.client.gui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
//#if MC>=1.19
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
//#else
//$$ import net.minecraftforge.client.ClientRegistry;
//$$ import net.minecraftforge.client.event.RenderGameOverlayEvent;
//$$ import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//#endif
//#if MC<1.21.3
//$$ import com.arthou.smputilities.client.bubble.BubbleRenderer;
//$$ import net.minecraftforge.client.event.RenderLevelStageEvent;
//#endif
//#if MC>=1.21 && MC<1.21.3
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//#endif
//#if MC>=1.20.2
import net.minecraftforge.network.PacketDistributor;
//#else
//$$ import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
//#endif

/**
 * Forge client glue. Only loaded on a physical client. Speech bubbles use
 * the level stage event up to 1.21.1 and a LevelRenderer mixin on 1.21.3 to
 * 1.21.8; Forge 1.21.9+ has no hook for them yet.
 */
public final class ForgeClient {
    private ForgeClient() {
    }

    static void init() {
        //#if MC>=1.19
        ForgeBus.onMod(RegisterKeyMappingsEvent.class, event -> event.register(SuClient.PANEL_KEY));
        ForgeBus.on(RenderGuiEvent.Post.class, event -> SuClient.renderHud(gfx(event)));
        ForgeBus.on(ClientPlayerNetworkEvent.LoggingOut.class, event -> SuClient.loggedOut());
        //#else
        //$$ ForgeBus.onMod(FMLClientSetupEvent.class, event -> ClientRegistry.registerKeyBinding(SuClient.PANEL_KEY));
        //$$ ForgeBus.on(RenderGameOverlayEvent.Post.class, event -> {
        //$$     if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
        //$$         SuClient.renderHud(new Gfx(event.getMatrixStack(), null));
        //$$     }
        //$$ });
        //$$ ForgeBus.on(ClientPlayerNetworkEvent.LoggedOutEvent.class, event -> SuClient.loggedOut());
        //#endif
        //#if MC>=1.20.2
        ForgeBus.on(TickEvent.ClientTickEvent.Post.class, event -> SuClient.tick());
        //#else
        //$$ ForgeBus.on(TickEvent.ClientTickEvent.class, event -> {
        //$$     if (event.phase == TickEvent.Phase.END) {
        //$$         SuClient.tick();
        //$$     }
        //$$ });
        //#endif
        //#if MC>=1.21 && MC<1.21.3
        //$$ ForgeBus.on(RenderLevelStageEvent.class, event -> {
        //$$     if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
        //$$         PoseStack pose = new PoseStack();
        //$$         pose.mulPose(event.getPoseStack());
        //$$         BubbleRenderer.renderWorld(pose);
        //$$     }
        //$$ });
        //#elif MC>=1.20
        //$$ ForgeBus.on(RenderLevelStageEvent.class, event -> {
        //$$     if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
        //$$         BubbleRenderer.renderWorld(event.getPoseStack());
        //$$     }
        //$$ });
        //#elif MC<1.21.3
        //$$ ForgeBus.on(RenderLevelStageEvent.class, event -> {
        //$$     if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
        //$$         BubbleRenderer.renderWorld(event.getPoseStack());
        //$$     }
        //$$ });
        //#endif
    }

    //#if MC>=1.19
    private static Gfx gfx(RenderGuiEvent.Post event) {
        //#if MC>=1.20
        return new Gfx(event.getGuiGraphics());
        //#else
        //$$ return new Gfx(event.getPoseStack(), null);
        //#endif
    }
    //#endif

    static Component nametag(Player player, Component name) {
        return SuClient.nametag(player, name);
    }

    static void receive(byte[] bytes) {
        ClientNetwork.receive(bytes);
    }

    static boolean canSendToServer() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && ForgeNetwork.channel != null && ForgeNetwork.channel.isRemotePresent(connection.getConnection());
    }

    static void sendToServer(byte[] bytes) {
        //#if MC>=1.20.2
        ForgeNetwork.channel.send(ForgeNetwork.wrap(bytes), PacketDistributor.SERVER.noArg());
        //#else
        //$$ ClientPacketListener connection = Minecraft.getInstance().getConnection();
        //$$ if (connection != null) {
        //$$     connection.send(new ServerboundCustomPayloadPacket(ForgeNetwork.ID, ForgeNetwork.wrap(bytes)));
        //$$ }
        //#endif
    }
}
