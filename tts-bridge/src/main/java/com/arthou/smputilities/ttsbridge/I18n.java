package com.arthou.smputilities.ttsbridge;

import java.util.Locale;

/** The window speaks Portuguese on Portuguese systems and English elsewhere. */
final class I18n {
    private static final boolean PORTUGUESE = "pt".equals(Locale.getDefault().getLanguage());

    private I18n() {
    }

    static String t(String english, String portuguese) {
        return PORTUGUESE ? portuguese : english;
    }
}
