package com.arthou.smputilities.core.chat;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds blocked words and advertising in chat lines. Words are matched after
 * folding case, accents and common look-alike characters ("4" for "a", "$"
 * for "s"), with separators between letters ignored, so "B.a.D" and "b4d"
 * match "bad". Advertising is an IPv4 address or a Discord invite.
 */
public final class ChatFilter {
    public enum Verdict { CLEAN, CENSORED, BLOCKED }

    /** What the filter found: the text to send instead (censored), and the first match. */
    public record Result(Verdict verdict, String text, String match) {
    }

    private static final Pattern IPV4 = Pattern.compile("(?<![\\d.])(?:\\d{1,3}[.,]\\s?){3}\\d{1,3}(?![\\d.])");
    private static final Pattern INVITE = Pattern.compile("(?i)(discord\\.gg|discord(?:app)?\\.com/invite)\\s*/\\s*\\w+");

    private final List<String> words = new ArrayList<>();
    private final boolean censor;
    private final boolean advertising;

    /**
     * @param words       blocked words; matched as parts of words unless they start with "=", which
     *                    matches whole words only
     * @param censor      true replaces matches with "*", false blocks the whole line
     * @param advertising also block IPv4 addresses and Discord invites (never censored)
     */
    public ChatFilter(List<String> words, boolean censor, boolean advertising) {
        for (String word : words) {
            if (word != null && !word.isBlank()) {
                boolean whole = word.startsWith("=");
                String folded = fold(whole ? word.substring(1) : word).folded().trim();
                if (!folded.isEmpty()) {
                    this.words.add((whole ? "=" : "") + folded);
                }
            }
        }
        this.censor = censor;
        this.advertising = advertising;
    }

    public boolean isEmpty() {
        return words.isEmpty() && !advertising;
    }

    public Result check(String text) {
        if (text == null || text.isEmpty()) {
            return new Result(Verdict.CLEAN, text, "");
        }
        if (advertising) {
            Matcher ip = IPV4.matcher(text);
            while (ip.find()) {
                if (validIp(ip.group())) {
                    return new Result(Verdict.BLOCKED, text, ip.group());
                }
            }
            Matcher invite = INVITE.matcher(text);
            if (invite.find()) {
                return new Result(Verdict.BLOCKED, text, invite.group());
            }
        }
        if (words.isEmpty()) {
            return new Result(Verdict.CLEAN, text, "");
        }
        Folded folded = fold(text);
        boolean[] hidden = new boolean[text.length()];
        String first = null;
        for (String entry : words) {
            boolean whole = entry.startsWith("=");
            String word = whole ? entry.substring(1) : entry;
            int from = 0;
            while (true) {
                int at = folded.folded().indexOf(word, from);
                if (at < 0) {
                    break;
                }
                int end = at + word.length();
                from = at + 1;
                if (whole && (!folded.boundaryBefore(at) || !folded.boundaryAfter(end))) {
                    continue;
                }
                int start = folded.origin()[at];
                int stop = folded.origin()[end - 1] + 1;
                if (first == null) {
                    first = text.substring(start, stop);
                }
                for (int i = start; i < stop; i++) {
                    hidden[i] = true;
                }
            }
        }
        if (first == null) {
            return new Result(Verdict.CLEAN, text, "");
        }
        if (!censor) {
            return new Result(Verdict.BLOCKED, text, first);
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            out.append(hidden[i] && !Character.isWhitespace(text.charAt(i)) ? '*' : text.charAt(i));
        }
        return new Result(Verdict.CENSORED, out.toString(), first);
    }

    private static boolean validIp(String text) {
        String[] parts = text.replace(',', '.').replace(" ", "").split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            int value = Integer.parseInt(part);
            if (value > 255) {
                return false;
            }
        }
        return true;
    }

    /** Letters, digits and single spaces of the text, folded, with where each came from. */
    private record Folded(String folded, int[] origin) {
        boolean boundaryBefore(int index) {
            return index == 0 || folded.charAt(index - 1) == ' ';
        }

        boolean boundaryAfter(int end) {
            return end >= folded.length() || folded.charAt(end) == ' ';
        }
    }

    private static Folded fold(String text) {
        StringBuilder folded = new StringBuilder(text.length());
        int[] origin = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                // Matches never cross words; dots, dashes and the like inside a word are skipped.
                if (folded.length() > 0 && folded.charAt(folded.length() - 1) != ' ') {
                    origin[folded.length()] = i;
                    folded.append(' ');
                }
                continue;
            }
            char mapped = map(c);
            if (mapped != 0) {
                origin[folded.length()] = i;
                folded.append(mapped);
            }
        }
        return new Folded(folded.toString().trim().isEmpty() ? "" : folded.toString(), origin);
    }

    /** The folded letter for a character, or 0 for a separator. */
    private static char map(char c) {
        switch (c) {
            case '0':
                return 'o';
            case '1':
            case '!':
            case '|':
                return 'i';
            case '3':
                return 'e';
            case '4':
            case '@':
                return 'a';
            case '5':
            case '$':
                return 's';
            case '7':
                return 't';
            default:
                break;
        }
        if (Character.isLetterOrDigit(c)) {
            String plain = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
            return plain.isEmpty() ? c : plain.toLowerCase(Locale.ROOT).charAt(0);
        }
        return 0;
    }
}
