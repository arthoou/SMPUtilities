package com.arthou.smputilities.core.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds which players a chat message mentions. A mention is a whole word
 * equal to a player's account name or RP nickname, optionally prefixed by @.
 */
public final class MentionFinder {
    /** A player that can be mentioned; {@code names} holds the real name and the nickname. */
    public record Candidate<T>(T id, Collection<String> names) {
    }

    public record Match(int start, int end) {
    }

    private MentionFinder() {
    }

    public static <T> Set<T> find(String message, Collection<Candidate<T>> candidates, boolean requireAt) {
        Set<T> found = new LinkedHashSet<>();
        for (Candidate<T> candidate : candidates) {
            for (String name : candidate.names()) {
                if (!matches(message, name, requireAt).isEmpty()) {
                    found.add(candidate.id());
                    break;
                }
            }
        }
        return found;
    }

    /** Positions where {@code name} occurs as a whole word (case-insensitive). */
    public static List<Match> matches(String message, String name, boolean requireAt) {
        List<Match> result = new ArrayList<>();
        if (message == null || name == null || name.isBlank() || name.length() < 2) {
            return result;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        String needle = name.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int index = lower.indexOf(needle, from);
            if (index < 0) {
                break;
            }
            int end = index + needle.length();
            boolean hasAt = index > 0 && lower.charAt(index - 1) == '@';
            int wordStart = hasAt ? index - 1 : index;
            boolean leftOk = wordStart == 0 || !isNameChar(lower.charAt(wordStart - 1));
            boolean rightOk = end >= lower.length() || !isNameChar(lower.charAt(end));
            if (leftOk && rightOk && (!requireAt || hasAt)) {
                result.add(new Match(wordStart, end));
            }
            from = index + 1;
        }
        return result;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
