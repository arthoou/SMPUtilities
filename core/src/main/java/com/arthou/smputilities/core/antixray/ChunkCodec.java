package com.arthou.smputilities.core.antixray;

import java.util.Arrays;

/**
 * The block data of a chunk packet as the game writes it: for every section a
 * short (its non-air block count), its block states and its biomes. Each of
 * the two is a paletted container: one byte of bits per entry, a palette (one
 * id, a list of ids, or none for the global palette) and the entries packed
 * into longs that no entry crosses. Before 1.21.5 the longs are preceded by
 * their count.
 */
public final class ChunkCodec {
    public static final int SECTION_BLOCKS = 4096;
    private static final int SECTION_BIOMES = 64;
    /** Block states use a palette up to 8 bits per entry. */
    private static final int MAX_BLOCK_PALETTE_BITS = 8;
    /** Biomes use a palette up to 3 bits per entry. */
    private static final int MAX_BIOME_PALETTE_BITS = 3;

    private ChunkCodec() {
    }

    /** Where one section lies in the buffer, and how its block states are stored. */
    public static final class Section {
        public final int start;
        public final int statesStart;
        public final int statesEnd;
        public final int end;
        /** Bits per entry as written; 0 means the whole section is {@code palette[0]}. */
        public final int bits;
        /** The palette, or null for the global palette (entries are block state ids). */
        public final int[] palette;
        final int dataStart;
        final int storageBits;

        Section(int start, int statesStart, int statesEnd, int end, int bits, int[] palette, int dataStart, int storageBits) {
            this.start = start;
            this.statesStart = statesStart;
            this.statesEnd = statesEnd;
            this.end = end;
            this.bits = bits;
            this.palette = palette;
            this.dataStart = dataStart;
            this.storageBits = storageBits;
        }

        public boolean singleValue() {
            return bits == 0;
        }
    }

    /**
     * Splits the buffer into its sections.
     *
     * @throws IllegalArgumentException when the bytes do not have that layout
     */
    public static Section[] parse(byte[] buffer, int sectionCount, boolean lengthPrefix) {
        Reader in = new Reader(buffer);
        Section[] sections = new Section[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            int start = in.pos;
            in.skip(2);
            int statesStart = in.pos;
            int bits = in.readByte();
            int[] palette;
            int storageBits;
            if (bits == 0) {
                palette = new int[] {in.readVarInt()};
                storageBits = 0;
            } else if (bits <= MAX_BLOCK_PALETTE_BITS) {
                palette = readPalette(in);
                storageBits = bits;
            } else {
                palette = null;
                storageBits = bits;
            }
            int longs = longCount(in, lengthPrefix, storageBits, SECTION_BLOCKS);
            int dataStart = in.pos;
            in.skip(longs * 8L);
            int statesEnd = in.pos;
            skipBiomes(in, lengthPrefix);
            sections[i] = new Section(start, statesStart, statesEnd, in.pos, bits, palette, dataStart, storageBits);
        }
        return sections;
    }

    /** The 4096 block state ids of a section, in y, z, x order. */
    public static int[] decode(byte[] buffer, Section section) {
        int[] ids = new int[SECTION_BLOCKS];
        if (section.bits == 0) {
            Arrays.fill(ids, section.palette[0]);
            return ids;
        }
        int bits = section.storageBits;
        int perLong = 64 / bits;
        long mask = (1L << bits) - 1;
        int[] palette = section.palette;
        int index = 0;
        int offset = section.dataStart;
        while (index < SECTION_BLOCKS) {
            long word = readLong(buffer, offset);
            offset += 8;
            for (int j = 0; j < perLong && index < SECTION_BLOCKS; j++) {
                int value = (int) ((word >>> (j * bits)) & mask);
                if (palette != null) {
                    value = value < palette.length ? palette[value] : palette[0];
                }
                ids[index++] = value;
            }
        }
        return ids;
    }

