package com.arthou.smputilities.client.staff;

import com.arthou.smputilities.client.gui.Theme;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** One sidebar category. */
final class NavItem extends AbstractButton {
    private final StaffPanelScreen.TabInfo info;
    private final StaffPanelScreen panel;
    private float hover;
    private long lastFrame = Util.getMillis();

    NavItem(int x, int y, int width, int height, StaffPanelScreen.TabInfo info, StaffPanelScreen panel) {
        super(x, y, width, height, Component.translatable("panel.smputilities.tab." + info.id()));
        this.info = info;
        this.panel = panel;
    }

    @Override
    public void onPress() {
        panel.switchTab(info.id());
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        hover = Theme.approach(hover, active && isHoveredOrFocused() ? 1 : 0, now - lastFrame, 90);
        lastFrame = now;
        boolean current = panel.isCurrent(info);
        if (!current && hover > 0.01F) {
            Theme.roundRect(graphics, getX(), getY(), getX() + getWidth(), getY() + getHeight(), Theme.mix(0x00232A34, Theme.CARD_HOVER, hover));
        }
        Font font = Minecraft.getInstance().font;
        int color = !active ? Theme.TEXT_OFF : current ? Theme.TEXT : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, hover);
        int iconColor = !active ? Theme.TEXT_OFF : current ? Theme.ACCENT : Theme.mix(Theme.TEXT_DIM, Theme.ACCENT, hover);
        int textY = getY() + (getHeight() - 8) / 2;
        graphics.drawString(font, info.icon(), getX() + 8 + Math.round(hover * 1.5F), textY, iconColor, false);
        graphics.drawString(font, Theme.clip(font, getMessage(), getWidth() - 28), getX() + 22 + Math.round(hover * 1.5F), textY, color, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
