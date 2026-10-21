package com.arthou.smputilities.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Text field drawn in the SMPutilities style. Pass the outer box; the text sits inside it. */
public class SuTextField extends EditBox {
    public SuTextField(int x, int y, int width, int height, Component hint) {
        super(Minecraft.getInstance().font, x + 5, y + (height - 8) / 2, width - 10, 8, hint);
        setBordered(false);
        setMaxLength(256);
        setHint(hint.copy().withStyle(style -> style.withColor(Theme.TEXT_OFF & 0xFFFFFF)));
        setTextColor(Theme.TEXT);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean focused = isFocused();
        Theme.roundBox(graphics, getX() - 5, getY() - 6, getWidth() + 10, getHeight() + 12, 0xFF11151B,
            focused ? Theme.ACCENT_DARK : isHovered() ? Theme.BORDER_HOVER : Theme.BORDER);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }
}
