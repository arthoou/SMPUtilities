package com.arthou.smputilities.client.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;

import java.util.UUID;
//#if MC>=1.20.2
import net.minecraft.client.gui.components.PlayerFaceRenderer;
//#if MC>=1.21.9
import net.minecraft.world.entity.player.PlayerSkin;
import java.util.Optional;
//#else
//$$ import net.minecraft.client.resources.PlayerSkin;
//#endif
//#else
//$$ import com.mojang.blaze3d.systems.RenderSystem;
//$$ import net.minecraft.resources.ResourceLocation;
//#if MC>=1.19.2
//$$ import net.minecraft.client.gui.components.PlayerFaceRenderer;
//#else
//$$ import net.minecraft.client.gui.GuiComponent;
//#endif
//#endif

/** Player faces for lists; skins of offline players are fetched in the background by the skin manager. */
public final class Heads {
    private Heads() {
    }

    //#if MC>=1.20.2
    public static void draw(Gfx graphics, UUID id, String name, int x, int y, int size) {
        PlayerFaceRenderer.draw(graphics.graphics(), skin(id, name), x, y, size);
    }

    private static PlayerSkin skin(UUID id, String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(id);
            if (info != null) {
                return info.getSkin();
            }
        }
        try {
            GameProfile profile = new GameProfile(id, name == null || name.isEmpty() ? "?" : name);
            //#if MC>=1.21.9
            Optional<PlayerSkin> loaded = minecraft.getSkinManager().get(profile).getNow(Optional.empty());
            return loaded.orElseGet(() -> DefaultPlayerSkin.get(id));
            //#else
            //$$ return minecraft.getSkinManager().getInsecureSkin(profile);
            //#endif
        } catch (RuntimeException e) {
            return DefaultPlayerSkin.get(id);
        }
    }
    //#else
    //$$ public static void draw(Gfx graphics, UUID id, String name, int x, int y, int size) {
    //$$     ResourceLocation skin = skin(id, name);
    //#if MC>=1.20
    //$$     PlayerFaceRenderer.draw(graphics.graphics(), skin, x, y, size);
    //#else
    //$$     RenderSystem.setShaderTexture(0, skin);
    //$$     RenderSystem.enableBlend();
    //#if MC>=1.19.2
    //$$     PlayerFaceRenderer.draw(graphics.pose(), x, y, size);
    //#else
    //$$     GuiComponent.blit(graphics.pose(), x, y, size, size, 8, 8, 8, 8, 64, 64);
    //$$     GuiComponent.blit(graphics.pose(), x, y, size, size, 40, 8, 8, 8, 64, 64);
    //#endif
    //#endif
    //$$ }
    //$$
    //$$ private static ResourceLocation skin(UUID id, String name) {
    //$$     Minecraft minecraft = Minecraft.getInstance();
    //$$     if (minecraft.getConnection() != null) {
    //$$         PlayerInfo info = minecraft.getConnection().getPlayerInfo(id);
    //$$         if (info != null) {
    //$$             return info.getSkinLocation();
    //$$         }
    //$$     }
    //#if MC>=1.19.2
    //$$     try {
    //$$         return minecraft.getSkinManager().getInsecureSkinLocation(new GameProfile(id, name == null || name.isEmpty() ? "?" : name));
    //$$     } catch (RuntimeException e) {
    //$$         return DefaultPlayerSkin.getDefaultSkin(id);
    //$$     }
    //#else
    //$$     return DefaultPlayerSkin.getDefaultSkin(id);
    //#endif
    //$$ }
    //#endif
}
