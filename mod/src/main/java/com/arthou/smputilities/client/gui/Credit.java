package com.arthou.smputilities.client.gui;

import com.arthou.smputilities.mc.Texts;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * "Made by arthou" in the bottom right corner of SMPutilities' screens: its
 * own screens, and the chest-style menus the server opens (their titles
 * carry {@link Texts#MENU_MARK}).
 */
public final class Credit {
    public static final String TEXT = "Made by arthou";
    private static final float SCALE = 0.75F;

    private Credit() {
    }

    public static void draw(Gfx graphics, Font font, int width, int height) {
        graphics.push();
        graphics.translate(width - font.width(TEXT) * SCALE - 4, height - font.lineHeight * SCALE - 3);
        graphics.scale(SCALE);
        graphics.drawString(font, TEXT, 0, 0, Theme.TEXT_DIM, false);
        graphics.pop();
    }

    /** For menus the server opened: draws the credit when the title is one of SMPutilities'. */
    public static void drawForMenu(Gfx graphics, Font font, Component title, int width, int height) {
        if (Texts.isMenuMarked(title)) {
            draw(graphics, font, width, height);
        }
    }
}
