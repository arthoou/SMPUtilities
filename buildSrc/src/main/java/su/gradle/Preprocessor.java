package su.gradle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the shared sources into the sources of one target (Minecraft version
 * plus loader). Directives sit alone on their line:
 *
 * <pre>
 * //#if MC>=1.20.5 &amp;&amp; !FABRIC
 * ...
 * //#elif MC>=1.19
 * ...
 * //#else
 * ...
 * //#endif
 * </pre>
 *
 * Lines of inactive branches become empty lines, so line numbers in compiler
 * errors match the shared source. Inside an active branch a leading
 * {@code //$$ } is removed, which lets alternative code stay commented out for
 * IDEs. Swaps rename identifiers for version ranges (for example a class that
 * Mojang renamed).
 */
public final class Preprocessor {
    /** A whole-word replacement applied to active lines when {@code condition} holds. */
    public record Swap(String condition, String from, String to) {
    }

    private static final Pattern DIRECTIVE = Pattern.compile("^\\s*//#(if|elif|else|endif)\\b\\s*(.*)$");
    private static final Pattern ESCAPED = Pattern.compile("^(\\s*)//\\$\\$ ?");

    private final int[] minecraft;
    private final Set<String> flags;
    private final List<CompiledSwap> swaps = new ArrayList<>();

    private record CompiledSwap(Pattern pattern, String replacement) {
    }

    private static boolean isWord(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /**
     * @param minecraft target version, for example "1.20.1"
     * @param flags     names that are true, for example FABRIC; every other name is false
     */
    public Preprocessor(String minecraft, Set<String> flags, List<Swap> swapRules) {
        this.minecraft = parseVersion(minecraft);
        this.flags = flags;
        for (Swap swap : swapRules) {
            if (evaluate(swap.condition(), "swap rule " + swap.from())) {
                // Word boundaries only where the text starts or ends with a word character, so ".location()" works too.
                String from = swap.from();
                String before = isWord(from.charAt(0)) ? "(?<![\\w$])" : "";
                String after = isWord(from.charAt(from.length() - 1)) ? "(?![\\w$])" : "";
                swaps.add(new CompiledSwap(Pattern.compile(before + Pattern.quote(from) + after), Matcher.quoteReplacement(swap.to())));
            }
        }
    }

    private static final class Frame {
        final boolean parentActive;
        final int line;
        boolean taken;
        boolean active;
        boolean sawElse;

        Frame(boolean parentActive, int line) {
            this.parentActive = parentActive;
            this.line = line;
        }
    }

    /** Processes one file. Throws {@link IllegalArgumentException} with the file and line on bad directives. */
    public String process(String file, String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length());
        Deque<Frame> stack = new ArrayDeque<>();
        boolean active = true;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String where = file + ":" + (i + 1);
            Matcher directive = DIRECTIVE.matcher(line);
            if (directive.matches()) {
                String kind = directive.group(1);
                String argument = directive.group(2).trim();
                switch (kind) {
                    case "if" -> {
                        Frame frame = new Frame(active, i + 1);
                        frame.active = active && evaluate(argument, where);
                        frame.taken = frame.active;
                        stack.push(frame);
                        active = frame.active;
                    }
                    case "elif" -> {
                        Frame frame = require(stack, where, kind);
                        if (frame.sawElse) {
                            throw new IllegalArgumentException(where + ": #elif after #else");
                        }
                        boolean value = !frame.taken && frame.parentActive && evaluate(argument, where);
                        frame.active = value;
                        frame.taken |= value;
                        active = value;
                    }
                    case "else" -> {
                        Frame frame = require(stack, where, kind);
                        if (frame.sawElse) {
                            throw new IllegalArgumentException(where + ": second #else");
                        }
                        frame.sawElse = true;
                        frame.active = !frame.taken && frame.parentActive;
                        frame.taken = true;
                        active = frame.active;
                    }
                    default -> {
                        Frame frame = require(stack, where, kind);
                        stack.pop();
                        active = frame.parentActive;
                    }
                }
                // Keep the line count: a directive becomes an empty line.
            } else if (active) {
                out.append(transform(line));
            }
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        if (!stack.isEmpty()) {
            throw new IllegalArgumentException(file + ":" + stack.peek().line + ": #if without #endif");
        }
        return out.toString();
    }

    private static Frame require(Deque<Frame> stack, String where, String kind) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException(where + ": #" + kind + " without #if");
        }
        return stack.peek();
    }

    private String transform(String line) {
        Matcher escaped = ESCAPED.matcher(line);
        String result = escaped.find() ? escaped.group(1) + line.substring(escaped.end()) : line;
        for (CompiledSwap swap : swaps) {
            result = swap.pattern().matcher(result).replaceAll(swap.replacement());
        }
        return result;
    }

    // ------------------------------------------------------------ conditions

    /** Evaluates a condition such as {@code MC>=1.20.5 && (FORGE || NEOFORGE)}. */
    public boolean evaluate(String expression, String where) {
        Parser parser = new Parser(expression, where);
        boolean value = parser.or();
        parser.expectEnd();
        return value;
    }

    private final class Parser {
        private final String text;
        private final String where;
        private int pos;

        Parser(String text, String where) {
            this.text = text;
            this.where = where;
        }

        boolean or() {
            boolean value = and();
            while (consume("||")) {
                boolean right = and();
                value = value || right;
            }
            return value;
        }

        boolean and() {
            boolean value = unary();
            while (consume("&&")) {
                boolean right = unary();
                value = value && right;
            }
            return value;
        }

        boolean unary() {
            if (consume("!")) {
                return !unary();
            }
            if (consume("(")) {
                boolean value = or();
                if (!consume(")")) {
                    throw error("missing )");
                }
                return value;
            }
            String name = identifier();
            if (name.equals("MC")) {
                String op = operator();
                int[] version = parseVersion(versionLiteral());
                int cmp = compare(minecraft, version);
                return switch (op) {
                    case ">=" -> cmp >= 0;
                    case "<=" -> cmp <= 0;
                    case ">" -> cmp > 0;
                    case "<" -> cmp < 0;
                    case "==" -> cmp == 0;
                    default -> cmp != 0;
                };
            }
            if (!KNOWN_FLAGS.contains(name)) {
                throw error("unknown name " + name);
            }
            return flags.contains(name);
        }

        private String identifier() {
            skip();
            int start = pos;
            while (pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_')) {
                pos++;
            }
            if (start == pos) {
                throw error("expected a name");
            }
            return text.substring(start, pos).toUpperCase(Locale.ROOT);
        }

        private String operator() {
            skip();
            for (String op : new String[] {">=", "<=", "==", "!=", ">", "<"}) {
                if (text.startsWith(op, pos)) {
                    pos += op.length();
                    return op;
                }
            }
            throw error("expected a comparison after MC");
        }

        private String versionLiteral() {
            skip();
            int start = pos;
            while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) {
                throw error("expected a version");
            }
            return text.substring(start, pos);
        }

        private boolean consume(String token) {
            skip();
            if (text.startsWith(token, pos)) {
                pos += token.length();
                return true;
            }
            return false;
        }

        private void skip() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        void expectEnd() {
            skip();
            if (pos != text.length()) {
                throw error("unexpected '" + text.substring(pos) + "'");
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(where + ": " + message + " in condition '" + text + "'");
        }
    }

    /** Names a condition may use besides MC. */
    public static final Set<String> KNOWN_FLAGS = Set.of("FABRIC", "FORGE", "NEOFORGE", "FORGELIKE");

    static int[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        int[] result = new int[3];
        for (int i = 0; i < parts.length && i < 3; i++) {
            result[i] = Integer.parseInt(parts[i]);
        }
        return result;
    }

    static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }
}
