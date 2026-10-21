package com.arthou.smputilities.core.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads and writes simple config values by dotted path ("chat.customFormat"),
 * which is how the staff panel edits settings without a field per packet.
 * Only booleans, numbers, strings and enums are reachable; maps and lists are
 * edited through dedicated commands.
 */
public final class ConfigPaths {
    public enum Kind { BOOLEAN, INT, LONG, DOUBLE, FLOAT, STRING }

    public record Entry(String path, Kind kind, Object value) {
    }

    private ConfigPaths() {
    }

    public static List<Entry> list(Object root) {
        List<Entry> entries = new ArrayList<>();
        collect(root, "", entries);
        return entries;
    }

    private static void collect(Object node, String prefix, List<Entry> out) {
        for (Field field : node.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String path = prefix.isEmpty() ? field.getName() : prefix + "." + field.getName();
            try {
                Object value = field.get(node);
                Kind kind = kindOf(field.getType());
                if (kind != null) {
                    out.add(new Entry(path, kind, value));
                } else if (value != null && field.getType().getName().startsWith(SuConfig.class.getName())) {
                    collect(value, path, out);
                }
            } catch (IllegalAccessException ignored) {
                // public fields only
            }
        }
    }

    public static Entry get(Object root, String path) {
        Located located = locate(root, path);
        if (located == null) {
            return null;
        }
        try {
            return new Entry(path, located.kind, located.field.get(located.owner));
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /**
     * Parses {@code text} for the field's type and stores it.
     *
     * @return null on success, otherwise a short error key
     */
    public static String set(Object root, String path, String text) {
        Located located = locate(root, path);
        if (located == null) {
            return "unknown_setting";
        }
        try {
            Object value = parse(located.kind, text);
            if (value == null) {
                return "invalid_value";
            }
            located.field.set(located.owner, value);
            return null;
        } catch (IllegalAccessException e) {
            return "unknown_setting";
        }
    }

    private static Object parse(Kind kind, String text) {
        String value = text == null ? "" : text.trim();
        try {
            switch (kind) {
                case BOOLEAN:
                    if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") || value.equals("1")) {
                        return Boolean.TRUE;
                    }
                    if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off") || value.equals("0")) {
                        return Boolean.FALSE;
                    }
                    return null;
                case INT:
                    return value.toLowerCase(Locale.ROOT).startsWith("0x") ? Integer.parseUnsignedInt(value.substring(2), 16) : Integer.parseInt(value);
                case LONG:
                    return Long.parseLong(value);
                case DOUBLE:
                    return Double.parseDouble(value);
                case FLOAT:
                    return Float.parseFloat(value);
                case STRING:
                    return text == null ? "" : text;
                default:
                    return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Located locate(Object root, String path) {
        String[] parts = path.split("\\.");
        Object node = root;
        for (int i = 0; i < parts.length; i++) {
            Field field;
            try {
                field = node.getClass().getField(parts[i]);
            } catch (NoSuchFieldException e) {
                return null;
            }
            if (Modifier.isStatic(field.getModifiers())) {
                return null;
            }
            if (i == parts.length - 1) {
                Kind kind = kindOf(field.getType());
                return kind == null ? null : new Located(node, field, kind);
            }
            try {
                node = field.get(node);
            } catch (IllegalAccessException e) {
                return null;
            }
            if (node == null || !node.getClass().getName().startsWith(SuConfig.class.getName())) {
                return null;
            }
        }
        return null;
    }

    private static Kind kindOf(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return Kind.BOOLEAN;
        }
        if (type == int.class || type == Integer.class) {
            return Kind.INT;
        }
        if (type == long.class || type == Long.class) {
            return Kind.LONG;
        }
        if (type == double.class || type == Double.class) {
            return Kind.DOUBLE;
        }
        if (type == float.class || type == Float.class) {
            return Kind.FLOAT;
        }
        if (type == String.class) {
            return Kind.STRING;
        }
        return null;
    }

    private record Located(Object owner, Field field, Kind kind) {
    }
}