    /**
     * Writes block states the way the game would, starting from how the
     * section was written before: a palette keeps at least its bits per entry
     * (the game sends at least 4), grows while it fits in 8 bits and becomes
     * the global palette ({@code globalBits}) beyond; a global palette stays
     * global.
     */
    public static void writeStates(Bytes out, int[] ids, Section original, int globalBits, boolean lengthPrefix, PaletteScratch scratch) {
        try {
            int count = scratch.build(ids);
            if (count == 1 && original.palette != null) {
                out.writeByte(0);
                out.writeVarInt(ids[0]);
                if (lengthPrefix) {
                    out.writeVarInt(0);
                }
                return;
            }
            int needed = 32 - Integer.numberOfLeadingZeros(count - 1);
            boolean global = original.palette == null || needed > MAX_BLOCK_PALETTE_BITS;
            int bits = original.palette == null ? original.bits : global ? globalBits : Math.max(needed, original.bits);
            out.writeByte(bits);
            if (!global) {
                out.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    out.writeVarInt(scratch.values[i]);
                }
            }
            int perLong = 64 / bits;
            int longs = (SECTION_BLOCKS + perLong - 1) / perLong;
            if (lengthPrefix) {
                out.writeVarInt(longs);
            }
            int index = 0;
            for (int l = 0; l < longs; l++) {
                long word = 0;
                for (int j = 0; j < perLong && index < SECTION_BLOCKS; j++) {
                    int id = ids[index++];
                    long value = global ? id : scratch.slotOf(id);
                    word |= value << (j * bits);
                }
                out.writeLong(word);
            }
        } finally {
            scratch.reset();
        }
    }

    private static int[] readPalette(Reader in) {
        int size = in.readVarInt();
        if (size <= 0 || size > SECTION_BLOCKS) {
            throw new IllegalArgumentException("palette size " + size);
        }
        int[] palette = new int[size];
        for (int i = 0; i < size; i++) {
            palette[i] = in.readVarInt();
        }
        return palette;
    }

    private static void skipBiomes(Reader in, boolean lengthPrefix) {
        int bits = in.readByte();
        if (bits == 0) {
            in.readVarInt();
        } else if (bits <= MAX_BIOME_PALETTE_BITS) {
            readPalette(in);
        }
        in.skip(longCount(in, lengthPrefix, bits, SECTION_BIOMES) * 8L);
    }

    private static int longCount(Reader in, boolean lengthPrefix, int storageBits, int entries) {
        if (lengthPrefix) {
            int count = in.readVarInt();
            if (count < 0) {
                throw new IllegalArgumentException("long count " + count);
            }
            return count;
        }
        if (storageBits == 0) {
            return 0;
        }
        if (storageBits > 32) {
            throw new IllegalArgumentException("bits " + storageBits);
        }
        int perLong = 64 / storageBits;
        return (entries + perLong - 1) / perLong;
    }

    static long readLong(byte[] buffer, int offset) {
        if (offset + 8 > buffer.length) {
            throw new IllegalArgumentException("data past the end");
        }
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (buffer[offset + i] & 0xFF);
        }
        return value;
    }

    private static final class Reader {
        private final byte[] buffer;
        int pos;

        Reader(byte[] buffer) {
            this.buffer = buffer;
        }

        int readByte() {
            if (pos >= buffer.length) {
                throw new IllegalArgumentException("unexpected end");
            }
            return buffer[pos++] & 0xFF;
        }

        int readVarInt() {
            int value = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                int b = readByte();
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return value;
                }
            }
            throw new IllegalArgumentException("varint too long");
        }

        void skip(long bytes) {
            if (pos + bytes > buffer.length) {
                throw new IllegalArgumentException("unexpected end");
            }
            pos += (int) bytes;
        }
    }

    /** A growing byte array. */
    public static final class Bytes {
        private byte[] data;
        private int size;

        public Bytes(int capacity) {
            data = new byte[Math.max(16, capacity)];
        }

        private void ensure(int extra) {
            if (size + extra > data.length) {
                data = Arrays.copyOf(data, Math.max(data.length * 2, size + extra));
            }
        }

        public void writeByte(int value) {
            ensure(1);
            data[size++] = (byte) value;
        }

        public void writeBytes(byte[] source, int from, int to) {
            ensure(to - from);
            System.arraycopy(source, from, data, size, to - from);
            size += to - from;
        }

        public void writeVarInt(int value) {
            while ((value & ~0x7F) != 0) {
                writeByte((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            writeByte(value);
        }

        public void writeLong(long value) {
            ensure(8);
            for (int i = 7; i >= 0; i--) {
                data[size++] = (byte) (value >>> (i * 8));
            }
        }

        public byte[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }

    /** Builds a palette without allocating per section; one per thread. */
    public static final class PaletteScratch {
        /** Far above the number of block states any game has. */
        private static final int MAX_ID = 1 << 22;
        private int[] slots = new int[0];
        final int[] values = new int[SECTION_BLOCKS];
        private int count;

        int build(int[] ids) {
            for (int id : ids) {
                if (id < 0 || id >= MAX_ID) {
                    throw new IllegalArgumentException("block state id " + id);
                }
                if (id >= slots.length) {
                    int old = slots.length;
                    slots = Arrays.copyOf(slots, Math.max(id + 1, old * 2));
                    Arrays.fill(slots, old, slots.length, -1);
                }
                if (slots[id] < 0) {
                    slots[id] = count;
                    values[count++] = id;
                }
            }
            return count;
        }

        int slotOf(int id) {
            return slots[id];
        }

        void reset() {
            for (int i = 0; i < count; i++) {
                slots[values[i]] = -1;
            }
            count = 0;
        }
    }
}
