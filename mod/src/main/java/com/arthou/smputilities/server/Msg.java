package com.arthou.smputilities.server;

import com.arthou.smputilities.core.chat.StyledText;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

/** Builds chat components and sends translated feedback. */
public final class Msg {
    public static final int ACCENT = 0x6AF06B;
    public static final int DANGER = 0xFF625F;
    public static final int WARN = 0xFFC857;
    public static final int MUTED = 0xA0A8B5;

    private Msg() {
    }

    /** "&a..." codes to a component. */
    public static MutableComponent styled(String text) {
        return styled(StyledText.parse(text));
    }

    public static MutableComponent styled(StyledText text) {
        MutableComponent root = Component.empty();
        for (StyledText.Run run : text.runs()) {
            if (run.text().isEmpty()) {
                continue;
            }
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
            root.append(Component.literal(run.text()).setStyle(style));
        }
        return root;
    }

    public static MutableComponent prefix() {
        return Component.empty()
            .append(Component.literal("SMPU").withStyle(Style.EMPTY.withColor(ACCENT).withBold(true)))
            .append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static MutableComponent tr(ServerPlayer player, String key, Object... args) {
        return Component.literal(Lang.tr(player, key, args));
    }

    public static void info(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(prefix().append(tr(player, key, args).withStyle(ChatFormatting.GRAY)));
    }

    public static void ok(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(prefix().append(tr(player, key, args).withStyle(Style.EMPTY.withColor(ACCENT))));
    }

    public static void error(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(prefix().append(tr(player, key, args).withStyle(Style.EMPTY.withColor(DANGER))));
    }

    public static int ok(CommandSourceStack source, String key, Object... args) {
        ServerPlayer player = source.getPlayer();
        MutableComponent text = prefix().append(tr(player, key, args).withStyle(Style.EMPTY.withColor(ACCENT)));
        source.sendSuccess(() -> text, false);
        return 1;
    }

    public static int info(CommandSourceStack source, String key, Object... args) {
        ServerPlayer player = source.getPlayer();
        MutableComponent text = prefix().append(tr(player, key, args).withStyle(ChatFormatting.GRAY));
        source.sendSuccess(() -> text, false);
        return 1;
    }

    public static int error(CommandSourceStack source, String key, Object... args) {
        source.sendFailure(prefix().append(tr(source.getPlayer(), key, args).withStyle(Style.EMPTY.withColor(DANGER))));
        return 0;
    }

    public static void raw(CommandSourceStack source, Component text) {
        source.sendSuccess(() -> text, false);
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
        context.server().sendSystemMessage(Component.literal(Lang.format(Lang.EN, key, args)));
    }
}
