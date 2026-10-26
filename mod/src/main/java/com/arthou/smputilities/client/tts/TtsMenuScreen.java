package com.arthou.smputilities.client.tts;

import com.arthou.smputilities.client.gui.Widget;
import com.arthou.smputilities.client.gui.Gfx;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.client.ClientNetwork;
import com.arthou.smputilities.client.gui.SuScreen;
import com.arthou.smputilities.client.gui.Theme;
import com.arthou.smputilities.network.Payloads;
import com.arthou.smputilities.tts.TtsPresets;
import com.arthou.smputilities.tts.TtsPresets.VoiceLanguage;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.function.Consumer;

/**
 * /su tts menu: a small setup with the steps in a side bar (gender, then
 * variation, then language). Confirm sends the choice; the button at the
 * bottom of the side bar turns the voice off.
 */
public final class TtsMenuScreen extends SuScreen {
    private static final int GENDER_FEMININE = 0;
    private static final int GENDER_MASCULINE = 1;
    private static final int[] VARIANTS = {3, 2};

    private static final int STEP_GENDER = 0;
    private static final int STEP_VARIANT = 1;
    private static final int STEP_LANGUAGE = 2;
    private static final int STEP_COUNT = 3;
    private static final int STEP_SPACING = 38;

    private static final int FEMININE = 0xFFFF8FC8;
    private static final int MASCULINE = 0xFF7FB6FF;

    private final int currentMode;
    private final int currentLanguage;

    private int gender = -1;
    private int variant = -1;
    private int language = -1;
    private int step = STEP_GENDER;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int sideWidth;

    private TtsMenuScreen(int currentMode, int currentLanguage) {
        super(Texts.translatable("tts.title"));
        this.currentMode = currentMode;
        this.currentLanguage = currentLanguage;

        if (currentMode >= TtsPresets.MODE_FEMININE_1 && currentMode <= TtsPresets.MODE_FEMININE_3) {
            gender = GENDER_FEMININE;
            variant = currentMode;
        } else if (currentMode >= TtsPresets.MODE_MASCULINE_1 && currentMode <= TtsPresets.MODE_MASCULINE_2) {
            gender = GENDER_MASCULINE;
            variant = currentMode - TtsPresets.MODE_FEMININE_3;
        }
        if (currentMode > 0 && currentLanguage >= 0 && currentLanguage < VoiceLanguage.values().length) {
            language = currentLanguage;
        }
    }

    public static void open(int mode, int language) {
        Minecraft.getInstance().setScreen(new TtsMenuScreen(mode, language));
    }

