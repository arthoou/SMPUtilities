package com.arthou.smputilities.ttsbridge;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Locale;

/** Nexel-inspired dark surface with an amber/yellow accent ramp. */
final class Theme {
    static final Color BG          = new Color(0x11, 0x13, 0x18);
    static final Color SURFACE     = new Color(0x1A, 0x1D, 0x24);
    static final Color SURFACE_ALT = new Color(0x22, 0x26, 0x2F);
    static final Color BORDER      = new Color(0x2E, 0x33, 0x3E);

    static final Color AMBER       = new Color(0xFF, 0xC5, 0x3D);
    static final Color AMBER_SOFT  = new Color(0xFF, 0xD9, 0x7A);
    static final Color AMBER_DEEP  = new Color(0xC8, 0x8A, 0x14);
    static final Color AMBER_GHOST = new Color(0xFF, 0xC5, 0x3D, 0x22);

    static final Color TEXT        = new Color(0xF2, 0xF3, 0xF6);
    static final Color TEXT_DIM    = new Color(0x9A, 0xA1, 0xB0);
    static final Color OK          = new Color(0x5C, 0xD6, 0x7A);
    static final Color ERR         = new Color(0xFF, 0x6B, 0x5F);

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
