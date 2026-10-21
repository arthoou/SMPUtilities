package com.arthou.smputilities.client;

import com.arthou.smputilities.core.nick.NameAnimation;
import com.arthou.smputilities.mc.Texts;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;

import java.util.Random;

/**
 * Nameplate animations, ported from HiddenNames (pitbox46, MIT): breathe
 * pulses the name's color, rainbow runs a hue along the letters, cycle
 * blends between random colors, random scrambles the name.
 */
public final class NameAnimations {
    private NameAnimations() {
    }

    /** @param tick the level's game time plus a per-player offset */
    public static Component apply(NameAnimation animation, Component name, Player player, long tick) {
        return switch (animation) {
            case BREATHE -> breathe(name, tick);
            case RAINBOW -> rainbow(name, tick);
            case CYCLE -> cycle(name, player, tick);
            case RANDOM -> random(player, tick);
            default -> name;
        };
    }

    private static Component breathe(Component name, long tick) {
        int amp = 60;
        int cycle = 180;
        int base = firstColor(name);
        double sin = Math.sin((tick % 360) * 2 * Math.PI / cycle);
        int red = clamp((base >> 16 & 0xFF) + amp * sin);
        int green = clamp((base >> 8 & 0xFF) + amp * sin);
        int blue = clamp((base & 0xFF) + amp * sin);
        return Texts.literal(name.getString()).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(red << 16 | green << 8 | blue)));
    }

    private static Component rainbow(Component name, long tick) {
        MutableComponent result = Texts.empty();
        String text = name.getString();
        for (int i = 0; i < text.length(); i++) {
            float hue = ((tick + 3L * i) % 180) / 180F;
            result.append(Texts.literal(String.valueOf(text.charAt(i))).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(hsvToRgb(hue)))));
        }
        return result;
    }

    private static Component cycle(Component name, Player player, long tick) {
        int toNext = 80;
        long step = tick / toNext;
        int from = new Random(player.getId() * 31L + step).nextInt();
        int to = new Random(player.getId() * 31L + step + 1).nextInt();
        float percent = (float) (toNext - tick % toNext) / toNext;
        return Texts.literal(name.getString()).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(blend(from, to, percent))));
    }

    private static Component random(Player player, long tick) {
        int toNext = 15;
        Random rng = new Random(player.getId() * (tick / toNext));
        int length = 3 + rng.nextInt(7);
        MutableComponent result = Texts.empty();
        for (int i = 0; i < length; i++) {
            char c = (char) (32 + rng.nextInt(95));
            result.append(Texts.literal(String.valueOf(c)).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rng.nextInt() & 0xFFFFFF))));
        }
        return result;
    }

    /** The first color in the name (styled names keep it on their parts), white when there is none. */
    private static int firstColor(Component name) {
        TextColor color = name.getStyle().getColor();
        if (color == null) {
            for (Component part : name.getSiblings()) {
                if (part.getStyle().getColor() != null) {
                    color = part.getStyle().getColor();
                    break;
                }
            }
        }
        return color == null ? 0xFFFFFF : color.getValue();
    }

    private static int clamp(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    private static int blend(int primary, int secondary, float percent) {
        float p = Math.max(0, Math.min(1, percent));
        int red = (int) ((primary >> 16 & 0xFF) * p + (secondary >> 16 & 0xFF) * (1 - p));
        int green = (int) ((primary >> 8 & 0xFF) * p + (secondary >> 8 & 0xFF) * (1 - p));
        int blue = (int) ((primary & 0xFF) * p + (secondary & 0xFF) * (1 - p));
        return red << 16 | green << 8 | blue;
    }

    /** Full saturation and value. */
    private static int hsvToRgb(float hue) {
        float h = (hue - (float) Math.floor(hue)) * 6F;
        int sector = (int) h;
        float f = h - sector;
        float q = 1 - f;
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> { r = 1; g = f; b = 0; }
            case 1 -> { r = q; g = 1; b = 0; }
            case 2 -> { r = 0; g = 1; b = f; }
            case 3 -> { r = 0; g = q; b = 1; }
            case 4 -> { r = f; g = 0; b = 1; }
            default -> { r = 1; g = 0; b = q; }
        }
        return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }
}