    @Override
    protected void build() {
        panelWidth = Math.min(400, width - 20);
        panelHeight = Math.min(236, height - 20);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        sideWidth = Math.max(104, Math.min(132, panelWidth / 3));

        // --- side bar: the three steps, then "turn off" at the bottom ---
        boolean[] reachable = {true, gender >= 0, gender >= 0 && variant > 0};
        for (int i = 0; i < STEP_COUNT; i++) {
            int index = i;
            StepButton item = new StepButton(panelLeft + 6, stepY(i), sideWidth - 12, 32, index, button -> {
                step = index;
                rebuild();
            });
            item.active = reachable[i];
            addWidget(item);
        }

        FlatButton off = new FlatButton(panelLeft + 8, panelTop + panelHeight - 30, sideWidth - 16, 20,
            Texts.translatable("tts.off"), button -> turnOff());
        off.kind = Kind.DANGER;
        off.active = currentMode > 0;
        addWidget(off);

        // --- content: options of the current step ---
        int contentLeft = contentLeft();
        int contentWidth = contentRight() - contentLeft;
        int optionsTop = panelTop + 56;

        switch (step) {
            case STEP_GENDER -> {
                int cardWidth = (contentWidth - 8) / 2;
                for (int g = 0; g < 2; g++) {
                    int value = g;
                    OptionCard card = new OptionCard(contentLeft + g * (cardWidth + 8), optionsTop, cardWidth, 70,
                        genderName(g), button -> pickGender(value));
                    card.big = g == GENDER_MASCULINE ? "♂" : "♀";
                    card.bigColor = g == GENDER_MASCULINE ? MASCULINE : FEMININE;
                    card.subtitle = Texts.translatable("tts.gender.variants", VARIANTS[g]);
                    card.selected = gender == g;
                    addWidget(card);
                }
            }
            case STEP_VARIANT -> {
                int count = VARIANTS[gender];
                int cardWidth = (contentWidth - 8 * (count - 1)) / count;
                for (int v = 1; v <= count; v++) {
                    int value = v;
                    OptionCard card = new OptionCard(contentLeft + (v - 1) * (cardWidth + 8), optionsTop, cardWidth, 70,
                        Texts.literal(speakerName(modeFor(gender, v))), button -> pickVariant(value));
                    card.big = Integer.toString(v);
                    card.bigColor = gender == GENDER_MASCULINE ? MASCULINE : FEMININE;
                    card.subtitle = Texts.translatable("tts.variant", v);
                    card.selected = variant == v;
                    addWidget(card);
                }
            }
            default -> {
                VoiceLanguage[] languages = VoiceLanguage.values();
                int columnWidth = (contentWidth - 8) / 2;
                for (int i = 0; i < languages.length; i++) {
                    int value = i;
                    LanguageRow row = new LanguageRow(contentLeft + (i % 2) * (columnWidth + 8), optionsTop + (i / 2) * 25,
                        columnWidth, 21, languages[i], button -> pickLanguage(value));
                    row.selected = language == i;
                    addWidget(row);
                }
            }
        }

        // --- footer ---
        int buttonWidth = Math.min(86, (contentWidth - 8) / 2);
        int footerY = panelTop + panelHeight - 30;
        int right = contentRight();
        FlatButton confirm = new FlatButton(right - buttonWidth, footerY, buttonWidth, 20,
            Texts.translatable("tts.confirm"), button -> confirm());
        confirm.kind = Kind.PRIMARY;
        confirm.active = isComplete();
        addWidget(confirm);
        addWidget(new FlatButton(right - buttonWidth * 2 - 6, footerY, buttonWidth, 20,
            Texts.translatable("tts.cancel"), button -> onClose()));
    }

    private int stepY(int index) {
        return panelTop + 50 + index * STEP_SPACING;
    }

    private int contentLeft() {
        return panelLeft + sideWidth + 14;
    }

    private int contentRight() {
        return panelLeft + panelWidth - 14;
    }

    // ------------------------------------------------------------- actions

    private void pickGender(int value) {
        if (gender != value) {
            gender = value;
            if (variant > VARIANTS[value]) {
                variant = -1;
            }
        }
        step = STEP_VARIANT;
        rebuild();
    }

    private void pickVariant(int value) {
        variant = value;
        step = STEP_LANGUAGE;
        rebuild();
    }

    private void pickLanguage(int value) {
        language = value;
        rebuild();
    }

    private boolean isComplete() {
        return gender >= 0 && variant > 0 && language >= 0;
    }

    private boolean isDone(int index) {
        return switch (index) {
            case STEP_GENDER -> gender >= 0;
            case STEP_VARIANT -> variant > 0;
            default -> language >= 0;
        };
    }

    private void confirm() {
        if (!isComplete()) {
            return;
        }
        ClientNetwork.send(new Payloads.TtsSelect(modeFor(gender, variant), language));
        onClose();
    }

    private void turnOff() {
        ClientNetwork.send(new Payloads.TtsSelect(0, -1));
        onClose();
    }

    // -------------------------------------------------------------- labels

    private static int modeFor(int gender, int variant) {
        return gender == GENDER_MASCULINE ? TtsPresets.MODE_FEMININE_3 + variant : variant;
    }

    /** "Amazon US English (Ivy)" -> "Ivy", "RHVoice ... (Letícia-F123)" -> "Letícia". */
    private String speakerName(int mode) {
        VoiceLanguage voiceLanguage = language >= 0 ? VoiceLanguage.values()[language] : VoiceLanguage.ENGLISH;
        String voice = TtsPresets.resolveVoiceName(voiceLanguage, mode);
        int open = voice.lastIndexOf('(');
        int close = voice.lastIndexOf(')');
        String name = open >= 0 && close > open ? voice.substring(open + 1, close) : voice;
        int dash = name.indexOf('-');
        return dash > 0 ? name.substring(0, dash) : name;
    }

