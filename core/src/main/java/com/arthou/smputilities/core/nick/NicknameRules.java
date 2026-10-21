package com.arthou.smputilities.core.nick;

import com.arthou.smputilities.core.chat.StyledText;
import com.arthou.smputilities.core.config.SuConfig;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class NicknameRules {
    private NicknameRules() {
    }

    private static final Pattern LEADING_CODES = Pattern.compile("^(?:&(?:#[0-9a-fA-F]{6}|[0-9a-fA-Fk-oK-OrR]))+");

    /** The color and format codes at the start of a nickname ("&c&l" in "&c&lBob"), which style the whole name. */
    public static String leadingCodes(String nickname) {
        if (nickname == null) {
            return "";
        }
        java.util.regex.Matcher matcher = LEADING_CODES.matcher(nickname);
        return matcher.find() ? matcher.group() : "";
    }

    /** Comparison key: no color codes, trimmed, lower case, collapsed spaces. */
    public static String key(String nickname) {
        return StyledText.strip(nickname).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * @param allowColors whether this player may keep color codes
     * @return null when valid, otherwise a translation key suffix
     */
    public static String validate(SuConfig.Nicknames config, String nickname, boolean allowColors) {
        if (nickname == null) {
            return "nick.invalid";
        }
        if (!allowColors && !StyledText.strip(nickname).equals(nickname.replace("&&", "&"))) {
            return "nick.no_colors";
        }
        String plain = StyledText.strip(nickname).trim();
        if (plain.length() < config.minLength) {
            return "nick.too_short";
        }
        if (plain.length() > config.maxLength) {
            return "nick.too_long";
        }
        try {
            if (!Pattern.compile(config.allowedPattern).matcher(plain).matches()) {
                return "nick.invalid_chars";
            }
        } catch (PatternSyntaxException e) {
            // a broken pattern in the config should not block every nickname
            return null;
        }
        return null;
    }
}
