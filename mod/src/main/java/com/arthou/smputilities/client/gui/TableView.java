package com.arthou.smputilities.client.gui;

import com.arthou.smputilities.network.Dto;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Scrollable table with a header, row selection, a text filter and small
 * action buttons at the end of each row.
 */
public class TableView extends AbstractWidget {
    public record Column(Component title, float weight) {
    }

    public record RowButton(String id, Component label, int color) {
    }

    /** Draws a row itself; return false to fall back to plain cells. */
    public interface RowRenderer {
        boolean render(GuiGraphics graphics, Dto.Row row, int x, int y, int width, int height, boolean hovered, boolean selected);
    }

    private static final int HEADER = 14;
    /** Scroll positions kept across widget rebuilds, by {@link #remember} key. */
    private static final java.util.Map<String, Float> SCROLLS = new java.util.HashMap<>();
    private String scrollKey;
    private final List<Column> columns;
    private List<Dto.Row> rows = List.of();
    private List<Dto.Row> shown = List.of();
    private String filter = "";
    private float scroll;
    private float scrollTarget;
    public int rowHeight = 14;
    public String selectedId = "";
    public Consumer<Dto.Row> onSelect = row -> {
    };
    public Function<Dto.Row, List<RowButton>> buttons = row -> List.of();
    public BiConsumer<Dto.Row, String> onButton = (row, button) -> {
    };
    public RowRenderer rowRenderer;
    public Component emptyText = Component.empty();

    public TableView(int x, int y, int width, int height, List<Column> columns) {
        super(x, y, width, height, Component.empty());
        this.columns = columns;
    }

    /** Keeps the scroll position under {@code key} when the screen rebuilds its widgets. */
    public TableView remember(String key) {
        scrollKey = key;
        scrollTarget = SCROLLS.getOrDefault(key, 0F);
        scroll = scrollTarget;
        return this;
    }

    public void setRows(List<Dto.Row> value) {
        rows = value == null ? List.of() : value;
        applyFilter();
    }

    public void setFilter(String value) {
        filter = value == null ? "" : value.toLowerCase(Locale.ROOT);
        applyFilter();
    }

