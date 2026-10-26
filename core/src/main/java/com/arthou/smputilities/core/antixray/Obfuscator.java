package com.arthou.smputilities.core.antixray;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Hides blocks in a chunk packet, following the obfuscation of DrexHD's
 * AntiXray (ChunkPacketBlockControllerAntiXray, MIT): a block that may be
 * hidden and touches no transparent block on any of its six sides is sent as
 * one of the preset blocks. The lowest layer of the world, the highest one
 * and the sides facing a chunk that is not loaded count as uncovered.
 *
 * <p>AntiXray writes into the world's own palettes so the presets always fit;
 * here only the packet changes: the sections that change are encoded again
 * with a palette that holds the presets.
 */
public final class Obfuscator {
    /** Sides of the chunk, for {@link Neighbors}. */
    public static final int WEST = 0;
    public static final int EAST = 1;
    public static final int NORTH = 2;
    public static final int SOUTH = 3;

    /** The four chunks around the one being sent. */
    public interface Neighbors {
        /**
         * Whether the block of the neighbouring chunk on {@code side}, in section
         * {@code sectionIndex} at the given section coordinates, lets light through.
         * True when that chunk is not loaded.
         */
        boolean transparent(int side, int sectionIndex, int x, int y, int z);
    }

    private static final ThreadLocal<ChunkCodec.PaletteScratch> SCRATCH = ThreadLocal.withInitial(ChunkCodec.PaletteScratch::new);

    private Obfuscator() {
    }

    /**
     * The packet buffer with the hidden blocks replaced, or {@code buffer}
     * itself when nothing changes.
     *
     * @param minSectionY  section y of the lowest section
     * @param globalBits   bits per entry of the global block state palette
     * @throws IllegalArgumentException when the buffer does not have the expected layout
     */
    public static byte[] rewrite(byte[] buffer, int minSectionY, int sectionCount, boolean lengthPrefix, int globalBits,
                                 XrayRules rules, Neighbors neighbors) {
        ChunkCodec.Section[] sections = ChunkCodec.parse(buffer, sectionCount, lengthPrefix);
        int[][] decoded = new int[sectionCount][];
        int[][] changed = new int[sectionCount][];
        boolean any = false;
        for (int s = 0; s < sectionCount; s++) {
            int bottomY = (minSectionY + s) << 4;
            int[] presets = rules.presets(bottomY);
            if (presets == null) {
                break;
            }
            ChunkCodec.Section section = sections[s];
            if (section.singleValue() || !mayHide(section, rules)) {
                continue;
            }
            int[] result = obfuscate(buffer, sections, decoded, s, presets, rules, neighbors);
            if (result != null) {
                changed[s] = result;
                any = true;
            }
        }
        if (!any) {
            return buffer;
        }
        ChunkCodec.Bytes out = new ChunkCodec.Bytes(buffer.length + buffer.length / 4);
        ChunkCodec.PaletteScratch scratch = SCRATCH.get();
        int last = 0;
        for (int s = 0; s < sectionCount; s++) {
            ChunkCodec.Section section = sections[s];
            if (changed[s] == null) {
                continue;
            }
            out.writeBytes(buffer, last, section.statesStart);
            ChunkCodec.writeStates(out, changed[s], section, globalBits, lengthPrefix, scratch);
            last = section.statesEnd;
        }
        out.writeBytes(buffer, last, buffer.length);
        return out.toArray();
    }

    /** False when nothing in the palette may be hidden, so the section can be skipped without decoding it. */
    private static boolean mayHide(ChunkCodec.Section section, XrayRules rules) {
        if (section.palette == null) {
            return true;
        }
        for (int id : section.palette) {
            if (rules.obfuscates(id)) {
                return true;
            }
        }
        return false;
    }

    private static int[] obfuscate(byte[] buffer, ChunkCodec.Section[] sections, int[][] decoded, int s, int[] presets,
                                   XrayRules rules, Neighbors neighbors) {
        int[] ids = decoded(buffer, sections, decoded, s);
        int[] below = s > 0 ? decoded(buffer, sections, decoded, s - 1) : null;
        int[] above = s + 1 < sections.length ? decoded(buffer, sections, decoded, s + 1) : null;
        Picker picker = new Picker(presets.length, rules.mode == XrayRules.Mode.OBFUSCATE_LAYER);
        int[] result = null;
        for (int y = 0; y < 16; y++) {
            picker.nextLayer();
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = (y << 8) | (z << 4) | x;
                    int id = ids[index];
                    if (!rules.obfuscates(id) || uncovered(ids, below, above, index, s, x, y, z, rules, neighbors)) {
                        continue;
                    }
                    int replacement = presets[picker.next()];
                    if (replacement != id) {
                        if (result == null) {
                            result = ids.clone();
                        }
                        result[index] = replacement;
                    }
                }
            }
        }
        return result;
    }

    private static boolean uncovered(int[] ids, int[] below, int[] above, int index, int s, int x, int y, int z,
                                     XrayRules rules, Neighbors neighbors) {
        if (y > 0 ? !rules.solid(ids[index - 256]) : below == null || !rules.solid(below[index + 15 * 256])) {
            return true;
        }
        if (y < 15 ? !rules.solid(ids[index + 256]) : above == null || !rules.solid(above[index - 15 * 256])) {
            return true;
        }
        if (x > 0 ? !rules.solid(ids[index - 1]) : neighbors.transparent(WEST, s, 15, y, z)) {
            return true;
        }
        if (x < 15 ? !rules.solid(ids[index + 1]) : neighbors.transparent(EAST, s, 0, y, z)) {
            return true;
        }
        if (z > 0 ? !rules.solid(ids[index - 16]) : neighbors.transparent(NORTH, s, x, y, 15)) {
            return true;
        }
        return z < 15 ? !rules.solid(ids[index + 16]) : neighbors.transparent(SOUTH, s, x, y, 0);
    }

    private static int[] decoded(byte[] buffer, ChunkCodec.Section[] sections, int[][] decoded, int s) {
        if (decoded[s] == null) {
            decoded[s] = ChunkCodec.decode(buffer, sections[s]);
        }
        return decoded[s];
    }

    /** AntiXray's random choice of preset: xorshift with a bounded result, or one value per layer. */
    private static final class Picker {
        private final int bound;
        private final boolean perLayer;
        private int state;
        private int layerValue;

        Picker(int bound, boolean perLayer) {
            this.bound = bound;
            this.perLayer = perLayer;
            do {
                state = ThreadLocalRandom.current().nextInt();
            } while (state == 0);
        }

        void nextLayer() {
            if (perLayer) {
                layerValue = random();
            }
        }

        int next() {
            if (bound == 1) {
                return 0;
            }
            return perLayer ? layerValue : random();
        }

        private int random() {
            if (bound == 1) {
                return 0;
            }
            // https://en.wikipedia.org/wiki/Xorshift
            state ^= state << 13;
            state ^= state >>> 17;
            state ^= state << 5;
            // https://www.pcg-random.org/posts/bounded-rands.html
            return (int) ((Integer.toUnsignedLong(state) * bound) >>> 32);
        }
    }
}
