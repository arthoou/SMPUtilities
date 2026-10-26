package com.arthou.smputilities.core.antixray;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObfuscatorTest {
    private static final int AIR = 0;
    private static final int STONE = 1;
    private static final int DEEPSLATE = 2;
    private static final int DIRT = 3;
    private static final int GLASS = 4;
    private static final int DIAMOND = 5;
    private static final int GOLD = 6;
    private static final int REGISTRY = 1000;
    private static final int GLOBAL_BITS = 10;
    private static final Obfuscator.Neighbors SOLID_AROUND = (side, s, x, y, z) -> false;
    private static final Obfuscator.Neighbors NOT_LOADED = (side, s, x, y, z) -> true;

    private static boolean[] flags(int... ids) {
        boolean[] flags = new boolean[REGISTRY];
        for (int id : ids) {
            flags[id] = true;
        }
        return flags;
    }

    private static XrayRules hide(int maxHeight) {
        return new XrayRules(XrayRules.Mode.HIDE, maxHeight, 2, flags(STONE, DEEPSLATE, DIRT, DIAMOND, GOLD),
            flags(DIAMOND, GOLD), new int[] {DEEPSLATE}, new int[] {STONE});
    }

    private static int index(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    private static int[] filled(int id) {
        int[] ids = new int[ChunkCodec.SECTION_BLOCKS];
        Arrays.fill(ids, id);
        return ids;
    }

    // ------------------------------------------------- a writer like the game's

    /** Writes sections the way the game does, independently of ChunkCodec. */
    private static byte[] chunk(boolean lengthPrefix, int[]... sections) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int[] ids : sections) {
            int nonAir = 0;
            for (int id : ids) {
                if (id != AIR) {
                    nonAir++;
                }
            }
            out.write(nonAir >> 8);
            out.write(nonAir);
            container(out, ids, lengthPrefix, 8, 4, GLOBAL_BITS);
            // Two biomes, so the biome container has a palette and data to skip.
            int[] biomes = new int[64];
            for (int i = 0; i < 64; i++) {
                biomes[i] = i < 32 ? 7 : 9;
            }
            container(out, biomes, lengthPrefix, 3, 1, 6);
        }
        return out.toByteArray();
    }

    private static void container(ByteArrayOutputStream out, int[] values, boolean lengthPrefix, int maxPaletteBits, int minBits, int globalBits) {
        Map<Integer, Integer> palette = new LinkedHashMap<>();
        for (int value : values) {
            palette.putIfAbsent(value, palette.size());
        }
        if (palette.size() == 1) {
            out.write(0);
            varInt(out, values[0]);
            if (lengthPrefix) {
                varInt(out, 0);
            }
            return;
        }
        int needed = 32 - Integer.numberOfLeadingZeros(palette.size() - 1);
        boolean global = needed > maxPaletteBits;
        int bits = global ? globalBits : Math.max(minBits, needed);
        out.write(bits);
        if (!global) {
            varInt(out, palette.size());
            for (int value : palette.keySet()) {
                varInt(out, value);
            }
        }
        int perLong = 64 / bits;
        int longs = (values.length + perLong - 1) / perLong;
        if (lengthPrefix) {
            varInt(out, longs);
        }
        for (int l = 0; l < longs; l++) {
            long word = 0;
            for (int j = 0; j < perLong && l * perLong + j < values.length; j++) {
                int value = values[l * perLong + j];
                word |= (long) (global ? value : palette.get(value)) << (j * bits);
            }
            for (int b = 7; b >= 0; b--) {
                out.write((int) (word >>> (b * 8)));
            }
        }
    }

    private static void varInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static int[][] decodeAll(byte[] buffer, int count, boolean lengthPrefix) {
        ChunkCodec.Section[] sections = ChunkCodec.parse(buffer, count, lengthPrefix);
        int[][] result = new int[count][];
        for (int i = 0; i < count; i++) {
            result[i] = ChunkCodec.decode(buffer, sections[i]);
        }
        return result;
    }

    // --------------------------------------------------------------- tests

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void roundTripsTheGameLayout(boolean lengthPrefix) {
        int[] a = filled(STONE);
        a[index(3, 4, 5)] = DIAMOND;
        int[] b = new int[ChunkCodec.SECTION_BLOCKS];
        for (int i = 0; i < b.length; i++) {
            b[i] = i % 300; // more than 256 states: global palette
        }
        int[] c = filled(AIR);
        byte[] buffer = chunk(lengthPrefix, a, b, c);
        int[][] decoded = decodeAll(buffer, 3, lengthPrefix);
        assertArrayEquals(a, decoded[0]);
        assertArrayEquals(b, decoded[1]);
        assertArrayEquals(c, decoded[2]);
        ChunkCodec.Section[] sections = ChunkCodec.parse(buffer, 3, lengthPrefix);
        assertEquals(buffer.length, sections[2].end);
        assertTrue(sections[2].singleValue());
        assertEquals(GLOBAL_BITS, sections[1].bits);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void hidesOresInsideStoneAndKeepsUncoveredOnes(boolean lengthPrefix) {
        int[] section = filled(STONE);
        section[index(8, 8, 8)] = DIAMOND;            // buried
        section[index(4, 8, 8)] = GOLD;               // next to air
        section[index(3, 8, 8)] = AIR;
        section[index(10, 5, 10)] = DIAMOND;          // next to glass
        section[index(10, 6, 10)] = GLASS;
        byte[] buffer = chunk(lengthPrefix, filled(STONE), section, filled(STONE));

        byte[] hidden = Obfuscator.rewrite(buffer, 0, 3, lengthPrefix, GLOBAL_BITS, hide(256), SOLID_AROUND);
        int[] result = decodeAll(hidden, 3, lengthPrefix)[1];
        assertEquals(STONE, result[index(8, 8, 8)]);
        assertEquals(GOLD, result[index(4, 8, 8)]);
        assertEquals(DIAMOND, result[index(10, 5, 10)]);
        int[] expected = section.clone();
        expected[index(8, 8, 8)] = STONE;
        assertArrayEquals(expected, result);
    }

    @Test
    void keepsEverythingElseOfThePacket() {
        int[] section = filled(STONE);
        section[index(8, 8, 8)] = DIAMOND;
        int[] untouched = filled(DIRT);
        untouched[index(1, 1, 1)] = AIR;
        byte[] buffer = chunk(true, untouched, section, untouched);
        byte[] hidden = Obfuscator.rewrite(buffer, 0, 3, true, GLOBAL_BITS, hide(256), SOLID_AROUND);

        ChunkCodec.Section[] before = ChunkCodec.parse(buffer, 3, true);
        ChunkCodec.Section[] after = ChunkCodec.parse(hidden, 3, true);
        // Section headers, the untouched sections and every biome container are byte for byte the same.
        assertArrayEquals(Arrays.copyOfRange(buffer, 0, before[1].statesStart), Arrays.copyOfRange(hidden, 0, after[1].statesStart));
        assertArrayEquals(Arrays.copyOfRange(buffer, before[1].statesEnd, buffer.length), Arrays.copyOfRange(hidden, after[1].statesEnd, hidden.length));
    }

    @Test
    void returnsTheSameBufferWhenNothingIsHidden() {
        int[] section = filled(STONE);
        section[index(0, 0, 0)] = DIAMOND; // bottom layer of the world counts as uncovered
        byte[] buffer = chunk(false, section, filled(AIR));
        assertSame(buffer, Obfuscator.rewrite(buffer, 0, 2, false, GLOBAL_BITS, hide(256), SOLID_AROUND));
    }

    @Test
    void worldBottomTopAndUnloadedNeighboursCountAsUncovered() {
        int[] low = filled(STONE);
        low[index(5, 0, 5)] = DIAMOND;   // bottom of the world
        low[index(5, 15, 5)] = DIAMOND;  // touches the section above, which is stone
        int[] high = filled(STONE);
        high[index(6, 15, 6)] = DIAMOND; // top of the world
        high[index(0, 7, 7)] = DIAMOND;  // west edge of the chunk
        byte[] buffer = chunk(false, low, high);

        int[][] solid = decodeAll(Obfuscator.rewrite(buffer, 0, 2, false, GLOBAL_BITS, hide(256), SOLID_AROUND), 2, false);
        assertEquals(DIAMOND, solid[0][index(5, 0, 5)]);
        assertEquals(STONE, solid[0][index(5, 15, 5)]);
        assertEquals(DIAMOND, solid[1][index(6, 15, 6)]);
        assertEquals(STONE, solid[1][index(0, 7, 7)]);

        int[][] open = decodeAll(Obfuscator.rewrite(buffer, 0, 2, false, GLOBAL_BITS, hide(256), NOT_LOADED), 2, false);
        assertEquals(DIAMOND, open[1][index(0, 7, 7)]);
        assertEquals(STONE, open[0][index(5, 15, 5)]);
    }

    @Test
    void asksTheNeighbourOnTheRightSide() {
        int[] section = filled(STONE);
        section[index(0, 7, 7)] = DIAMOND;
        section[index(15, 7, 7)] = DIAMOND;
        section[index(7, 7, 0)] = DIAMOND;
        section[index(7, 7, 15)] = DIAMOND;
        byte[] buffer = chunk(false, filled(STONE), section, filled(STONE));
        StringBuilder asked = new StringBuilder();
        Obfuscator.rewrite(buffer, 0, 3, false, GLOBAL_BITS, hide(256), (side, s, x, y, z) -> {
            asked.append(side).append(':').append(s).append(':').append(x).append(',').append(y).append(',').append(z).append(' ');
            return false;
        });
        String calls = asked.toString();
        assertTrue(calls.contains(Obfuscator.WEST + ":1:15,7,7 "), calls);
        assertTrue(calls.contains(Obfuscator.EAST + ":1:0,7,7 "), calls);
        assertTrue(calls.contains(Obfuscator.NORTH + ":1:7,7,15 "), calls);
        assertTrue(calls.contains(Obfuscator.SOUTH + ":1:7,7,0 "), calls);
    }

    @Test
    void leavesSectionsFromTheMaximumHeightAlone() {
        int[] section = filled(STONE);
        section[index(8, 8, 8)] = DIAMOND;
        byte[] buffer = chunk(false, filled(STONE), section, section, filled(STONE));
        // Sections start at y -16, 0, 16, 32; with a maximum of 16 only the first two may change.
        int[][] result = decodeAll(Obfuscator.rewrite(buffer, -1, 4, false, GLOBAL_BITS, hide(16), SOLID_AROUND), 4, false);
        assertEquals(STONE, result[1][index(8, 8, 8)]);
        assertEquals(DIAMOND, result[2][index(8, 8, 8)]);
    }

    @Test
    void usesDeepslateBelowZero() {
        int[] section = filled(DEEPSLATE);
        section[index(8, 8, 8)] = DIAMOND;
        byte[] buffer = chunk(false, filled(DEEPSLATE), section, filled(DEEPSLATE));
        int[][] result = decodeAll(Obfuscator.rewrite(buffer, -4, 3, false, GLOBAL_BITS, hide(256), SOLID_AROUND), 3, false);
        assertEquals(DEEPSLATE, result[1][index(8, 8, 8)]);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void obfuscateFillsBuriedBlocksWithRandomOres(boolean lengthPrefix) {
        XrayRules rules = new XrayRules(XrayRules.Mode.OBFUSCATE, 256, 2, flags(STONE, DIRT, DIAMOND, GOLD),
            flags(STONE, DIAMOND, GOLD), new int[] {DIAMOND, GOLD}, new int[] {DIAMOND, GOLD});
        int[] section = filled(STONE);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                section[index(x, 15, z)] = AIR; // open top layer
            }
        }
        byte[] buffer = chunk(lengthPrefix, filled(STONE), section, filled(AIR));
        int[] result = decodeAll(Obfuscator.rewrite(buffer, 0, 3, lengthPrefix, GLOBAL_BITS, rules, SOLID_AROUND), 3, lengthPrefix)[1];
        int diamonds = 0;
        int golds = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int id = result[index(x, y, z)];
                    if (y == 15) {
                        assertEquals(AIR, id);
                    } else if (y == 14) {
                        assertEquals(STONE, id, "stone under the open layer stays");
                    } else {
                        assertTrue(id == DIAMOND || id == GOLD, "buried stone becomes an ore");
                        diamonds += id == DIAMOND ? 1 : 0;
                        golds += id == GOLD ? 1 : 0;
                    }
                }
            }
        }
        assertTrue(diamonds > 100 && golds > 100, diamonds + " " + golds);
    }

    @Test
    void obfuscateLayerUsesOneBlockPerLayer() {
        XrayRules rules = new XrayRules(XrayRules.Mode.OBFUSCATE_LAYER, 256, 2, flags(STONE, DIAMOND, GOLD),
            flags(STONE, DIAMOND, GOLD), new int[] {DIAMOND, GOLD}, new int[] {DIAMOND, GOLD});
        int[] section = filled(STONE);
        section[index(0, 0, 0)] = DIRT; // a single-block section is sent as it is, as in AntiXray
        byte[] buffer = chunk(false, filled(STONE), section, filled(STONE));
        int[] result = decodeAll(Obfuscator.rewrite(buffer, 0, 3, false, GLOBAL_BITS, rules, SOLID_AROUND), 3, false)[1];
        assertNotEquals(STONE, result[index(5, 5, 5)]);
        for (int y = 0; y < 16; y++) {
            int first = result[index(1, y, 1)];
            for (int z = 1; z < 15; z++) {
                for (int x = 1; x < 15; x++) {
                    assertEquals(first, result[index(x, y, z)], "layer " + y);
                }
            }
        }
    }

    @Test
    void switchesToTheGlobalPaletteWhenThePresetsDoNotFit() {
        int[] section = new int[ChunkCodec.SECTION_BLOCKS];
        for (int i = 0; i < section.length; i++) {
            section[i] = 10 + i % 255;
        }
        // 256 solid states: an 8-bit palette. The diamond on the open west side stays, the buried one
        // becomes stone, and 257 states no longer fit.
        section[index(0, 8, 8)] = DIAMOND;
        section[index(8, 8, 8)] = DIAMOND;
        boolean[] solid = new boolean[REGISTRY];
        Arrays.fill(solid, true);
        solid[AIR] = false;
        XrayRules rules = new XrayRules(XrayRules.Mode.HIDE, 256, 2, solid, flags(DIAMOND), new int[] {STONE}, new int[] {STONE});
        byte[] buffer = chunk(false, filled(STONE), section, filled(STONE));
        assertEquals(8, ChunkCodec.parse(buffer, 3, false)[1].bits);
        byte[] hidden = Obfuscator.rewrite(buffer, 0, 3, false, GLOBAL_BITS, rules, (side, s, x, y, z) -> side == Obfuscator.WEST);
        ChunkCodec.Section after = ChunkCodec.parse(hidden, 3, false)[1];
        assertEquals(GLOBAL_BITS, after.bits);
        int[] expected = section.clone();
        expected[index(8, 8, 8)] = STONE;
        assertArrayEquals(expected, ChunkCodec.decode(hidden, after));
    }

    @Test
    void rejectsBytesOfAnotherLayout() {
        byte[] buffer = chunk(true, filled(STONE), filled(STONE));
        assertThrows(IllegalArgumentException.class, () -> ChunkCodec.parse(buffer, 3, true));
        assertThrows(IllegalArgumentException.class, () -> ChunkCodec.parse(new byte[] {0, 0, 20, 1, 1}, 1, false));
    }

    @Test
    void updateOffsetsCoverAntiXraysDiamond() {
        int[][] offsets = hide(256).updateOffsets();
        // 1 + 6 + 18 blocks for a radius of 2, as AntiXray's updateNearbyBlocks visits them.
        assertEquals(25, offsets.length);
    }
}
