package com.arthou.smputilities.client.gui;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Label with an animated switch on the right. */
public class SuToggle extends Widget {
    private final Consumer<Boolean> onChange;
    private boolean value;
    private float knob;
    private float hover;
    private long lastFrame = Util.getMillis();

    public SuToggle(int x, int y, int width, int height, Component label, boolean value, Consumer<Boolean> onChange) {
        super(x, y, width, height, label);
        this.value = value;
        this.knob = value ? 1 : 0;
        this.onChange = onChange;
    }

    public SuToggle tooltip(Component text) {
        setTooltip(text);
        return this;
    }

    public boolean value() {
        return value;
    }

    @Override
    protected void onClick(double mouseX, double mouseY) {
        value = !value;
        onChange.accept(value);
    }

    @Override
    protected void renderWidget(Gfx graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float delta = now - lastFrame;
        lastFrame = now;
        knob = Theme.approach(knob, value ? 1 : 0, delta, 80);
        hover = Theme.approach(hover, active && isHoveredOrFocused() ? 1 : 0, delta, 90);
        Font font = Minecraft.getInstance().font;
        Theme.roundBox(graphics, getX(), getY(), getWidth(), getHeight(), Theme.mix(Theme.CARD, Theme.CARD_HOVER, hover),
            Theme.mix(Theme.BORDER, Theme.BORDER_HOVER, hover));
        int switchWidth = 22;
        int sx = getX() + getWidth() - switchWidth - 6;
        int sy = getY() + (getHeight() - 10) / 2;
        Theme.roundRect(graphics, sx, sy, sx + switchWidth, sy + 10, Theme.mix(0xFF3A404B, Theme.ACCENT_DARK, knob));
        int kx = sx + 1 + Math.round(knob * (switchWidth - 10));
        Theme.roundRect(graphics, kx, sy + 1, kx + 8, sy + 9, active ? Theme.mix(Theme.TEXT_DIM, Theme.ACCENT, knob) : Theme.TEXT_OFF);
        int textColor = active ? Theme.mix(Theme.TEXT_DIM, Theme.TEXT, Math.max(hover, knob * 0.6F)) : Theme.TEXT_OFF;
        graphics.drawString(font, Theme.clip(font, getMessage(), getWidth() - switchWidth - 16), getX() + 6, getY() + (getHeight() - 8) / 2, textColor, false);
    }
}
