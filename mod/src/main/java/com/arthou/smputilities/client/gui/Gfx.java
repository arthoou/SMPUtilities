package com.arthou.smputilities.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
//#if MC>=1.20
import net.minecraft.client.gui.GuiGraphics;
//#else
//$$ import com.mojang.blaze3d.systems.RenderSystem;
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//$$ import com.mojang.blaze3d.platform.Window;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.gui.GuiComponent;
//$$ import net.minecraft.client.gui.screens.Screen;
//#endif

/**
 * The 2D drawing calls SMPutilities' screens use, on every supported
 * version: {@code GuiGraphics} from 1.20, {@code PoseStack} with
 * {@code GuiComponent} before. Colors are ARGB.
 */
public final class Gfx {
    //#if MC>=1.20
    private final GuiGraphics graphics;

    public Gfx(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public GuiGraphics graphics() {
        return graphics;
    }
    //#else
    //$$ private final PoseStack pose;
    //$$ /** Needed for tooltips before 1.20; null on the HUD. */
    //$$ private final Screen screen;
    //$$
    //$$ public Gfx(PoseStack pose, Screen screen) {
    //$$     this.pose = pose;
    //$$     this.screen = screen;
    //$$ }
    //$$
    //$$ public PoseStack pose() {
    //$$     return pose;
    //$$ }
    //$$
    //$$ /** Opens the protected gradient of GuiComponent. */
    //$$ private static final class Access extends GuiComponent {
    //$$     static final Access INSTANCE = new Access();
    //$$
    //$$     void gradient(PoseStack pose, int x1, int y1, int x2, int y2, int top, int bottom) {
    //$$         fillGradient(pose, x1, y1, x2, y2, top, bottom);
    //$$     }
    //$$ }
    //#endif

    // ------------------------------------------------------------- shapes

    public void fill(int x1, int y1, int x2, int y2, int color) {
        //#if MC>=1.20
        graphics.fill(x1, y1, x2, y2, color);
        //#else
        //$$ GuiComponent.fill(pose, x1, y1, x2, y2, color);
        //#endif
    }

    /** Vertical gradient. */
    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        //#if MC>=1.20
        graphics.fillGradient(x1, y1, x2, y2, top, bottom);
        //#else
        //$$ Access.INSTANCE.gradient(pose, x1, y1, x2, y2, top, bottom);
        //#endif
    }

    /** One pixel frame around the given box. */
    public void outline(int x, int y, int width, int height, int color) {
        fill(x, y, x + width, y + 1, color);
        fill(x, y + height - 1, x + width, y + height, color);
        fill(x, y + 1, x + 1, y + height - 1, color);
        fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    // --------------------------------------------------------------- text

    public void drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        //#if MC>=1.20
        graphics.drawString(font, text, x, y, color, shadow);
        //#else
        //$$ if (shadow) {
        //$$     font.drawShadow(pose, text, x, y, color);
        //$$ } else {
        //$$     font.draw(pose, text, x, y, color);
        //$$ }
        //#endif
    }

    public void drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        drawString(font, text.getVisualOrderText(), x, y, color, shadow);
    }

    public void drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        //#if MC>=1.20
        graphics.drawString(font, text, x, y, color, shadow);
        //#else
        //$$ if (shadow) {
        //$$     font.drawShadow(pose, text, x, y, color);
        //$$ } else {
        //$$     font.draw(pose, text, x, y, color);
        //$$ }
        //#endif
    }

    public void drawString(Font font, String text, int x, int y, int color) {
        drawString(font, text, x, y, color, true);
    }

    public void drawString(Font font, Component text, int x, int y, int color) {
        drawString(font, text, x, y, color, true);
    }

    public void drawCenteredString(Font font, Component text, int centerX, int y, int color) {
        FormattedCharSequence sequence = text.getVisualOrderText();
        drawString(font, sequence, centerX - font.width(sequence) / 2, y, color, true);
    }

    public void drawCenteredString(Font font, String text, int centerX, int y, int color) {
        drawString(font, text, centerX - font.width(text) / 2, y, color, true);
    }

    // ------------------------------------------------------------ clipping

    public void enableScissor(int x1, int y1, int x2, int y2) {
        //#if MC>=1.20
        graphics.enableScissor(x1, y1, x2, y2);
        //#elif MC>=1.19.2
        //$$ GuiComponent.enableScissor(x1, y1, x2, y2);
        //#else
        //$$ Window window = Minecraft.getInstance().getWindow();
        //$$ double scale = window.getGuiScale();
        //$$ RenderSystem.enableScissor((int) (x1 * scale), (int) (window.getHeight() - y2 * scale),
        //$$     Math.max(0, (int) ((x2 - x1) * scale)), Math.max(0, (int) ((y2 - y1) * scale)));
        //#endif
    }

    public void disableScissor() {
        //#if MC>=1.20
        graphics.disableScissor();
        //#elif MC>=1.19.2
        //$$ GuiComponent.disableScissor();
        //#else
        //$$ RenderSystem.disableScissor();
        //#endif
    }

    // ----------------------------------------------------------- transform

    public void push() {
        //#if MC>=1.21.6
        graphics.pose().pushMatrix();
        //#elif MC>=1.20
        //$$ graphics.pose().pushPose();
        //#else
        //$$ pose.pushPose();
        //#endif
    }

    public void pop() {
        //#if MC>=1.21.6
        graphics.pose().popMatrix();
        //#elif MC>=1.20
        //$$ graphics.pose().popPose();
        //#else
        //$$ pose.popPose();
        //#endif
    }

    public void translate(float x, float y) {
        //#if MC>=1.21.6
        graphics.pose().translate(x, y);
        //#elif MC>=1.20
        //$$ graphics.pose().translate(x, y, 0);
        //#else
        //$$ pose.translate(x, y, 0);
        //#endif
    }

    public void scale(float factor) {
        //#if MC>=1.21.6
        graphics.pose().scale(factor, factor);
        //#elif MC>=1.20
        //$$ graphics.pose().scale(factor, factor, 1);
        //#else
        //$$ pose.scale(factor, factor, 1);
        //#endif
    }

    /**
     * Makes what follows draw over everything drawn so far (dialogs, toasts).
     * Call between {@link #push} and {@link #pop}.
     */
    public void above(int depth) {
        //#if MC>=1.21.6
        graphics.nextStratum();
        //#elif MC>=1.20
        //$$ graphics.pose().translate(0, 0, depth);
        //#else
        //$$ pose.translate(0, 0, depth);
        //#endif
    }

    // ------------------------------------------------------------- tooltip

    public void tooltip(Font font, List<FormattedCharSequence> lines, int x, int y) {
        //#if MC>=1.21.6
        graphics.setTooltipForNextFrame(font, lines, x, y);
        //#elif MC>=1.20
        //$$ graphics.renderTooltip(font, lines, x, y);
        //#else
        //$$ if (screen != null) {
        //$$     screen.renderTooltip(pose, lines, x, y);
        //$$ }
        //#endif
    }
}
