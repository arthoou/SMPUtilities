package com.arthou.smputilities.core.chat;

import java.util.Map;

/** Replaces {key} placeholders. Unknown keys are left untouched. */
public final class Placeholders {
    private Placeholders() {
    }

    public static String apply(String template, Map<String, String> values) {
        if (template == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(template.length() + 32);
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{') {
                int end = template.indexOf('}', i + 1);
                if (end > i) {
                    String key = template.substring(i + 1, end);
                    String value = values.get(key);
                    if (value != null) {
                        out.append(value);
                        i = end + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** Escapes text inserted into a template so player input cannot inject color codes. */
    public static String escapeCodes(String text) {
        return text == null ? "" : text.replace("&", "&&").replace("§", "");
    }
}