    private void applyFilter() {
        if (filter.isEmpty()) {
            shown = rows;
        } else {
            List<Dto.Row> result = new ArrayList<>();
            for (Dto.Row row : rows) {
                for (String cell : row.cells()) {
                    if (cell.toLowerCase(Locale.ROOT).contains(filter)) {
                        result.add(row);
                        break;
                    }
                }
            }
            shown = result;
        }
        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll());
    }

    private boolean showHeader() {
        return !columns.isEmpty();
    }

    private int bodyTop() {
        return getY() + (showHeader() ? HEADER : 0);
    }

    private int maxScroll() {
        return Math.max(0, shown.size() * rowHeight - (getY() + getHeight() - bodyTop()));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        scroll = Theme.approach(scroll, scrollTarget, 16, 60);
        int x = getX();
        int width = getWidth();
        Theme.roundBox(graphics, x, getY(), width, getHeight(), 0xFF12161C, Theme.BORDER);
        if (showHeader()) {
            graphics.fill(x + 1, getY() + 1, x + width - 1, getY() + HEADER, 0xFF1A1F27);
            int cx = x + 6;
            float total = totalWeight();
            for (Column column : columns) {
                int columnWidth = (int) ((width - 12 - buttonAreaWidth(null)) * column.weight() / total);
                graphics.drawString(font, Theme.clip(font, column.title(), columnWidth - 4), cx, getY() + 4, Theme.TEXT_DIM, false);
                cx += columnWidth;
            }
        }
        int top = bodyTop();
        int bottom = getY() + getHeight() - 1;
        graphics.enableScissor(x + 1, top, x + width - 1, bottom);
        if (shown.isEmpty()) {
            graphics.drawCenteredString(font, emptyText, x + width / 2, top + 10, Theme.TEXT_OFF);
        }
        for (int i = 0; i < shown.size(); i++) {
            int rowY = top + i * rowHeight - Math.round(scroll);
            if (rowY + rowHeight < top || rowY > bottom) {
                continue;
            }
            Dto.Row row = shown.get(i);
            boolean hovered = isMouseOver(mouseX, mouseY) && mouseY >= rowY && mouseY < rowY + rowHeight && mouseY >= top;
            boolean selected = row.id().equals(selectedId);
            if (selected) {
                graphics.fill(x + 1, rowY, x + width - 1, rowY + rowHeight, Theme.CARD_SELECTED);
                graphics.fill(x + 1, rowY, x + 3, rowY + rowHeight, Theme.ACCENT);
            } else if (hovered) {
                graphics.fill(x + 1, rowY, x + width - 1, rowY + rowHeight, Theme.CARD_HOVER);
            } else if (i % 2 == 1) {
                graphics.fill(x + 1, rowY, x + width - 1, rowY + rowHeight, Theme.ROW_ALT);
            }
            int buttonsWidth = buttonAreaWidth(row);
            if (rowRenderer == null || !rowRenderer.render(graphics, row, x + 4, rowY, width - 8 - buttonsWidth, rowHeight, hovered, selected)) {
                int cx = x + 6;
                float total = totalWeight();
                for (int c = 0; c < columns.size() && c < row.cells().size(); c++) {
                    int columnWidth = (int) ((width - 12 - buttonAreaWidth(null)) * columns.get(c).weight() / total);
                    graphics.drawString(font, Theme.clip(font, row.cells().get(c), columnWidth - 4), cx, rowY + (rowHeight - 8) / 2,
                        c == 0 ? Theme.TEXT : Theme.TEXT_DIM, false);
                    cx += columnWidth;
                }
            }
            int bx = x + width - 4;
            List<RowButton> rowButtons = buttons.apply(row);
            for (int b = rowButtons.size() - 1; b >= 0; b--) {
                RowButton button = rowButtons.get(b);
                int bw = font.width(button.label()) + 8;
                bx -= bw;
                boolean overButton = hovered && mouseX >= bx && mouseX < bx + bw;
                Theme.roundRect(graphics, bx, rowY + 1, bx + bw, rowY + rowHeight - 1, Theme.withAlpha(button.color(), overButton ? 0.45F : 0.2F));
                graphics.drawString(font, button.label(), bx + 4, rowY + (rowHeight - 8) / 2, button.color(), false);
                bx -= 3;
            }
        }
        graphics.disableScissor();
        int max = maxScroll();
        if (max > 0) {
            int trackHeight = bottom - top;
            int thumb = Math.max(12, trackHeight * trackHeight / (trackHeight + max));
            int thumbY = top + Math.round((trackHeight - thumb) * (scroll / max));
            graphics.fill(x + width - 4, thumbY, x + width - 2, thumbY + thumb, Theme.BORDER_HOVER);
        }
    }

    private float totalWeight() {
        float total = 0;
        for (Column column : columns) {
            total += column.weight();
        }
        return total <= 0 ? 1 : total;
    }

    /** Width reserved for row buttons, so columns line up even on rows without buttons. */
    private int buttonAreaWidth(Dto.Row row) {
        Font font = Minecraft.getInstance().font;
        int widest = 0;
        List<Dto.Row> sample = row != null ? List.of(row) : shown.isEmpty() ? List.of() : List.of(shown.get(0));
        for (Dto.Row candidate : sample) {
            int width = 0;
            for (RowButton button : buttons.apply(candidate)) {
                width += font.width(button.label()) + 11;
            }
            widest = Math.max(widest, width);
        }
        return widest;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY) || mouseY < bodyTop()) {
            return false;
        }
        int index = (int) ((mouseY - bodyTop() + scroll) / rowHeight);
        if (index < 0 || index >= shown.size()) {
            return true;
        }
        Dto.Row row = shown.get(index);
        Font font = Minecraft.getInstance().font;
        int bx = getX() + getWidth() - 4;
        List<RowButton> rowButtons = buttons.apply(row);
        for (int b = rowButtons.size() - 1; b >= 0; b--) {
            RowButton rowButton = rowButtons.get(b);
            int bw = font.width(rowButton.label()) + 8;
            bx -= bw;
            if (mouseX >= bx && mouseX < bx + bw) {
                playDownSound(Minecraft.getInstance().getSoundManager());
                onButton.accept(row, rowButton.id());
                return true;
            }
            bx -= 3;
        }
        selectedId = row.id();
        playDownSound(Minecraft.getInstance().getSoundManager());
        onSelect.accept(row);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scrollTarget = Mth.clamp(scrollTarget - (float) scrollY * rowHeight * 2, 0, maxScroll());
        if (scrollKey != null) {
            SCROLLS.put(scrollKey, scrollTarget);
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
