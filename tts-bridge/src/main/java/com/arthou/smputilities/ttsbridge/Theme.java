package com.arthou.smputilities.ttsbridge;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Locale;

/** Midnight surfaces with a teal accent: the voice app's own look. */
final class Theme {
    static final Color BG          = new Color(0x0C, 0x10, 0x1C);
    static final Color SIDEBAR     = new Color(0x09, 0x0C, 0x16);
    static final Color SURFACE     = new Color(0x14, 0x19, 0x2B);
    static final Color SURFACE_ALT = new Color(0x1B, 0x22, 0x3A);
    static final Color BORDER      = new Color(0x26, 0x2F, 0x4D);

    static final Color ACCENT       = new Color(0x2D, 0xD4, 0xBF);
    static final Color ACCENT_SOFT  = new Color(0x7C, 0xE7, 0xD8);
    static final Color ACCENT_DEEP  = new Color(0x16, 0x8F, 0x80);
    static final Color ACCENT_GHOST = new Color(0x2D, 0xD4, 0xBF, 0x22);
    static final Color ON_ACCENT    = new Color(0x04, 0x1F, 0x1B);

    static final Color TEXT        = new Color(0xEE, 0xF2, 0xFA);
    static final Color TEXT_DIM    = new Color(0x8C, 0x96, 0xB2);
    static final Color OK          = new Color(0x4A, 0xDE, 0x80);
    static final Color WARN        = new Color(0xFB, 0xBF, 0x24);
    static final Color ERR         = new Color(0xF8, 0x71, 0x71);

    private Theme() {
    }

    private static String pick(String... candidates) {
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String want : candidates) {
            for (String have : available) {
                if (have.equalsIgnoreCase(want)) {
                    return have;
                }
            }
        }
        return Font.SANS_SERIF;
    }

    private static final String UI_FAMILY = pick("Inter", "Segoe UI", "Noto Sans", "DejaVu Sans");
    private static final String MONO_FAMILY = pick("JetBrains Mono", "Cascadia Mono", "Consolas", "DejaVu Sans Mono");

    static Font ui(int style, int size) {
        return new Font(UI_FAMILY, style, size);
    }

    static Font mono(int style, int size) {
        return new Font(MONO_FAMILY, style, size);
    }

    static String osLabel() {
        String name = System.getProperty("os.name", "unknown");
        String arch = System.getProperty("os.arch", "");
        return name + (arch.isBlank() ? "" : " (" + arch.toLowerCase(Locale.ROOT) + ")");
    }
}
