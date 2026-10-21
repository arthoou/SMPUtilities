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
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.function.Consumer;

/**
 * /su tts: the voice studio. One page: the five voices (named after their
 * speaker) on the left, the languages on the right, a live waveform in the
 * voice's color on top, and Activate / Turn off at the bottom.
 */
public final class TtsMenuScreen extends SuScreen {
    private static final int[] MODES = {TtsPresets.MODE_FEMININE_1, TtsPresets.MODE_FEMININE_2, TtsPresets.MODE_FEMININE_3,
        TtsPresets.MODE_MASCULINE_1, TtsPresets.MODE_MASCULINE_2};
    private static final int FEMININE = 0xFFFF8FC8;
    private static final int MASCULINE = 0xFF7FB6FF;
    /** The studio's own accent, so it does not look like the staff panel. */
    private static final int STUDIO = 0xFFB18CFF;
    private static final int HEADER = 46;
    private static final int BARS = 34;

    private final int currentMode;
    private final int currentLanguage;
    private final long openedAt = Util.getMillis();

    private int mode;
    private int language = -1;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int splitX;

    private TtsMenuScreen(int currentMode, int currentLanguage) {
        super(Texts.translatable("tts.title"));
        this.currentMode = currentMode;
        this.currentLanguage = currentLanguage;
        this.mode = currentMode >= TtsPresets.MODE_FEMININE_1 && currentMode <= TtsPresets.MODE_MASCULINE_2 ? currentMode : 0;
        if (currentMode > 0 && currentLanguage >= 0 && currentLanguage < VoiceLanguage.values().length) {
            language = currentLanguage;
        }
    }

    public static void open(int mode, int language) {
        Minecraft.getInstance().setScreen(new TtsMenuScreen(mode, language));
    }

    @Override
    protected void build() {
        panelWidth = Math.min(420, width - 20);
        panelHeight = Math.min(232, height - 20);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        int inner = panelWidth - 28;
        splitX = panelLeft + 14 + inner * 2 / 5;
        int bodyTop = panelTop + HEADER + 22;

        // voices
        int voiceWidth = splitX - panelLeft - 14 - 8;
        int rowHeight = Math.max(18, Math.min(22, (panelTop + panelHeight - 40 - bodyTop) / MODES.length - 2));
        for (int i = 0; i < MODES.length; i++) {
            int value = MODES[i];
            VoiceRow row = new VoiceRow(panelLeft + 14, bodyTop + i * (rowHeight + 2), voiceWidth, rowHeight, value, button -> {
                mode = value;
                rebuild();
            });
            row.selected = mode == value;
            addWidget(row);
        }

        // languages
        VoiceLanguage[] languages = VoiceLanguage.values();
        int languagesLeft = splitX + 8;
        int columnWidth = (panelLeft + panelWidth - 14 - languagesLeft - 6) / 2;
        int languageHeight = Math.max(18, Math.min(22, (panelTop + panelHeight - 40 - bodyTop) / 4 - 3));
        for (int i = 0; i < languages.length; i++) {
            int value = i;
            LanguageRow row = new LanguageRow(languagesLeft + (i % 2) * (columnWidth + 6), bodyTop + (i / 2) * (languageHeight + 3), columnWidth,
                languageHeight, languages[i], button -> {
                    language = value;
                    rebuild();
                });
            row.selected = language == i;
            addWidget(row);
        }

        // footer
        int footerY = panelTop + panelHeight - 28;
        int right = panelLeft + panelWidth - 14;
        int buttonWidth = Math.min(92, (panelWidth - 28) / 4);
        FlatButton activate = new FlatButton(right - buttonWidth, footerY, buttonWidth, 20, Texts.translatable("tts.activate"), button -> confirm());
        activate.kind = Kind.PRIMARY;
        activate.active = mode > 0 && language >= 0;
        addWidget(activate);
        FlatButton off = new FlatButton(right - buttonWidth * 2 - 6, footerY, buttonWidth, 20, Texts.translatable("tts.off"), button -> turnOff());
        off.kind = Kind.DANGER;
        off.active = currentMode > 0;
        addWidget(off);
    }

    // ------------------------------------------------------------- actions

    private void confirm() {
        if (mode > 0 && language >= 0) {
            ClientNetwork.send(new Payloads.TtsSelect(mode, language));
            onClose();
        }
    }

