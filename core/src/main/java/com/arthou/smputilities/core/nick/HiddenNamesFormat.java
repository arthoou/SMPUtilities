package com.arthou.smputilities.core.nick;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Map;

/**
 * Turns a name saved by HiddenNames (a text component in JSON) into
 * SMPutilities' {@code &}-code text, keeping colors and formats.
 */
public final class HiddenNamesFormat {
    private static final Map<String, Character> COLORS = Map.ofEntries(
        Map.entry("black", '0'), Map.entry("dark_blue", '1'), Map.entry("dark_green", '2'), Map.entry("dark_aqua", '3'),
        Map.entry("dark_red", '4'), Map.entry("dark_purple", '5'), Map.entry("gold", '6'), Map.entry("gray", '7'),
        Map.entry("dark_gray", '8'), Map.entry("blue", '9'), Map.entry("green", 'a'), Map.entry("aqua", 'b'),
        Map.entry("red", 'c'), Map.entry("light_purple", 'd'), Map.entry("yellow", 'e'), Map.entry("white", 'f'));
    private static final String[][] FORMATS = {
        {"obfuscated", "k"}, {"bold", "l"}, {"strikethrough", "m"}, {"underlined", "n"}, {"italic", "o"}};

    private HiddenNamesFormat() {
    }

    public static String toLegacy(JsonElement component) {
        StringBuilder out = new StringBuilder();
        append(component, "", out);
        return out.toString();
    }

    private static void append(JsonElement element, String inherited, StringBuilder out) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive()) {
            text(element.getAsString(), inherited, out);
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            // The first element styles the rest, as in vanilla.
            String first = array.size() > 0 && array.get(0).isJsonObject() ? codes(array.get(0).getAsJsonObject(), inherited) : inherited;
            for (int i = 0; i < array.size(); i++) {
                append(array.get(i), i == 0 ? inherited : first, out);
            }
            return;
        }
        JsonObject object = element.getAsJsonObject();
        String codes = codes(object, inherited);
        if (object.has("text")) {
            text(object.get("text").getAsString(), codes, out);
        } else if (object.has("translate")) {
            text(object.get("translate").getAsString(), codes, out);
        }
        if (object.has("extra") && object.get("extra").isJsonArray()) {
            for (JsonElement child : object.getAsJsonArray("extra")) {
                append(child, codes, out);
            }
        }
    }

    /** The codes that give a part its style: the inherited ones, overridden by its own color and formats. */
    private static String codes(JsonObject object, String inherited) {
        String codes = inherited;
        if (object.has("color")) {
            String color = object.get("color").getAsString().toLowerCase(Locale.ROOT);
            String code = null;
            if (COLORS.containsKey(color)) {
                code = "&" + COLORS.get(color);
            } else if (color.matches("#[0-9a-f]{6}")) {
                code = "&" + color;
            }
            if (code != null) {
                // A color resets the formats, so they are written again after it.
                codes = code + formatsOf(inherited);
            }
        }
        StringBuilder formats = new StringBuilder();
        for (String[] format : FORMATS) {
            if (object.has(format[0]) && object.get(format[0]).isJsonPrimitive() && object.get(format[0]).getAsBoolean()
                && !codes.contains("&" + format[1])) {
                formats.append('&').append(format[1]);
            }
        }
        return codes + formats;
    }

    private static String formatsOf(String codes) {
        StringBuilder result = new StringBuilder();
        for (String[] format : FORMATS) {
            if (codes.contains("&" + format[1])) {
                result.append('&').append(format[1]);
            }
        }
        return result.toString();
    }

    private static void text(String text, String codes, StringBuilder out) {
        if (!text.isEmpty()) {
            out.append(codes).append(text.replace("&", "&&"));
        }
    }
}
