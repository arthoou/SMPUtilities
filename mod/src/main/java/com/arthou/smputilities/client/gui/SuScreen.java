package com.arthou.smputilities.client.gui;

import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
//#if MC>=1.20
import net.minecraft.client.gui.GuiGraphics;
//#else
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//#endif
//#if MC>=1.21.9
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//#endif

/**
 * Base for SMPutilities screens: own widgets, modal dialogs and toasts. The
 * vanilla render and input methods (whose signatures differ between
 * versions) are overridden here once and turned into plain calls, so
 * subclasses work the same on every version.
 */
public abstract class SuScreen extends Screen {
    private record Toast(String text, boolean error, long createdAt) {
    }

    private final List<Widget> widgets = new ArrayList<>();
    private final List<Toast> toasts = new ArrayList<>();
    private Dialog dialog;
    private boolean wasOpened;

    protected SuScreen(Component title) {
        super(title);
    }

    // -------------------------------------------------------------- widgets

    /** Builds the widgets for the current size. Called on open, on resize and by {@link #rebuild}. */
    protected abstract void build();

    /** Once, when the screen is shown (before the first {@link #build}). */
    protected void opened() {
    }

    @Override
    protected final void init() {
        if (!wasOpened) {
            wasOpened = true;
            opened();
        }
        widgets.clear();
        build();
        if (dialog != null) {
            dialog.open(this);
        }
    }

    public void rebuild() {
        widgets.clear();
        build();
    }

    public <T extends Widget> T addWidget(T widget) {
        widgets.add(widget);
        return widget;
    }

    // -------------------------------------------------------- dialogs/toasts

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

    // -------------------------------------------------------------- drawing

    /** Behind the widgets (panel frames, headers). */
    protected void renderBack(Gfx graphics, int mouseX, int mouseY, float partialTick) {
    }

    /** Over the widgets, under dialogs and toasts. */
    protected void renderFront(Gfx graphics, int mouseX, int mouseY, float partialTick) {
    }

