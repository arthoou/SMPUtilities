package com.arthou.smputilities.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.function.Consumer;

/** Flat button with a smoothed hover. */
public class SuButton extends AbstractButton {
    public enum Kind { GHOST, PRIMARY, DANGER, WARN, SUBTLE }

    private final Consumer<SuButton> action;
    private float hover;
    private long lastFrame = Util.getMillis();
    public Kind kind = Kind.GHOST;
    public String icon = "";

    public SuButton(int x, int y, int width, int height, Component message, Consumer<SuButton> action) {
        super(x, y, width, height, message);
        this.action = action;
    }

    public SuButton kind(Kind value) {
        kind = value;
        return this;
    }

    public SuButton icon(String value) {
        icon = value;
        return this;
    }

    public SuButton tooltip(Component text) {
        setTooltip(Tooltip.create(text));
        return this;
    }

    public SuButton enabled(boolean value) {
        active = value;
        return this;
    }

    @Override
    public void onPress() {
        action.accept(this);
    }

    protected float hover() {
        long now = Util.getMillis();
        float target = active && isHoveredOrFocused() ? 1.0F : 0.0F;
        hover = Theme.approach(hover, target, now - lastFrame, 90.0F);
        lastFrame = now;
        return hover;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float t = hover();
        int background;
        int border;
        int textColor;
        switch (kind) {
            case PRIMARY -> {
                background = active ? Theme.mix(Theme.ACCENT, Theme.ACCENT_LIGHT, t) : Theme.CARD;
                border = active ? background : Theme.BORDER;
                textColor = active ? Theme.ACCENT_TEXT : Theme.TEXT_OFF;
            }
            case DANGER -> {
                background = active ? Theme.mix(0x00FF625F, 0x40FF625F, t) : 0x00000000;
                border = active ? Theme.mix(0x80FF625F, Theme.DANGER, t) : Theme.BORDER;
                textColor = active ? Theme.DANGER : Theme.TEXT_OFF;
            }
            case WARN -> {
                background = active ? Theme.mix(0x00FFC857, 0x40FFC857, t) : 0x00000000;
                border = active ? Theme.mix(0x80FFC857, Theme.WARN, t) : Theme.BORDER;
                textColor = active ? Theme.WARN : Theme.TEXT_OFF;
            }
            case SUBTLE -> {
                background = Theme.mix(0x00232A34, Theme.CARD_HOVER, t);
                border = background;
                textColor = active ? Theme.mix(Theme.TEXT_DIM, Theme.TEXT, t) : Theme.TEXT_OFF;
            }
            default -> {
                background = Theme.mix(Theme.CARD, Theme.CARD_HOVER, t);
                border = Theme.mix(Theme.BORDER, Theme.BORDER_HOVER, t);
                textColor = active ? Theme.TEXT : Theme.TEXT_OFF;
            }
        }
        Theme.roundBox(graphics, getX(), getY(), getWidth(), getHeight(), background, border);
        Font font = Minecraft.getInstance().font;
        Component label = icon.isEmpty() ? getMessage() : Component.literal(icon + " ").append(getMessage());
        FormattedCharSequence text = Theme.clip(font, label.copy().withStyle(ChatFormatting.BOLD), getWidth() - 8);
        graphics.drawString(font, text, getX() + (getWidth() - font.width(text)) / 2, getY() + (getHeight() - 8) / 2, textColor, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
