package com.arthou.smputilities.mc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
//#if MC>=1.20.3
import net.minecraft.nbt.NbtAccounter;
//#endif

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * NBT access that reads the same on every version. From 1.21.5 the getters
 * return Optionals; these helpers always return a value (the default when the
 * key is missing or has another type).
 */
public final class Nbt {
    private Nbt() {
    }

    public static int getInt(CompoundTag tag, String key, int fallback) {
        //#if MC>=1.21.5
        return tag.getIntOr(key, fallback);
        //#else
        //$$ return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback;
        //#endif
    }

    public static long getLong(CompoundTag tag, String key, long fallback) {
        //#if MC>=1.21.5
        return tag.getLongOr(key, fallback);
        //#else
        //$$ return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : fallback;
        //#endif
    }

    public static float getFloat(CompoundTag tag, String key, float fallback) {
        //#if MC>=1.21.5
        return tag.getFloatOr(key, fallback);
        //#else
        //$$ return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getFloat(key) : fallback;
        //#endif
    }

    public static double getDouble(CompoundTag tag, String key, double fallback) {
        //#if MC>=1.21.5
        return tag.getDoubleOr(key, fallback);
        //#else
        //$$ return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getDouble(key) : fallback;
        //#endif
    }

    public static byte getByte(CompoundTag tag, String key, byte fallback) {
        //#if MC>=1.21.5
        return tag.getByteOr(key, fallback);
        //#else
        //$$ return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getByte(key) : fallback;
        //#endif
    }

    public static String getString(CompoundTag tag, String key, String fallback) {
        //#if MC>=1.21.5
        return tag.getStringOr(key, fallback);
        //#else
        //$$ return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;
        //#endif
    }

    public static String getString(CompoundTag tag, String key) {
        return getString(tag, key, "");
    }

    /** The compound under {@code key}, or a new empty one. */
    public static CompoundTag getCompound(CompoundTag tag, String key) {
        Tag value = tag.get(key);
        return value instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    /** The list under {@code key}, or a new empty one. */
    public static ListTag getList(CompoundTag tag, String key) {
        Tag value = tag.get(key);
        return value instanceof ListTag list ? list : new ListTag();
    }

    public static boolean hasCompound(CompoundTag tag, String key) {
        return tag.get(key) instanceof CompoundTag;
    }

    public static boolean hasList(CompoundTag tag, String key) {
        return tag.get(key) instanceof ListTag;
    }

    public static boolean has(CompoundTag tag, String key) {
        return tag.get(key) != null;
    }

    /** The compounds of a list; other elements are skipped. */
    public static List<CompoundTag> compounds(ListTag list) {
        List<CompoundTag> result = new ArrayList<>(list.size());
        for (Tag element : list) {
            if (element instanceof CompoundTag compound) {
                result.add(compound);
            }
        }
        return result;
    }

    /** The string elements of a list. */
    public static List<String> strings(ListTag list) {
        List<String> result = new ArrayList<>(list.size());
        for (Tag element : list) {
            if (element instanceof net.minecraft.nbt.StringTag string) {
                result.add(asString(string));
            }
        }
        return result;
    }

    private static String asString(net.minecraft.nbt.StringTag tag) {
        //#if MC>=1.21.5
        return tag.value();
        //#else
        //$$ return tag.getAsString();
        //#endif
    }

    public static double listDouble(ListTag list, int index, double fallback) {
        if (index < 0 || index >= list.size()) {
            return fallback;
        }
        //#if MC>=1.21.5
        return list.getDoubleOr(index, fallback);
        //#else
        //$$ return list.getDouble(index);
        //#endif
    }

    public static float listFloat(ListTag list, int index, float fallback) {
        if (index < 0 || index >= list.size()) {
            return fallback;
        }
        //#if MC>=1.21.5
        return list.getFloatOr(index, fallback);
        //#else
        //$$ return list.getFloat(index);
        //#endif
    }

    public static Set<String> keys(CompoundTag tag) {
        //#if MC>=1.21.5
        return tag.keySet();
        //#else
        //$$ return tag.getAllKeys();
        //#endif
    }

    // ----------------------------------------------------------------- files

    public static CompoundTag readCompressed(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return readCompressed(in);
        }
    }

    public static CompoundTag readCompressed(InputStream in) throws IOException {
        //#if MC>=1.20.3
        return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        //#else
        //$$ return NbtIo.readCompressed(in);
        //#endif
    }

    public static void writeCompressed(CompoundTag tag, OutputStream out) throws IOException {
        NbtIo.writeCompressed(tag, out);
    }

    public static byte[] compressed(CompoundTag tag) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, bytes);
        return bytes.toByteArray();
    }

    // --------------------------------------------------------- data versions

    public static int dataVersion(CompoundTag tag, int fallback) {
        return getInt(tag, "DataVersion", fallback);
    }

    public static void addCurrentDataVersion(CompoundTag tag) {
        tag.putInt("DataVersion", Mc.dataVersion());
    }
}
