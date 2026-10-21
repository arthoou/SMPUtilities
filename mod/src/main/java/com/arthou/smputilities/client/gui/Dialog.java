package com.arthou.smputilities.client.gui;

import com.arthou.smputilities.mc.Texts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Modal box drawn over a {@link SuScreen}; while open it receives all input. */
public abstract class Dialog {
    protected final Font font = Minecraft.getInstance().font;
    protected final List<AbstractWidget> widgets = new ArrayList<>();
    protected final Component title;
    protected SuScreen screen;
    protected int x;
    protected int y;
    protected int width;
    protected int height;

    protected Dialog(Component title) {
        this.title = title;
    }

    void open(SuScreen owner) {
        this.screen = owner;
        widgets.clear();
        layout(owner.width, owner.height);
    }

    protected abstract void layout(int screenWidth, int screenHeight);

    protected void close() {
        screen.closeDialog(this);
    }

    protected <T extends AbstractWidget> T add(T widget) {
        widgets.add(widget);
        return widget;
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(0, 0, screen.width, screen.height, 0x90000000);
        Theme.shadow(graphics, x, y, x + width, y + height);
        Theme.roundBox(graphics, x, y, width, height, Theme.PANEL_BG, Theme.BORDER_HOVER);
        graphics.fillGradient(x + 1, y + 1, x + width - 1, y + 24, 0x226AF06B, 0x006AF06B);
        graphics.drawString(font, Theme.clip(font, title, width - 16), x + 8, y + 8, Theme.TEXT, false);
        renderBody(graphics, mouseX, mouseY);
        for (AbstractWidget widget : widgets) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.pose().popPose();
    }

