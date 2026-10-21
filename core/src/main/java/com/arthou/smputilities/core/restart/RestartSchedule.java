package com.arthou.smputilities.core.restart;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Countdown warnings and daily restart times for /su restart. */
public final class RestartSchedule {
    private RestartSchedule() {
    }

    /**
     * Warnings crossed while the remaining time went from {@code before} to
     * {@code after} seconds (each warning is a number of seconds left).
     */
    public static List<Integer> crossed(List<Integer> warnings, long before, long after) {
        List<Integer> result = new ArrayList<>();
        for (Integer warning : warnings) {
            if (warning != null && warning > 0 && before > warning && after <= warning) {
                result.add(warning);
            }
        }
        result.sort((a, b) -> b - a);
        return result;
    }

    /**
     * The next of the daily times ("06:00", "18:30") strictly after
     * {@code now}, in {@code now}'s zone, or -1 when none parses.
     */
    public static long nextDaily(List<String> times, ZonedDateTime now) {
        long best = -1;
        if (times == null) {
            return best;
        }
        for (String text : times) {
            LocalTime time;
            try {
                time = LocalTime.parse(text == null ? "" : text.trim());
            } catch (DateTimeParseException e) {
                continue;
            }
            ZonedDateTime candidate = now.with(time);
            if (!candidate.isAfter(now)) {
                candidate = candidate.plusDays(1).with(time);
            }
            long millis = candidate.toInstant().toEpochMilli();
            if (best < 0 || millis < best) {
                best = millis;
            }
        }
        return best;
    }
}
