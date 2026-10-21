package com.arthou.smputilities.client.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;

import java.util.UUID;

/** Player faces for lists; skins of offline players are fetched in the background by the skin manager. */
public final class Heads {
    private Heads() {
    }

    public static void draw(GuiGraphics graphics, UUID id, String name, int x, int y, int size) {
        PlayerFaceRenderer.draw(graphics, skin(id, name), x, y, size);
    }

    public static PlayerSkin skin(UUID id, String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(id);
            if (info != null) {
                return info.getSkin();
            }
        }
        try {
            return minecraft.getSkinManager().getInsecureSkin(new GameProfile(id, name == null || name.isEmpty() ? "?" : name));
        } catch (RuntimeException e) {
            return DefaultPlayerSkin.get(id);
        }
    }
}