    protected void renderBody(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (AbstractWidget widget : widgets) {
            boolean over = widget.isMouseOver(mouseX, mouseY);
            if (widget instanceof EditBox box) {
                box.setFocused(over);
            }
            if (over && widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return true;
    }

    boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        for (AbstractWidget widget : widgets) {
            if (widget instanceof EditBox box && box.isFocused() && box.keyPressed(key, scan, modifiers)) {
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            focusNext();
            return true;
        }
        return true;
    }

    boolean charTyped(char character, int modifiers) {
        for (AbstractWidget widget : widgets) {
            if (widget instanceof EditBox box && box.isFocused()) {
                return box.charTyped(character, modifiers);
            }
        }
        return true;
    }

    private void focusNext() {
        List<EditBox> boxes = new ArrayList<>();
        for (AbstractWidget widget : widgets) {
            if (widget instanceof EditBox box) {
                boxes.add(box);
            }
        }
        if (boxes.isEmpty()) {
            return;
        }
        int current = -1;
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).isFocused()) {
                current = i;
            }
            boxes.get(i).setFocused(false);
        }
        boxes.get((current + 1) % boxes.size()).setFocused(true);
    }

    /** Enter key. */
    protected void submit() {
    }

    protected int drawWrapped(GuiGraphics graphics, Component text, int left, int top, int maxWidth, int color) {
        int line = top;
        for (FormattedCharSequence part : font.split(text, maxWidth)) {
            graphics.drawString(font, part, left, line, color, false);
            line += 10;
        }
        return line;
    }

    // ------------------------------------------------------------- variants

    /** Yes/no question; the confirm button is red for destructive actions. */
    public static final class Confirm extends Dialog {
        private final Component message;
        private final Component confirmLabel;
        private final boolean danger;
        private final Runnable onConfirm;

        public Confirm(Component title, Component message, Component confirmLabel, boolean danger, Runnable onConfirm) {
            super(title);
            this.message = message;
            this.confirmLabel = confirmLabel;
            this.danger = danger;
            this.onConfirm = onConfirm;
        }

        @Override
        protected void layout(int screenWidth, int screenHeight) {
            width = Math.min(280, screenWidth - 40);
            int lines = font.split(message, width - 16).size();
            height = 64 + lines * 10;
            x = (screenWidth - width) / 2;
            y = (screenHeight - height) / 2;
            int buttonWidth = (width - 24) / 2;
            add(new SuButton(x + 8, y + height - 28, buttonWidth, 20, Texts.translatable("gui.smputilities.cancel"), button -> close()));
            add(new SuButton(x + width - 8 - buttonWidth, y + height - 28, buttonWidth, 20, confirmLabel, button -> submit())
                .kind(danger ? SuButton.Kind.DANGER : SuButton.Kind.PRIMARY));
        }

        @Override
        protected void renderBody(GuiGraphics graphics, int mouseX, int mouseY) {
            drawWrapped(graphics, message, x + 8, y + 30, width - 16, Theme.TEXT_DIM);
        }

        @Override
        protected void submit() {
            close();
            onConfirm.run();
        }
    }

    /** One or more labelled text fields. */
    public static final class Form extends Dialog {
        public record Field(Component label, String initial, Component hint) {
        }

        private final List<Field> fields;
        private final Component submitLabel;
        private final boolean danger;
        private final Consumer<List<String>> onSubmit;
        private final List<SuTextField> boxes = new ArrayList<>();
        private final Component note;

        public Form(Component title, List<Field> fields, Component submitLabel, boolean danger, Component note, Consumer<List<String>> onSubmit) {
            super(title);
            this.fields = fields;
            this.submitLabel = submitLabel;
            this.danger = danger;
            this.note = note;
            this.onSubmit = onSubmit;
        }

        @Override
        protected void layout(int screenWidth, int screenHeight) {
            boxes.clear();
            width = Math.min(300, screenWidth - 40);
            int noteLines = note == null ? 0 : font.split(note, width - 16).size();
            height = 68 + fields.size() * 34 + noteLines * 10;
            x = (screenWidth - width) / 2;
            y = (screenHeight - height) / 2;
            int fieldY = y + 30 + noteLines * 10;
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                SuTextField box = add(new SuTextField(x + 8, fieldY + i * 34 + 11, width - 16, 18, field.hint()));
                box.setMaxLength(1024);
                box.setValue(field.initial() == null ? "" : field.initial());
                box.setFocused(i == 0);
                boxes.add(box);
            }
            int buttonWidth = (width - 24) / 2;
            add(new SuButton(x + 8, y + height - 28, buttonWidth, 20, Texts.translatable("gui.smputilities.cancel"), button -> close()));
            add(new SuButton(x + width - 8 - buttonWidth, y + height - 28, buttonWidth, 20, submitLabel, button -> submit())
                .kind(danger ? SuButton.Kind.DANGER : SuButton.Kind.PRIMARY));
        }

        @Override
        protected void renderBody(GuiGraphics graphics, int mouseX, int mouseY) {
            int top = y + 30;
            if (note != null) {
                top = drawWrapped(graphics, note, x + 8, top, width - 16, Theme.TEXT_DIM);
            }
            for (int i = 0; i < fields.size(); i++) {
                graphics.drawString(font, fields.get(i).label(), x + 8, top + i * 34, Theme.TEXT_DIM, false);
            }
        }

        @Override
        protected void submit() {
            List<String> values = new ArrayList<>();
            for (SuTextField box : boxes) {
                values.add(box.getValue());
            }
            close();
            onSubmit.accept(values);
        }
    }

    /** A list of options as buttons. */
    public static final class Choice extends Dialog {
        public record Option(String id, Component label, SuButton.Kind kind) {
        }

        private final List<Option> options;
        private final Consumer<String> onChoose;

        public Choice(Component title, List<Option> options, Consumer<String> onChoose) {
            super(title);
            this.options = options;
            this.onChoose = onChoose;
        }

        @Override
        protected void layout(int screenWidth, int screenHeight) {
            int columns = options.size() > 6 ? 2 : 1;
            width = Math.min(columns == 2 ? 320 : 220, screenWidth - 40);
            int rows = (options.size() + columns - 1) / columns;
            height = 62 + rows * 22;
            x = (screenWidth - width) / 2;
            y = (screenHeight - height) / 2;
            int buttonWidth = (width - 16 - (columns - 1) * 6) / columns;
            for (int i = 0; i < options.size(); i++) {
                Option option = options.get(i);
                int column = i % columns;
                int row = i / columns;
                add(new SuButton(x + 8 + column * (buttonWidth + 6), y + 28 + row * 22, buttonWidth, 18, option.label(), button -> {
                    close();
                    onChoose.accept(option.id());
                }).kind(option.kind()));
            }
            add(new SuButton(x + 8, y + height - 26, width - 16, 18, Texts.translatable("gui.smputilities.cancel"), button -> close())
                .kind(SuButton.Kind.SUBTLE));
        }
    }
}