    private void drawBackground(Gfx graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0xA0101010, 0xB0101010);
        renderBack(graphics, mouseX, mouseY, partialTick);
    }

    private void drawContent(Gfx graphics, int mouseX, int mouseY, float partialTick) {
        boolean modal = dialog != null;
        int mx = modal ? -1000 : mouseX;
        int my = modal ? -1000 : mouseY;
        Widget hovered = null;
        for (Widget widget : widgets) {
            widget.render(graphics, mx, my, partialTick);
            if (widget.visible && widget.isHovered() && widget.tooltip() != null) {
                hovered = widget;
            }
        }
        renderFront(graphics, mx, my, partialTick);
        renderToasts(graphics);
        if (modal) {
            dialog.render(graphics, mouseX, mouseY, partialTick);
        } else if (hovered != null) {
            graphics.tooltip(font, font.split(hovered.tooltip(), 220), mouseX, mouseY);
        }
    }

    //#if MC>=1.20.2
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // From 1.21.6 vanilla calls this before render(); earlier render() draws it below.
        //#if MC>=1.21.6
        drawBackground(new Gfx(graphics), mouseX, mouseY, partialTick);
        //#endif
    }
    //#endif

    //#if MC>=1.20
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Gfx gfx = new Gfx(graphics);
        //#if MC<1.21.6
        //$$ drawBackground(gfx, mouseX, mouseY, partialTick);
        //#endif
        drawContent(gfx, mouseX, mouseY, partialTick);
    }
    //#else
    //$$ @Override
    //$$ public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
    //$$     Gfx gfx = new Gfx(pose, this);
    //$$     drawBackground(gfx, mouseX, mouseY, partialTick);
    //$$     drawContent(gfx, mouseX, mouseY, partialTick);
    //$$ }
    //#endif

    private void renderToasts(Gfx graphics) {
        if (toasts.isEmpty()) {
            return;
        }
        long now = Util.getMillis();
        int y = height - 10;
        List<Toast> alive = new ArrayList<>();
        for (Iterator<Toast> iterator = toasts.iterator(); iterator.hasNext(); ) {
            Toast toast = iterator.next();
            if (now - toast.createdAt() > 4500) {
                iterator.remove();
            } else {
                alive.add(toast);
            }
        }
        graphics.push();
        graphics.above(300);
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
        graphics.pop();
    }

    // ---------------------------------------------------------------- input

    /** A mouse button went down. Subclasses may intercept before the widgets. */
    protected boolean handleClick(double mouseX, double mouseY, int button) {
        if (dialog != null) {
            return dialog.mouseClicked(mouseX, mouseY, button);
        }
        return clickWidgets(widgets, mouseX, mouseY, button);
    }

    /** Clicks the topmost widget under the mouse; focus follows the click. */
    static boolean clickWidgets(List<Widget> list, double mouseX, double mouseY, int button) {
        Widget target = null;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).isMouseOver(mouseX, mouseY)) {
                target = list.get(i);
                break;
            }
        }
        for (Widget widget : list) {
            if (widget != target && widget.isFocused()) {
                widget.setFocused(false);
            }
        }
        if (target == null) {
            return false;
        }
        if (target.focusable() && target.active) {
            target.setFocused(true);
        }
        return target.mouseClicked(mouseX, mouseY, button);
    }

    protected boolean handleRelease(double mouseX, double mouseY, int button) {
        if (dialog != null) {
            return true;
        }
        boolean handled = false;
        for (Widget widget : widgets) {
            handled |= widget.mouseReleased(mouseX, mouseY, button);
        }
        return handled;
    }

    protected boolean handleDrag(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dialog != null) {
            return true;
        }
        for (Widget widget : widgets) {
            if (widget.isFocused() && widget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    protected boolean handleScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (dialog != null) {
            return true;
        }
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget widget = widgets.get(i);
            if (widget.isMouseOver(mouseX, mouseY) && widget.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }

    protected boolean handleKey(int key, int scancode, int modifiers) {
        if (dialog != null) {
            return dialog.keyPressed(key, scancode, modifiers);
        }
        for (Widget widget : widgets) {
            if (widget.isFocused() && widget.keyPressed(key, scancode, modifiers)) {
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && shouldCloseOnEsc()) {
            onClose();
            return true;
        }
        return false;
    }

    protected boolean handleChar(String text, int modifiers) {
        if (dialog != null) {
            return dialog.charTyped(text, modifiers);
        }
        for (Widget widget : widgets) {
            if (widget.isFocused() && widget.charTyped(text, modifiers)) {
                return true;
            }
        }
        return false;
    }

    //#if MC>=1.21.9
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return handleClick(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return handleRelease(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return handleDrag(event.x(), event.y(), event.button(), dragX, dragY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return handleKey(event.key(), event.scancode(), event.modifiers());
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return handleChar(event.codepointAsString(), event.modifiers());
    }
    //#else
    //$$ @Override
    //$$ public boolean mouseClicked(double mouseX, double mouseY, int button) {
    //$$     return handleClick(mouseX, mouseY, button);
    //$$ }
    //$$
    //$$ @Override
    //$$ public boolean mouseReleased(double mouseX, double mouseY, int button) {
    //$$     return handleRelease(mouseX, mouseY, button);
    //$$ }
    //$$
    //$$ @Override
    //$$ public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
    //$$     return handleDrag(mouseX, mouseY, button, dragX, dragY);
    //$$ }
    //$$
    //$$ @Override
    //$$ public boolean keyPressed(int key, int scancode, int modifiers) {
    //$$     return handleKey(key, scancode, modifiers);
    //$$ }
    //$$
    //$$ @Override
    //$$ public boolean charTyped(char character, int modifiers) {
    //$$     return handleChar(String.valueOf(character), modifiers);
    //$$ }
    //#endif

    //#if MC>=1.20.2
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return handleScroll(mouseX, mouseY, scrollX, scrollY);
    }
    //#else
    //$$ @Override
    //$$ public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
    //$$     return handleScroll(mouseX, mouseY, 0, delta);
    //$$ }
    //#endif

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
