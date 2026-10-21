package com.arthou.smputilities.server;

import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.core.chat.StyledText;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds chat components and sends translated feedback. */
public final class Msg {
    public static final int ACCENT = 0x6AF06B;
    public static final int DANGER = 0xFF625F;
    public static final int WARN = 0xFFC857;
    public static final int MUTED = 0xA0A8B5;
    private static final Pattern URL = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);

    private Msg() {
    }

    /** "&a..." codes to a component. */
    public static MutableComponent styled(String text) {
        return styled(StyledText.parse(text));
    }

    /** Like {@link #styled(String)}, with http(s) links made clickable (announcements, MOTD, rules). */
    public static MutableComponent linked(String text) {
        MutableComponent root = Texts.empty();
        for (StyledText.Run run : StyledText.parse(text).runs()) {
            Style style = style(run);
            Matcher matcher = URL.matcher(run.text());
            int last = 0;
            while (matcher.find()) {
                if (matcher.start() > last) {
                    root.append(Texts.literal(run.text().substring(last, matcher.start())).setStyle(style));
                }
                String url = matcher.group();
                ClickEvent click = Texts.openUrl(url);
                Style linkStyle = click == null ? style : style.withUnderlined(true).withClickEvent(click);
                root.append(Texts.literal(url).setStyle(linkStyle));
                last = matcher.end();
            }
            if (last < run.text().length()) {
                root.append(Texts.literal(run.text().substring(last)).setStyle(style));
            }
        }
        return root;
    }

    public static MutableComponent styled(StyledText text) {
        MutableComponent root = Texts.empty();
        for (StyledText.Run run : text.runs()) {
            if (run.text().isEmpty()) {
                continue;
            }
            root.append(Texts.literal(run.text()).setStyle(style(run)));
        }
        return root;
    }

    private static Style style(StyledText.Run run) {
        Style style = Style.EMPTY;
        if (run.color() >= 0) {
            style = style.withColor(TextColor.fromRgb(run.color()));
        }
        if (run.bold()) {
            style = style.withBold(true);
        }
        if (run.italic()) {
            style = style.withItalic(true);
        }
        if (run.underlined()) {
            style = style.withUnderlined(true);
        }
        if (run.strikethrough()) {
            style = style.withStrikethrough(true);
        }
        if (run.obfuscated()) {
            style = style.withObfuscated(true);
        }
        return style;
    }

    public static MutableComponent prefix() {
        return Texts.empty()
            .append(Texts.literal("SMPU").withStyle(Style.EMPTY.withColor(ACCENT).withBold(true)))
            .append(Texts.literal(" » ").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static MutableComponent tr(ServerPlayer player, String key, Object... args) {
        return Texts.literal(Lang.tr(player, key, args));
    }

    public static void info(ServerPlayer player, String key, Object... args) {
        Mc.send(player, prefix().append(tr(player, key, args).withStyle(ChatFormatting.GRAY)));
    }

    public static void ok(ServerPlayer player, String key, Object... args) {
        Mc.send(player, prefix().append(tr(player, key, args).withStyle(Style.EMPTY.withColor(ACCENT))));
    }

    public static void error(ServerPlayer player, String key, Object... args) {
        Mc.send(player, prefix().append(tr(player, key, args).withStyle(Style.EMPTY.withColor(DANGER))));
    }

    public static int ok(CommandSourceStack source, String key, Object... args) {
        ServerPlayer player = Mc.player(source);
        MutableComponent text = prefix().append(tr(player, key, args).withStyle(Style.EMPTY.withColor(ACCENT)));
        Mc.reply(source, text);
        return 1;
    }

    public static int info(CommandSourceStack source, String key, Object... args) {
        ServerPlayer player = Mc.player(source);
        MutableComponent text = prefix().append(tr(player, key, args).withStyle(ChatFormatting.GRAY));
        Mc.reply(source, text);
        return 1;
    }

    public static int error(CommandSourceStack source, String key, Object... args) {
        source.sendFailure(prefix().append(tr(Mc.player(source), key, args).withStyle(Style.EMPTY.withColor(DANGER))));
        return 0;
    }

    public static void raw(CommandSourceStack source, Component text) {
        Mc.reply(source, text);
    }

    /** Sends {@code key} to every online player in their own language. */
    public static void broadcast(String key, Object... args) {
        SuServer context = SuServer.get();
        if (context == null) {
            return;
        }
        for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
            info(player, key, args);
        }
        Mc.console(context.server(), Texts.literal(Lang.format(Lang.EN, key, args)));
    }
}
