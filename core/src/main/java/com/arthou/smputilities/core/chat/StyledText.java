package com.arthou.smputilities.core.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Text split into styled runs, parsed from legacy {@code &} codes and
 * {@code &#RRGGBB} hex colors. The platform turns each run into a game
 * component; core code stays independent from the chat API.
 */
public final class StyledText {
    /** Color is 0xRRGGBB or -1 for "inherit". */
    public record Run(String text, int color, boolean bold, boolean italic, boolean underlined,
                      boolean strikethrough, boolean obfuscated) {
    }

    private static final int[] LEGACY_COLORS = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private final List<Run> runs;

    private StyledText(List<Run> runs) {
        this.runs = Collections.unmodifiableList(runs);
    }

    public List<Run> runs() {
        return runs;
    }

    public String plain() {
        StringBuilder builder = new StringBuilder();
        for (Run run : runs) {
            builder.append(run.text());
        }
        return builder.toString();
    }

    public static StyledText plain(String text) {
        List<Run> runs = new ArrayList<>();
        runs.add(new Run(text == null ? "" : text, -1, false, false, false, false, false));
        return new StyledText(runs);
    }

    /** Parses {@code &a}, {@code &l}, {@code &r}, {@code &#ff8800}; {@code &&} is a literal ampersand. */
    public static StyledText parse(String input) {
        return parse(input, true);
    }

    /**
     * @param allowCodes false keeps the text literally (used for players without color permission)
     */
    public static StyledText parse(String input, boolean allowCodes) {
        String text = input == null ? "" : input;
        if (!allowCodes) {
            return plain(text);
        }
        List<Run> runs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int color = -1;
        boolean bold = false;
        boolean italic = false;
        boolean underlined = false;
        boolean strike = false;
        boolean magic = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (c == '&' && code == '&') {
                    current.append('&');
                    i++;
                    continue;
                }
                int newColor = Integer.MIN_VALUE;
                int consumed = 1;
                if (code == '#' && isHex(text, i + 2, 6)) {
                    newColor = Integer.parseInt(text.substring(i + 2, i + 8), 16);
                    consumed = 7;
                } else {
                    int legacy = Character.digit(code, 16);
                    if (legacy >= 0) {
                        newColor = LEGACY_COLORS[legacy];
                    }
                }
                boolean isFormat = "klmnor".indexOf(code) >= 0;
                if (newColor == Integer.MIN_VALUE && !isFormat) {
                    current.append(c);
                    continue;
                }
                if (current.length() > 0) {
                    runs.add(new Run(current.toString(), color, bold, italic, underlined, strike, magic));
                    current.setLength(0);
                }
                if (newColor != Integer.MIN_VALUE) {
                    color = newColor;
                    bold = italic = underlined = strike = magic = false;
                } else {
                    switch (code) {
                        case 'k' -> magic = true;
                        case 'l' -> bold = true;
                        case 'm' -> strike = true;
                        case 'n' -> underlined = true;
                        case 'o' -> italic = true;
                        default -> {
                            color = -1;
                            bold = italic = underlined = strike = magic = false;
                        }
                    }
                }
                i += consumed;
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0 || runs.isEmpty()) {
            runs.add(new Run(current.toString(), color, bold, italic, underlined, strike, magic));
        }
        return new StyledText(runs);
    }

    /** Removes color and format codes, e.g. to compare or validate nicknames. */
    public static String strip(String input) {
        return parse(input).plain();
    }

    private static boolean isHex(String text, int start, int length) {
        if (start + length > text.length()) {
            return false;
        }
        for (int i = start; i < start + length; i++) {
            if (Character.digit(text.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    public static int legacyColor(char code) {
        int index = Character.digit(code, 16);
        return index < 0 ? -1 : LEGACY_COLORS[index];
    }
}
