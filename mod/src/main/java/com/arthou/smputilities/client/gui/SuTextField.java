package com.arthou.smputilities.client.gui;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Single-line text field in the SMPutilities style: cursor, selection,
 * clipboard and word jumps. Pass the outer box; the text sits inside it.
 */
public class SuTextField extends Widget {
    private static final int PADDING = 5;

    private final Font font = Minecraft.getInstance().font;
    private final Component hint;
    private String value = "";
    private int maxLength = 256;
    private int cursor;
    /** The other end of the selection; equal to {@link #cursor} when nothing is selected. */
    private int anchor;
    /** First visible character. */
    private int scroll;
    private long focusedAt;
    private Consumer<String> responder = text -> {
    };

    public SuTextField(int x, int y, int width, int height, Component hint) {
        super(x, y, width, height, hint);
        this.hint = hint;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String text) {
        String clipped = text == null ? "" : text.length() > maxLength ? text.substring(0, maxLength) : text;
        if (!clipped.equals(value)) {
            value = clipped;
            responder.accept(value);
        }
        cursor = anchor = value.length();
        scroll = 0;
    }

    public void setMaxLength(int length) {
        maxLength = Math.max(1, length);
        if (value.length() > maxLength) {
            setValue(value);
        }
    }

    public void setResponder(Consumer<String> value) {
        responder = value == null ? text -> {
        } : value;
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        if (focused && !isFocused()) {
            focusedAt = Util.getMillis();
        }
        super.setFocused(focused);
    }

    // -------------------------------------------------------------- editing

    private boolean hasSelection() {
        return cursor != anchor;
    }

    private String selected() {
        return value.substring(Math.min(cursor, anchor), Math.max(cursor, anchor));
    }

    private void insert(String text) {
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= ' ' && c != 127 && c != '§') {
                clean.append(c);
            }
        }
        int start = Math.min(cursor, anchor);
        int end = Math.max(cursor, anchor);
        int room = maxLength - (value.length() - (end - start));
        if (room <= 0 && clean.length() > 0) {
            return;
        }
        String added = clean.length() > room ? clean.substring(0, room) : clean.toString();
        value = value.substring(0, start) + added + value.substring(end);
        cursor = anchor = start + added.length();
        responder.accept(value);
    }

    private void deleteTo(int target) {
        if (hasSelection()) {
            insert("");
            return;
        }
        int start = Math.max(0, Math.min(cursor, target));
        int end = Math.min(value.length(), Math.max(cursor, target));
        if (start == end) {
            return;
        }
        value = value.substring(0, start) + value.substring(end);
        cursor = anchor = start;
        responder.accept(value);
    }

    private void moveTo(int position, boolean select) {
        cursor = Math.max(0, Math.min(value.length(), position));
        if (!select) {
            anchor = cursor;
        }
    }

    /** Start of the next word in {@code direction} (-1 or 1). */
    private int wordFrom(int position, int direction) {
        int i = position;
        if (direction < 0) {
            while (i > 0 && value.charAt(i - 1) == ' ') {
                i--;
            }
            while (i > 0 && value.charAt(i - 1) != ' ') {
                i--;
            }
        } else {
            while (i < value.length() && value.charAt(i) != ' ') {
                i++;
            }
            while (i < value.length() && value.charAt(i) == ' ') {
                i++;
            }
        }
        return i;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (!isFocused() || !active) {
            return false;
        }
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean control = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (control && !shift) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> {
                    anchor = 0;
                    cursor = value.length();
                    return true;
                }
                case GLFW.GLFW_KEY_C -> {
                    minecraft.keyboardHandler.setClipboard(selected());
                    return true;
                }
                case GLFW.GLFW_KEY_X -> {
                    minecraft.keyboardHandler.setClipboard(selected());
                    insert("");
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    insert(minecraft.keyboardHandler.getClipboard());
                    return true;
                }
                default -> {
                }
            }
        }
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> deleteTo(control ? wordFrom(cursor, -1) : cursor - 1);
            case GLFW.GLFW_KEY_DELETE -> deleteTo(control ? wordFrom(cursor, 1) : cursor + 1);
            case GLFW.GLFW_KEY_LEFT -> moveTo(control ? wordFrom(cursor, -1) : !shift && hasSelection() ? Math.min(cursor, anchor) : cursor - 1, shift);
            case GLFW.GLFW_KEY_RIGHT -> moveTo(control ? wordFrom(cursor, 1) : !shift && hasSelection() ? Math.max(cursor, anchor) : cursor + 1, shift);
            case GLFW.GLFW_KEY_HOME -> moveTo(0, shift);
            case GLFW.GLFW_KEY_END -> moveTo(value.length(), shift);
            default -> {
                // Enter, Tab and Escape go to the screen or dialog.
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(String text, int modifiers) {
        if (!isFocused() || !active) {
            return false;
        }
        insert(text);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int textX = getX() + PADDING;
        String visible = value.substring(scroll);
        int offset = font.plainSubstrByWidth(visible, Math.max(0, (int) mouseX - textX)).length();
        moveTo(scroll + offset, false);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!isFocused() || button != 0) {
            return false;
        }
        int textX = getX() + PADDING;
        String visible = value.substring(scroll);
        int offset = font.plainSubstrByWidth(visible, Math.max(0, (int) mouseX - textX)).length();
        moveTo(scroll + offset, true);
        return true;
    }

    // -------------------------------------------------------------- drawing

    @Override
    protected void renderWidget(Gfx graphics, int mouseX, int mouseY, float partialTick) {
        boolean focused = isFocused();
        int x = getX();
        int y = getY();
        int inner = getWidth() - PADDING * 2;
        Theme.roundBox(graphics, x, y, getWidth(), getHeight(), 0xFF11151B,
            focused ? Theme.ACCENT_DARK : isHovered() ? Theme.BORDER_HOVER : Theme.BORDER);
        int textX = x + PADDING;
        int textY = y + (getHeight() - 8) / 2;
        if (value.isEmpty() && !focused) {
            graphics.drawString(font, Theme.clip(font, hint, inner), textX, textY, Theme.TEXT_OFF, false);
            return;
        }
        // Keep the cursor visible.
        scroll = Math.min(scroll, Math.min(cursor, value.length()));
        while (scroll < cursor && font.width(value.substring(scroll, cursor)) > inner - 2) {
            scroll++;
        }
        String visible = font.plainSubstrByWidth(value.substring(scroll), inner);
        int start = Math.max(scroll, Math.min(cursor, anchor));
        int end = Math.min(scroll + visible.length(), Math.max(cursor, anchor));
        if (focused && end > start) {
            int selectionX = textX + font.width(value.substring(scroll, start));
            graphics.fill(selectionX, textY - 1, textX + font.width(value.substring(scroll, end)), textY + 9, 0x803C6E9E);
        }
        graphics.drawString(font, visible, textX, textY, active ? Theme.TEXT : Theme.TEXT_DIM, false);
        if (focused && (Util.getMillis() - focusedAt) / 500 % 2 == 0 && cursor >= scroll) {
            int cursorX = textX + font.width(value.substring(scroll, Math.min(cursor, scroll + visible.length())));
            graphics.fill(cursorX, textY - 1, cursorX + 1, textY + 9, Theme.TEXT);
        }
    }
}