    private void turnOff() {
        ClientNetwork.send(new Payloads.TtsSelect(0, -1));
        onClose();
    }

    // -------------------------------------------------------------- labels

    private static boolean masculine(int mode) {
        return mode == TtsPresets.MODE_MASCULINE_1 || mode == TtsPresets.MODE_MASCULINE_2;
    }

    private static int variant(int mode) {
        return masculine(mode) ? mode - TtsPresets.MODE_FEMININE_3 : mode;
    }

    private static int voiceColor(int mode) {
        return mode <= 0 ? Theme.TEXT_OFF : masculine(mode) ? MASCULINE : FEMININE;
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

    private Component status() {
        if (currentMode <= 0) {
            return Texts.translatable("tts.status.off");
        }
        VoiceLanguage[] languages = VoiceLanguage.values();
        VoiceLanguage active = currentLanguage >= 0 && currentLanguage < languages.length ? languages[currentLanguage] : VoiceLanguage.ENGLISH;
        return Texts.translatable("tts.status.on", TtsPresets.selectionComponent(currentMode, active));
    }

    private Component summary() {
        if (mode <= 0 || language < 0) {
            return Texts.translatable("tts.summary.pick");
        }
        return Texts.literal(speakerName(mode) + " · ").append(TtsPresets.languageComponent(VoiceLanguage.values()[language]));
    }

    // ------------------------------------------------------------ painting

    @Override
    protected void renderBack(Gfx graphics, int mouseX, int mouseY, float partialTick) {
        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;
        for (int i = 6; i >= 1; i--) {
            Theme.roundRect(graphics, panelLeft - i, panelTop - i + 2, right + i, bottom + i + 2, (0x12 - i) << 24);
        }
        Theme.roundRect(graphics, panelLeft - 1, panelTop - 1, right + 1, bottom + 1, Theme.BORDER);
        Theme.roundRect(graphics, panelLeft, panelTop, right, bottom, Theme.PANEL_BG);

        // header: waveform in the chosen voice's color behind the title
        int color = mode > 0 ? voiceColor(mode) : STUDIO;
        graphics.fillGradient(panelLeft + 1, panelTop + 1, right - 1, panelTop + HEADER, Theme.withAlpha(color, 0.16F), Theme.withAlpha(color, 0.02F));
        waveform(graphics, panelLeft + 12, panelTop + 8, panelWidth - 24, HEADER - 14, color);
        graphics.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), panelLeft + 14, panelTop + 12, Theme.TEXT, false);
        graphics.drawString(font, Theme.clip(font, Texts.translatable("tts.subtitle"), panelWidth / 2), panelLeft + 14, panelTop + 24, Theme.TEXT_DIM, false);

        boolean on = currentMode > 0;
        FormattedCharSequence pill = Theme.clip(font, status(), panelWidth / 2 - 30);
        int pillWidth = font.width(pill) + 18;
        int pillX = right - 14 - pillWidth;
        Theme.roundRect(graphics, pillX, panelTop + 10, right - 14, panelTop + 24, on ? Theme.withAlpha(voiceColor(currentMode), 0.22F) : 0x33454B57);
        graphics.fill(pillX + 6, panelTop + 15, pillX + 10, panelTop + 19, on ? voiceColor(currentMode) : Theme.TEXT_DIM);
        graphics.drawString(font, pill, pillX + 13, panelTop + 13, on ? Theme.TEXT : Theme.TEXT_DIM, false);
        graphics.fill(panelLeft + 1, panelTop + HEADER, right - 1, panelTop + HEADER + 1, Theme.BORDER);

        // column titles and divider
        int titlesY = panelTop + HEADER + 8;
        graphics.drawString(font, Texts.translatable("tts.voice").withStyle(ChatFormatting.BOLD), panelLeft + 14, titlesY, STUDIO, false);
        graphics.drawString(font, Texts.translatable("tts.language").withStyle(ChatFormatting.BOLD), splitX + 8, titlesY, STUDIO, false);
        graphics.fill(splitX, titlesY, splitX + 1, bottom - 36, Theme.BORDER);

