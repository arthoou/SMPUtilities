package com.arthou.smputilities.mc;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
//#if MC>=1.19
import net.minecraft.network.chat.contents.TranslatableContents;
//#else
//$$ import net.minecraft.network.chat.TextComponent;
//$$ import net.minecraft.network.chat.TranslatableComponent;
//#endif

/** Chat components across versions (1.18.2 still built them with constructors). */
public final class Texts {
    private Texts() {
    }

    public static MutableComponent literal(String text) {
        //#if MC>=1.19
        return Component.literal(text);
        //#else
        //$$ return new TextComponent(text);
        //#endif
    }

    public static MutableComponent empty() {
        //#if MC>=1.19
        return Component.empty();
        //#else
        //$$ return new TextComponent("");
        //#endif
    }

    public static MutableComponent translatable(String key, Object... args) {
        //#if MC>=1.19
        return Component.translatable(key, args);
        //#else
        //$$ return new TranslatableComponent(key, args);
        //#endif
    }

    /** The translation key when the component itself is a translation, otherwise null. */
    public static String translationKey(Component component) {
        //#if MC>=1.19
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
        //#else
        //$$ return component instanceof TranslatableComponent translatable ? translatable.getKey() : null;
        //#endif
    }

    public static ClickEvent runCommand(String command) {
        //#if MC>=1.21.5
        return new ClickEvent.RunCommand(command);
        //#else
        //$$ return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        //#endif
    }

    public static ClickEvent suggestCommand(String command) {
        //#if MC>=1.21.5
        return new ClickEvent.SuggestCommand(command);
        //#else
        //$$ return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command);
        //#endif
    }

    public static HoverEvent showText(Component text) {
        //#if MC>=1.21.5
        return new HoverEvent.ShowText(text);
        //#else
        //$$ return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
        //#endif
    }

    /** Adds a hover text to the component's style. */
    public static MutableComponent hover(MutableComponent component, Component text) {
        return component.withStyle(style -> style.withHoverEvent(showText(text)));
    }

    /** Makes the component run a command when clicked. */
    public static MutableComponent clickRun(MutableComponent component, String command) {
        return component.withStyle(style -> style.withClickEvent(runCommand(command)));
    }

    public static Style color(Style style, int rgb) {
        return style.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb & 0xFFFFFF));
    }
}
