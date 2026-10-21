package com.arthou.smputilities.client.gui;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Base for SMPutilities screens: modal dialogs and toast messages. */
public abstract class SuScreen extends Screen {
    private record Toast(String text, boolean error, long createdAt) {
    }

    private final List<Toast> toasts = new ArrayList<>();
    private Dialog dialog;

    protected SuScreen(Component title) {
        super(title);
    }

    public void openDialog(Dialog value) {
        dialog = value;
        value.open(this);
    }

    void closeDialog(Dialog value) {
        if (dialog == value) {
            dialog = null;
        }
    }

    public boolean hasDialog() {
        return dialog != null;
    }

    public void toast(String text, boolean error) {
        toasts.add(new Toast(text, error, Util.getMillis()));
        while (toasts.size() > 4) {
            toasts.remove(0);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean modal = dialog != null;
        super.render(graphics, modal ? -1000 : mouseX, modal ? -1000 : mouseY, partialTick);
        renderToasts(graphics);
        if (modal) {
            dialog.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderToasts(GuiGraphics graphics) {
        long now = Util.getMillis();
        int y = height - 10;
        Iterator<Toast> iterator = toasts.iterator();
        List<Toast> alive = new ArrayList<>();
        while (iterator.hasNext()) {
            Toast toast = iterator.next();
            if (now - toast.createdAt() > 4500) {
                iterator.remove();
            } else {
                alive.add(toast);
            }
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        for (int i = alive.size() - 1; i >= 0; i--) {
            Toast toast = alive.get(i);
            long age = now - toast.createdAt();
            float alpha = age < 150 ? age / 150.0F : age > 3800 ? Math.max(0, 1 - (age - 3800) / 700.0F) : 1;
            String text = Theme.clip(font, toast.text(), Math.min(320, width - 40));
            int boxWidth = font.width(text) + 20;
            int x = width - boxWidth - 10;
            int top = y - 20;
            int slide = Math.round((1 - Math.min(1, age / 150.0F)) * 12);
            Theme.roundBox(graphics, x + slide, top, boxWidth, 18, Theme.withAlpha(Theme.PANEL_BG, alpha),
                Theme.withAlpha(toast.error() ? Theme.DANGER : Theme.ACCENT_DARK, alpha));
            graphics.fill(x + slide + 5, top + 7, x + slide + 9, top + 11, Theme.withAlpha(toast.error() ? Theme.DANGER : Theme.ACCENT, alpha));
            if (alpha > 0.1F) {
                graphics.drawString(font, text, x + slide + 13, top + 5, Theme.withAlpha(Theme.TEXT, alpha), false);
            }
            y -= 22;
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (dialog != null) {
            return dialog.mouseClicked(mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (dialog != null) {
            return dialog.keyPressed(key, scan, modifiers);
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (dialog != null) {
            return dialog.charTyped(character, modifiers);
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (dialog != null) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
