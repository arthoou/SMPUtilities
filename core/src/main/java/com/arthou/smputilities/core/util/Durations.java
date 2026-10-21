package com.arthou.smputilities.core.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses "1d12h", "30m", "2w", "perm" and formats millisecond spans. */
public final class Durations {
    private static final Pattern PART = Pattern.compile("(\\d+)\\s*(mo|[smhdwy])");

    private Durations() {
    }

    /**
     * @return milliseconds, {@code -1} for permanent ("perm", "permanent", "forever", "0"),
     *         or {@code null} when the text is not a duration
     */
    public static Long parse(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        if (value.equals("perm") || value.equals("permanent") || value.equals("forever") || value.equals("permanente") || value.equals("0")) {
            return -1L;
        }
        Matcher matcher = PART.matcher(value);
        long total = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end && !value.substring(end, matcher.start()).isBlank()) {
                return null;
            }
            long amount = Long.parseLong(matcher.group(1));
            total += amount * unit(matcher.group(2));
            end = matcher.end();
        }
        if (end == 0 || end != value.length()) {
            return null;
        }
        return total;
    }

    private static long unit(String unit) {
        switch (unit) {
            case "s":
                return 1000L;
            case "m":
                return 60_000L;
            case "h":
                return 3_600_000L;
            case "d":
                return 86_400_000L;
            case "w":
                return 604_800_000L;
            case "mo":
                return 2_592_000_000L;
            case "y":
                return 31_536_000_000L;
            default:
                throw new IllegalArgumentException(unit);
        }
    }

    /** "2d 3h 4m", "45s"; at most three units. */
    public static String format(long millis) {
        if (millis < 0) {
            return "∞";
        }
        long seconds = millis / 1000;
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3600;
        long minutes = seconds % 3600 / 60;
        long secs = seconds % 60;
        StringBuilder builder = new StringBuilder();
        int parts = 0;
        if (days > 0) {
            builder.append(days).append("d ");
            parts++;
        }
        if (hours > 0) {
            builder.append(hours).append("h ");
            parts++;
        }
        if (minutes > 0 && parts < 3) {
            builder.append(minutes).append("m ");
            parts++;
        }
        if ((secs > 0 || builder.length() == 0) && parts < 3) {
            builder.append(secs).append('s');
        }
        return builder.toString().trim();
    }

    /** Formats game ticks (20 per second) as a play time. */
    public static String formatTicks(long ticks) {
        return format(ticks * 50L);
    }
}
