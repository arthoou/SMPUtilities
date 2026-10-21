package com.arthou.smputilities.client.staff;

import com.arthou.smputilities.client.gui.Gfx;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.client.ClientState;
import com.arthou.smputilities.client.gui.Dialog;
import com.arthou.smputilities.client.gui.SuButton;
import com.arthou.smputilities.client.gui.SuTextField;
import com.arthou.smputilities.client.gui.SuToggle;
import com.arthou.smputilities.client.gui.TableView;
import com.arthou.smputilities.client.gui.Theme;
import com.arthou.smputilities.core.perm.Perm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/** One category of the staff panel. Widgets are rebuilt in {@link #init()}; data arrives through {@link #dataChanged}. */
abstract class PanelTab {
    protected StaffPanelScreen panel;
    protected final Font font = Minecraft.getInstance().font;
    private String id;

    void attach(StaffPanelScreen owner, String tabId) {
        this.panel = owner;
        this.id = tabId;
    }

    String id() {
        return id;
    }

    /** First time the tab is shown: request what it needs. */
    void opened() {
    }

    /** Called every two seconds while the tab is shown. */
    void refresh() {
    }

    abstract void init();

    void dataChanged(String what) {
    }

    void tick() {
    }

    void renderBack(Gfx graphics, int mouseX, int mouseY, float partialTick) {
    }

    void renderFront(Gfx graphics, int mouseX, int mouseY, float partialTick) {
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    // ------------------------------------------------------------ helpers

    protected int left() {
        return panel.contentLeft();
    }

    protected int right() {
        return panel.contentRight();
    }

    protected int top() {
        return panel.contentTop();
    }

    protected int bottom() {
        return panel.contentBottom();
    }

    protected int width() {
        return right() - left();
    }

    protected static Component tr(String key, Object... args) {
        return Texts.translatable("panel.smputilities." + key, args);
    }

    protected SuButton button(int x, int y, int width, String key, Consumer<SuButton> action) {
        return panel.add(new SuButton(x, y, width, 18, tr(key), action));
    }

    protected SuButton button(int x, int y, int width, String key, Perm perm, Consumer<SuButton> action) {
        SuButton button = button(x, y, width, key, action);
        button.active = ClientState.has(perm);
        return button;
    }

    protected SuToggle toggle(int x, int y, int width, String key, boolean value, Consumer<Boolean> onChange) {
        return panel.add(new SuToggle(x, y, width, 18, tr(key), value, onChange));
    }

    protected SuTextField field(int x, int y, int width, String hintKey) {
        return panel.add(new SuTextField(x, y, width, 18, tr(hintKey)));
    }

    protected TableView table(int x, int y, int width, int height, List<TableView.Column> columns) {
        TableView table = panel.add(new TableView(x, y, width, height, columns));
        table.emptyText = tr("empty");
        return table;
    }

    protected static TableView.Column column(String key, float weight) {
        return new TableView.Column(tr("col." + key), weight);
    }

    protected static Dialog.Form.Field fieldDef(String key, String initial) {
        return new Dialog.Form.Field(tr("field." + key), initial, tr("field." + key + ".hint"));
    }

    protected void label(Gfx graphics, Component text, int x, int y) {
        graphics.drawString(font, text, x, y, Theme.TEXT_DIM, false);
    }

    protected void section(Gfx graphics, String key, int x, int y, int width) {
        graphics.drawString(font, tr(key).copy().withStyle(net.minecraft.ChatFormatting.BOLD), x, y, Theme.TEXT, false);
        graphics.fill(x + font.width(tr(key)) + 6, y + 4, x + width, y + 5, Theme.BORDER);
    }

    /** Lays buttons out in a grid; returns the y below the last row. */
    protected int grid(int x, int y, int width, int columns, int rowHeight, List<SuButton> buttons) {
        int gap = 4;
        int buttonWidth = (width - gap * (columns - 1)) / columns;
        for (int i = 0; i < buttons.size(); i++) {
            SuButton button = buttons.get(i);
            button.setX(x + (i % columns) * (buttonWidth + gap));
            button.setY(y + (i / columns) * rowHeight);
            button.setWidth(buttonWidth);
        }
        return y + ((buttons.size() + columns - 1) / columns) * rowHeight;
    }
}
