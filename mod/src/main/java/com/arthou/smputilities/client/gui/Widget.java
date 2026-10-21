package com.arthou.smputilities.client.gui;

import com.arthou.smputilities.mc.Texts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Base of SMPutilities' widgets. Vanilla widgets changed their drawing and
 * input methods many times between 1.18 and 1.21, so the mod's screens use
 * these instead: {@link SuScreen} draws them with {@link Gfx} and feeds them
 * plain coordinates, buttons and key codes on every version.
 */
public abstract class Widget {
    private int x;
    private int y;
    private int width;
    private int height;
    private Component message;
    private Component tooltip;
    private boolean focused;
    protected boolean hovered;
    public boolean active = true;
    public boolean visible = true;

    protected Widget(int x, int y, int width, int height, Component message) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.message = message == null ? Texts.empty() : message;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setX(int value) {
        x = value;
    }

    public void setY(int value) {
        y = value;
    }

    public void setWidth(int value) {
        width = value;
    }

    public Component getMessage() {
        return message;
    }

    public void setMessage(Component value) {
        message = value == null ? Texts.empty() : value;
    }

    public void setTooltip(Component value) {
        tooltip = value;
    }

    public Component tooltip() {
        return tooltip;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean value) {
        focused = value;
    }

    /** Whether clicking gives this widget the keyboard. */
    public boolean focusable() {
        return false;
    }

    public boolean isHovered() {
        return hovered;
    }

    public boolean isHoveredOrFocused() {
        return hovered || focused;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    public final void render(Gfx graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) {
            return;
        }
        hovered = isMouseOver(mouseX, mouseY);
        renderWidget(graphics, mouseX, mouseY, partialTick);
    }

    protected abstract void renderWidget(Gfx graphics, int mouseX, int mouseY, float partialTick);

    // ---------------------------------------------------------------- input

    /** Left click on an active widget; buttons override this. */
    protected void onClick(double mouseX, double mouseY) {
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        playClick();
        onClick(mouseX, mouseY);
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    /** GLFW key code, scancode and modifier bits. */
    public boolean keyPressed(int key, int scancode, int modifiers) {
        return false;
    }

    /** Typed text (one code point). */
    public boolean charTyped(String text, int modifiers) {
        return false;
    }

    public static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