        // footer: what will be activated
        graphics.fill(panelLeft + 14, bottom - 36, right - 14, bottom - 35, Theme.BORDER);
        int buttonWidth = Math.min(92, (panelWidth - 28) / 4);
        int summaryWidth = panelWidth - 28 - buttonWidth * 2 - 16;
        graphics.drawString(font, Theme.clip(font, summary(), summaryWidth), panelLeft + 14, bottom - 22, mode > 0 && language >= 0 ? Theme.TEXT : Theme.TEXT_DIM,
            false);
    }

    /** Bars that rise and fall like speech; flat and grey while nothing is chosen. */
    private void waveform(Gfx graphics, int x, int y, int width, int height, int color) {
        float time = (Util.getMillis() - openedAt) / 1000F;
        int barWidth = Math.max(2, width / (BARS * 2));
        int gap = (width - barWidth * BARS) / Math.max(1, BARS - 1);
        int middle = y + height / 2;
        boolean speaking = mode > 0;
        for (int i = 0; i < BARS; i++) {
            float wave = speaking
                ? (float) (Math.abs(Math.sin(time * 3.1 + i * 0.55)) * 0.6 + Math.abs(Math.sin(time * 5.3 + i * 1.7)) * 0.4)
                : 0.08F;
            int half = Math.max(1, Math.round(wave * height / 2F));
            int bx = x + i * (barWidth + gap);
            graphics.fill(bx, middle - half, bx + barWidth, middle + half, Theme.withAlpha(color, speaking ? 0.28F : 0.18F));
        }
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
                    background = active ? Theme.mix(STUDIO, 0xFFCDB6FF, t) : Theme.CARD;
                    border = active ? background : Theme.BORDER;
                    textColor = active ? 0xFF14101C : Theme.TEXT_OFF;
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

    /** One voice: gender mark in its color, the speaker's name, its number. */
    private final class VoiceRow extends AnimatedButton {
        private final int voice;
        boolean selected;

        @SuppressWarnings("unchecked")
        VoiceRow(int x, int y, int width, int height, int voice, Consumer<VoiceRow> action) {
            super(x, y, width, height, Texts.literal(speakerName(voice)), (Consumer<AnimatedButton>) (Consumer<?>) action);
            this.voice = voice;
        }

        @Override
        protected void renderWidget(Gfx graphics, int mouseX, int mouseY, float partialTick) {
            float t = hover();
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            int color = voiceColor(voice);
            int background = selected ? Theme.withAlpha(color, 0.18F) : Theme.mix(Theme.CARD, Theme.CARD_HOVER, t);
            int border = selected ? color : Theme.mix(Theme.BORDER, Theme.BORDER_HOVER, t);
            Theme.roundBox(graphics, x, y, w, h, background, border);
            Theme.roundRect(graphics, x + 4, y + 3, x + 4 + (h - 6), y + h - 3, Theme.withAlpha(color, selected ? 0.9F : 0.35F));
            String mark = masculine(voice) ? "♂" : "♀";
            graphics.drawString(font, mark, x + 4 + (h - 6 - font.width(mark)) / 2, y + (h - 8) / 2, selected ? 0xFF14101C : color, false);
            String number = (masculine(voice) ? "M" : "F") + variant(voice);
            int textX = x + h + 4;
            graphics.drawString(font, Theme.clip(font, getMessage().copy().withStyle(ChatFormatting.BOLD), w - h - 30), textX, y + (h - 8) / 2,
                selected ? Theme.TEXT : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, t), false);
            graphics.drawString(font, number, x + w - 6 - font.width(number), y + (h - 8) / 2, selected ? color : Theme.TEXT_OFF, false);
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
            int background = selected ? Theme.withAlpha(STUDIO, 0.18F) : Theme.mix(Theme.CARD, Theme.CARD_HOVER, t);
            int border = selected ? STUDIO : Theme.mix(Theme.BORDER, Theme.BORDER_HOVER, t);
            Theme.roundBox(graphics, x, y, w, h, background, border);
            flag(graphics, x + 6, y + (h - 11) / 2, voiceLanguage);
            int textX = x + 28;
            graphics.drawString(font, Theme.clip(font, getMessage(), w - 44), textX, y + (h - 8) / 2, selected ? Theme.TEXT : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, t), false);
            if (selected) {
                graphics.drawString(font, "✔", x + w - 12, y + (h - 8) / 2, STUDIO, false);
            }
        }
    }
}
