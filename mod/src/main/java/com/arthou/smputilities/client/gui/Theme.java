package com.arthou.smputilities.client.gui;

import com.arthou.smputilities.mc.Texts;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * SMPutilities' look: dark translucent panels, a green accent, cut corners
 * that read as rounded at GUI scale. Shared by every screen of the mod.
 */
public final class Theme {
    public static final int PANEL_BG = 0xF414181F;
    public static final int SIDEBAR_BG = 0xF40D1015;
    public static final int BORDER = 0xFF262C36;
    public static final int BORDER_HOVER = 0xFF3C4452;
    public static final int CARD = 0xFF1A1F27;
    public static final int CARD_HOVER = 0xFF232A34;
    public static final int CARD_SELECTED = 0xFF16291B;
    public static final int ACCENT = 0xFF6AF06B;
    public static final int ACCENT_LIGHT = 0xFF9AF79B;
    public static final int ACCENT_DARK = 0xFF2F7A3A;
    public static final int ACCENT_TEXT = 0xFF0B1A0C;
    public static final int DANGER = 0xFFFF625F;
    public static final int WARN = 0xFFFFC857;
    public static final int INFO = 0xFF7FB6FF;
    public static final int TEXT = 0xFFF2F4F8;
    public static final int TEXT_DIM = 0xFF8A93A3;
    public static final int TEXT_OFF = 0xFF4F5663;
    public static final int ROW_ALT = 0x0FFFFFFF;

    private Theme() {
    }

    /** Rectangle with the corner pixels cut. */
    public static void roundRect(Gfx graphics, int x1, int y1, int x2, int y2, int color) {
        if (x2 - x1 < 2 || y2 - y1 < 2) {
            graphics.fill(x1, y1, x2, y2, color);
            return;
        }
        graphics.fill(x1 + 1, y1, x2 - 1, y2, color);
        graphics.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        graphics.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    public static void roundBox(Gfx graphics, int x, int y, int w, int h, int background, int border) {
        roundRect(graphics, x, y, x + w, y + h, border);
        roundRect(graphics, x + 1, y + 1, x + w - 1, y + h - 1, background);
    }

    /** Soft drop shadow under a panel. */
    public static void shadow(Gfx graphics, int x1, int y1, int x2, int y2) {
        for (int i = 6; i >= 1; i--) {
            roundRect(graphics, x1 - i, y1 - i + 2, x2 + i, y2 + i + 2, (0x12 - i) << 24);
        }
    }

    public static int mix(int from, int to, float t) {
        float clamped = Mth.clamp(t, 0, 1);
        int a = Mth.lerpInt(clamped, from >>> 24, to >>> 24);
        int r = Mth.lerpInt(clamped, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = Mth.lerpInt(clamped, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = Mth.lerpInt(clamped, from & 0xFF, to & 0xFF);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int withAlpha(int color, float alpha) {
        int a = Mth.clamp((int) ((color >>> 24) * alpha), 0, 255);
        return a << 24 | (color & 0xFFFFFF);
    }

    /** Text cut to {@code width} with an ellipsis. */
    public static FormattedCharSequence clip(Font font, Component text, int width) {
        if (width <= 0) {
            return FormattedCharSequence.EMPTY;
        }
        if (font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        String plain = font.plainSubstrByWidth(text.getString(), Math.max(0, width - font.width("…"))) + "…";
        return Texts.literal(plain).withStyle(text.getStyle()).getVisualOrderText();
    }

    public static String clip(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    /** Eased animation step towards {@code target}. */
    public static float approach(float value, float target, float deltaMillis, float durationMillis) {
        float step = Math.min(1.0F, deltaMillis / durationMillis);
        return value + (target - value) * step;
    }

    /** Small pill with a colored dot, e.g. "● Online". */
    public static int pill(Gfx graphics, Font font, int x, int y, String text, int color) {
        int width = font.width(text) + 16;
        roundRect(graphics, x, y, x + width, y + 13, withAlpha(color, 0.2F));
        graphics.fill(x + 5, y + 5, x + 9, y + 9, color);
        graphics.drawString(font, text, x + 12, y + 3, color, false);
        return width;
    }
}