    private static Component genderName(int gender) {
        return Texts.translatable(gender == GENDER_MASCULINE ? "tts.gender.masculine" : "tts.gender.feminine");
    }

    private Component stepName(int index) {
        return Texts.translatable(switch (index) {
            case STEP_GENDER -> "tts.step.gender";
            case STEP_VARIANT -> "tts.step.variant";
            default -> "tts.step.language";
        });
    }

    private Component stepValue(int index) {
        return switch (index) {
            case STEP_GENDER -> gender >= 0 ? genderName(gender) : null;
            case STEP_VARIANT -> variant > 0
                ? Texts.translatable("tts.variant", variant).append(" · " + speakerName(modeFor(gender, variant)))
                : null;
            default -> language >= 0 ? TtsPresets.languageComponent(VoiceLanguage.values()[language]) : null;
        };
    }

    private Component stepTitle() {
        return Texts.translatable(switch (step) {
            case STEP_GENDER -> "tts.pick.gender";
            case STEP_VARIANT -> "tts.pick.variant";
            default -> "tts.pick.language";
        });
    }

    private Component status() {
        if (currentMode <= 0) {
            return Texts.translatable("tts.status.off");
        }
        VoiceLanguage[] languages = VoiceLanguage.values();
        VoiceLanguage active = currentLanguage >= 0 && currentLanguage < languages.length ? languages[currentLanguage] : VoiceLanguage.ENGLISH;
        return Texts.translatable("tts.status.on", TtsPresets.selectionComponent(currentMode, active));
    }

    // ------------------------------------------------------------ painting

    @Override
    protected void renderBack(Gfx graphics, int mouseX, int mouseY, float partialTick) {

        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;

        // soft shadow, panel, side bar
        for (int i = 6; i >= 1; i--) {
            Theme.roundRect(graphics, panelLeft - i, panelTop - i + 2, right + i, bottom + i + 2, (0x12 - i) << 24);
        }
        Theme.roundRect(graphics, panelLeft - 1, panelTop - 1, right + 1, bottom + 1, Theme.BORDER);
        Theme.roundRect(graphics, panelLeft, panelTop, right, bottom, Theme.PANEL_BG);
        graphics.fill(panelLeft + 1, panelTop, panelLeft + sideWidth, panelTop + 1, Theme.SIDEBAR_BG);
        graphics.fill(panelLeft, panelTop + 1, panelLeft + sideWidth, bottom - 1, Theme.SIDEBAR_BG);
        graphics.fill(panelLeft + 1, bottom - 1, panelLeft + sideWidth, bottom, Theme.SIDEBAR_BG);
        graphics.fill(panelLeft + sideWidth, panelTop, panelLeft + sideWidth + 1, bottom, Theme.BORDER);
        graphics.fillGradient(panelLeft + sideWidth + 1, panelTop, right - 1, panelTop + 46, 0x1A6AF06B, 0x006AF06B);

        // side bar header: logo badge + name
        int logoX = panelLeft + 10;
        int logoY = panelTop + 12;
        Theme.roundRect(graphics, logoX, logoY, logoX + 20, logoY + 20, Theme.ACCENT);
        graphics.drawString(font, "♪", logoX + 7, logoY + 6, 0xFF0B1A0C, false);
        graphics.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), logoX + 26, logoY + 2, Theme.TEXT, false);
        graphics.drawString(font, Theme.clip(font, Texts.translatable("tts.subtitle"), sideWidth - 42), logoX + 26, logoY + 12, Theme.TEXT_DIM, false);

        // stepper connectors between the step badges
        for (int i = 0; i < STEP_COUNT - 1; i++) {
            int lineX = panelLeft + 6 + 6 + 6;
            int top = stepY(i) + 8 + 14;
            int end = stepY(i + 1) + 8;
            graphics.fill(lineX, top + 2, lineX + 1, end - 2, isDone(i) ? Theme.ACCENT_DARK : Theme.BORDER);
        }

        // content header
        int contentLeft = contentLeft();
        int contentRight = contentRight();
        Component progress = Texts.translatable("tts.step_of", step + 1, STEP_COUNT);
        graphics.drawString(font, progress.copy().withStyle(ChatFormatting.BOLD), contentLeft, panelTop + 12, Theme.ACCENT, false);
        graphics.drawString(font, Theme.clip(font, stepTitle().copy().withStyle(ChatFormatting.BOLD), contentRight - contentLeft), contentLeft, panelTop + 26, Theme.TEXT, false);

        // status pill, top right
        boolean on = currentMode > 0;
        FormattedCharSequence pill = Theme.clip(font, status(), Math.max(20, contentRight - contentLeft - font.width(progress) - 30));
        int pillWidth = font.width(pill) + 18;
        int pillX = contentRight - pillWidth;
        Theme.roundRect(graphics, pillX, panelTop + 9, contentRight, panelTop + 23, on ? 0x332F7A3A : 0x33454B57);
        graphics.fill(pillX + 6, panelTop + 14, pillX + 10, panelTop + 18, on ? Theme.ACCENT : Theme.TEXT_DIM);
        graphics.drawString(font, pill, pillX + 13, panelTop + 12, on ? Theme.ACCENT : Theme.TEXT_DIM, false);

        graphics.fill(contentLeft, panelTop + 42, contentRight, panelTop + 43, Theme.BORDER);
        graphics.fill(contentLeft, panelTop + panelHeight - 38, contentRight, panelTop + panelHeight - 37, Theme.BORDER);
    }






    /** Small flag for each voice language, painted with plain fills (16x11). */
    private static void flag(Gfx g, int x, int y, VoiceLanguage language) {
        int w = 16;
        int h = 11;
        switch (language) {
            case ENGLISH -> {
                for (int row = 0; row < h; row++) {
                    g.fill(x, y + row, x + w, y + row + 1, row % 2 == 0 ? 0xFFB22234 : 0xFFFFFFFF);
                }
                g.fill(x, y, x + 7, y + 6, 0xFF3C3B6E);
            }
            case SPANISH -> {
                g.fill(x, y, x + w, y + h, 0xFFAA151B);
                g.fill(x, y + 3, x + w, y + 8, 0xFFF1BF00);
            }
            case PORTUGUESE -> {
                g.fill(x, y, x + w, y + h, 0xFF009C3B);
                for (int row = 1; row < h - 1; row++) {
                    int half = 7 - Math.abs(row - 5) * 7 / 4;
                    if (half > 0) {
                        g.fill(x + 8 - half, y + row, x + 8 + half, y + row + 1, 0xFFFFDF00);
                    }
                }
                g.fill(x + 6, y + 3, x + 10, y + 8, 0xFF002776);
                g.fill(x + 5, y + 4, x + 11, y + 7, 0xFF002776);
            }
            case POLISH -> {
                g.fill(x, y, x + w, y + 5, 0xFFFFFFFF);
                g.fill(x, y + 5, x + w, y + h, 0xFFDC143C);
            }
            case FRENCH -> {
                g.fill(x, y, x + 5, y + h, 0xFF0055A4);
                g.fill(x + 5, y, x + 11, y + h, 0xFFFFFFFF);
                g.fill(x + 11, y, x + w, y + h, 0xFFEF4135);
            }
            case GERMAN -> {
                g.fill(x, y, x + w, y + 4, 0xFF000000);
                g.fill(x, y + 4, x + w, y + 7, 0xFFDD0000);
                g.fill(x, y + 7, x + w, y + h, 0xFFFFCE00);
            }
            case ITALIAN -> {
                g.fill(x, y, x + 5, y + h, 0xFF009246);
                g.fill(x + 5, y, x + 11, y + h, 0xFFFFFFFF);
                g.fill(x + 11, y, x + w, y + h, 0xFFCE2B37);
            }
            case DUTCH -> {
                g.fill(x, y, x + w, y + 4, 0xFFAE1C28);
                g.fill(x, y + 4, x + w, y + 7, 0xFFFFFFFF);
                g.fill(x, y + 7, x + w, y + h, 0xFF21468B);
            }
        }
        g.outline(x - 1, y - 1, w + 2, h + 2, 0x50000000);
    }

    // ------------------------------------------------------------- widgets

    private enum Kind { GHOST, PRIMARY, DANGER }

    /** Base with a smoothed hover, so colours fade instead of snapping. */
    private abstract static class AnimatedButton extends Widget {
        private final Consumer<AnimatedButton> action;
        private float hover;
        private long lastFrame = Util.getMillis();

        AnimatedButton(int x, int y, int width, int height, Component message, Consumer<AnimatedButton> action) {
            super(x, y, width, height, message);
            this.action = action;
        }

        @Override
        protected void onClick(double mouseX, double mouseY) {
            action.accept(this);
        }

        float hover() {
            long now = Util.getMillis();
            float step = Math.min(1.0F, (now - lastFrame) / 90.0F);
            lastFrame = now;
            float target = active && isHoveredOrFocused() ? 1.0F : 0.0F;
            hover += (target - hover) * step;
            return hover;
        }
    }

    private final class FlatButton extends AnimatedButton {
        Kind kind = Kind.GHOST;

        @SuppressWarnings("unchecked")
        FlatButton(int x, int y, int width, int height, Component message, Consumer<FlatButton> action) {
            super(x, y, width, height, message, (Consumer<AnimatedButton>) (Consumer<?>) action);
        }

        @Override
        protected void renderWidget(Gfx graphics, int mouseX, int mouseY, float partialTick) {
            float t = hover();
            int background;
            int border;
            int textColor;
            switch (kind) {
                case PRIMARY -> {
                    background = active ? Theme.mix(Theme.ACCENT, 0xFF9AF79B, t) : Theme.CARD;
                    border = active ? background : Theme.BORDER;
                    textColor = active ? 0xFF0B1A0C : Theme.TEXT_OFF;
                }
                case DANGER -> {
                    background = active ? Theme.mix(0x00FF625F, 0x40FF625F, t) : 0x00000000;
                    border = active ? Theme.mix(0x80FF625F, Theme.DANGER, t) : Theme.BORDER;
                    textColor = active ? Theme.DANGER : Theme.TEXT_OFF;
                }
                default -> {
                    background = Theme.mix(Theme.CARD, Theme.CARD_HOVER, t);
                    border = Theme.mix(Theme.BORDER, 0xFF3C4452, t);
                    textColor = Theme.TEXT;
                }
            }
            Theme.roundBox(graphics, getX(), getY(), getWidth(), getHeight(), background, border);
            Component label = kind == Kind.PRIMARY && active ? Texts.literal("✔ ").append(getMessage()) : getMessage();
            FormattedCharSequence text = Theme.clip(font, label.copy().withStyle(ChatFormatting.BOLD), getWidth() - 8);
            graphics.drawString(font, text, getX() + (getWidth() - font.width(text)) / 2, getY() + (getHeight() - 8) / 2, textColor, false);
        }
    }

    /** One step of the side bar: badge (number or check), name, chosen value. */
    private final class StepButton extends AnimatedButton {
        private final int index;

        @SuppressWarnings("unchecked")
        StepButton(int x, int y, int width, int height, int index, Consumer<StepButton> action) {
            super(x, y, width, height, stepName(index), (Consumer<AnimatedButton>) (Consumer<?>) action);
            this.index = index;
        }

        @Override
        protected void renderWidget(Gfx graphics, int mouseX, int mouseY, float partialTick) {
            float t = hover();
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            boolean current = step == index;
            boolean done = isDone(index);

            if (current) {
                Theme.roundRect(graphics, x, y, x + w, y + h, Theme.CARD);
                graphics.fill(x, y + 4, x + 2, y + h - 4, Theme.ACCENT);
            } else if (t > 0.01F) {
                Theme.roundRect(graphics, x, y, x + w, y + h, Theme.mix(0x00232A34, Theme.CARD_HOVER, t));
            }

            // badge
            int bx = x + 6;
            int by = y + 8;
            if (done && !current) {
                Theme.roundRect(graphics, bx, by, bx + 14, by + 14, Theme.ACCENT_DARK);
                graphics.drawString(font, "✔", bx + 4, by + 3, Theme.ACCENT, false);
            } else if (current) {
                Theme.roundRect(graphics, bx, by, bx + 14, by + 14, Theme.ACCENT);
                graphics.drawString(font, Integer.toString(index + 1), bx + 5, by + 3, 0xFF0B1A0C, false);
            } else {
                Theme.roundBox(graphics, bx, by, 14, 14, Theme.SIDEBAR_BG, active ? 0xFF3C4452 : Theme.BORDER);
                graphics.drawString(font, Integer.toString(index + 1), bx + 5, by + 3, active ? Theme.TEXT_DIM : Theme.TEXT_OFF, false);
            }

            int textX = bx + 20;
            int textWidth = x + w - textX - 4;
            int nameColor = !active ? Theme.TEXT_OFF : current ? Theme.TEXT : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, t);
            Component value = stepValue(index);
            if (value == null) {
                graphics.drawString(font, Theme.clip(font, getMessage(), textWidth), textX, y + (h - 8) / 2, nameColor, false);
            } else {
                graphics.drawString(font, Theme.clip(font, getMessage(), textWidth), textX, y + 6, nameColor, false);
                graphics.drawString(font, Theme.clip(font, value, textWidth), textX, y + 18, active ? Theme.ACCENT : Theme.TEXT_OFF, false);
            }
        }
    }

    /** Big card for gender and variation: large glyph, title, subtitle. */
    private final class OptionCard extends AnimatedButton {
        String big = "";
        int bigColor = Theme.TEXT;
        Component subtitle;
        boolean selected;

        @SuppressWarnings("unchecked")
        OptionCard(int x, int y, int width, int height, Component message, Consumer<OptionCard> action) {
            super(x, y, width, height, message, (Consumer<AnimatedButton>) (Consumer<?>) action);
        }

        @Override
        protected void renderWidget(Gfx graphics, int mouseX, int mouseY, float partialTick) {
            float t = hover();
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            int background = selected ? Theme.CARD_SELECTED : Theme.mix(Theme.CARD, Theme.CARD_HOVER, t);
            int border = selected ? Theme.ACCENT : Theme.mix(Theme.BORDER, 0xFF3C4452, t);
            Theme.roundBox(graphics, x, y, w, h, background, border);
            if (selected) {
                graphics.drawString(font, "✔", x + w - 11, y + 4, Theme.ACCENT, false);
            }

            // glyph at 2x, lifted a little on hover
            int lift = Math.round(t * 2);
            graphics.push();
            graphics.translate(x + w / 2.0F - font.width(big), y + 9 - lift);
            graphics.scale(2.0F);
            graphics.drawString(font, big, 0, 0, bigColor, false);
            graphics.pop();

            FormattedCharSequence title = Theme.clip(font, getMessage().copy().withStyle(ChatFormatting.BOLD), w - 8);
            graphics.drawString(font, title, x + (w - font.width(title)) / 2, y + 36, Theme.TEXT, false);
            if (subtitle != null) {
                FormattedCharSequence sub = Theme.clip(font, subtitle, w - 8);
                graphics.drawString(font, sub, x + (w - font.width(sub)) / 2, y + 50, selected ? Theme.ACCENT : Theme.TEXT_DIM, false);
            }
        }
    }

    /** Language option: flag, name, check when chosen. */
    private final class LanguageRow extends AnimatedButton {
        private final VoiceLanguage voiceLanguage;
        boolean selected;

        @SuppressWarnings("unchecked")
        LanguageRow(int x, int y, int width, int height, VoiceLanguage voiceLanguage, Consumer<LanguageRow> action) {
            super(x, y, width, height, TtsPresets.languageComponent(voiceLanguage), (Consumer<AnimatedButton>) (Consumer<?>) action);
            this.voiceLanguage = voiceLanguage;
        }

        @Override
        protected void renderWidget(Gfx graphics, int mouseX, int mouseY, float partialTick) {
            float t = hover();
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            int background = selected ? Theme.CARD_SELECTED : Theme.mix(Theme.CARD, Theme.CARD_HOVER, t);
            int border = selected ? Theme.ACCENT : Theme.mix(Theme.BORDER, 0xFF3C4452, t);
            Theme.roundBox(graphics, x, y, w, h, background, border);
            flag(graphics, x + 6, y + (h - 11) / 2, voiceLanguage);
            int textX = x + 28;
            graphics.drawString(font, Theme.clip(font, getMessage(), w - 44), textX, y + (h - 8) / 2, selected ? Theme.TEXT : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, t), false);
            if (selected) {
                graphics.drawString(font, "✔", x + w - 12, y + (h - 8) / 2, Theme.ACCENT, false);
            }
        }
    }
}
