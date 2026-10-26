package com.arthou.smputilities.core.blocklog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockLogTest {
    private static final long DAY = LocalDate.of(2026, 10, 26).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    private static final String WORLD = "minecraft:overworld";
    private static final String ALICE = "a-uuid";
    private static final String BOB = "b-uuid";

    private static BlockLog.Entry block(long time, BlockLog.Action action, String actor, int x, int y, int z, String before, String after) {
        return new BlockLog.Entry(time, action, actor, actor.equals(ALICE) ? "Alice" : "Bob", WORLD, x, y, z, before, after, "", 1);
    }

    private static BlockLog.Query around(int x, int y, int z, int radius, long since, Set<String> actors) {
        return new BlockLog.Query(WORLD, x, y, z, radius, since, actors, null, 0);
    }

    @Test
    void findsChangesByPlaceTimeAndPlayer(@TempDir Path dir) {
        BlockLog log = new BlockLog(dir, ZoneOffset.UTC);
        log.add(block(DAY + 1, BlockLog.Action.BREAK, ALICE, 10, 64, 10, "minecraft:stone", "minecraft:air"));
        log.add(block(DAY + 2, BlockLog.Action.PLACE, BOB, 11, 64, 10, "minecraft:air", "minecraft:dirt"));
        log.add(block(DAY + 3, BlockLog.Action.BREAK, ALICE, 600, 70, 600, "minecraft:oak_log[axis=y]", "minecraft:air"));
        log.add(new BlockLog.Entry(DAY + 4, BlockLog.Action.TAKE, ALICE, "Alice", WORLD, 12, 64, 10, "H4sIA\tAAA\n", "", "", 5));
        log.add(new BlockLog.Entry(DAY + 5, BlockLog.Action.BREAK, ALICE, "Alice", "minecraft:the_nether", 10, 64, 10, "minecraft:netherrack", "minecraft:air", "", 1));

        List<BlockLog.Entry> near = log.find(around(10, 64, 10, 5, 0, null));
        assertEquals(3, near.size());
        assertEquals(BlockLog.Action.TAKE, near.get(0).action(), "newest first");
        assertEquals("H4sIA\tAAA\n", near.get(0).before(), "tabs and new lines survive");
        assertEquals(5, near.get(0).count());

        assertEquals(2, log.find(around(10, 64, 10, 5, 0, Set.of(ALICE))).size());
        assertEquals(1, log.find(around(10, 64, 10, 0, 0, null)).size(), "one block");
        assertEquals(3, log.find(around(0, 0, 0, -1, 0, Set.of(ALICE))).size(), "whole dimension");
        assertEquals(2, log.find(around(0, 0, 0, -1, DAY + 3, Set.of(ALICE))).size(), "since, by Alice");
        assertEquals(2, log.find(new BlockLog.Query(WORLD, 0, 0, 0, -1, DAY + 3, null, null, 0)).size(), "since");
        assertEquals(1, log.find(new BlockLog.Query(WORLD, 0, 0, 0, -1, 0, null, Set.of(BlockLog.Action.PLACE), 0)).size(), "actions");
        assertEquals(2, log.find(new BlockLog.Query(WORLD, 0, 0, 0, -1, 0, null, null, 2)).size(), "limit");
        assertEquals(1, log.find(new BlockLog.Query("minecraft:the_nether", 10, 64, 10, 3, 0, null, null, 0)).size());
        log.close();

        assertTrue(Files.isRegularFile(dir.resolve("minecraft_overworld").resolve("0.0").resolve("2026-10-26.log")));
        assertTrue(Files.isRegularFile(dir.resolve("minecraft_overworld").resolve("1.1").resolve("2026-10-26.log")));
        // A new instance reads what the old one wrote.
        BlockLog again = new BlockLog(dir, ZoneOffset.UTC);
        assertEquals(3, again.find(around(10, 64, 10, 5, 0, null)).size());
        again.close();
    }

    @Test
    void rollbackPutsEachBlockBackToItsFirstState() {
        List<BlockLog.Entry> entries = List.of(
            block(DAY + 3, BlockLog.Action.PLACE, ALICE, 1, 64, 1, "minecraft:air", "minecraft:tnt"),
            block(DAY + 1, BlockLog.Action.BREAK, ALICE, 1, 64, 1, "minecraft:chest[facing=north]", "minecraft:air"),
            block(DAY + 2, BlockLog.Action.BREAK, ALICE, 2, 63, 1, "minecraft:stone", "minecraft:air"),
            new BlockLog.Entry(DAY + 4, BlockLog.Action.TAKE, ALICE, "Alice", WORLD, 3, 64, 1, "diamonds", "", "", 3),
            new BlockLog.Entry(DAY + 5, BlockLog.Action.ADD, ALICE, "Alice", WORLD, 3, 64, 1, "dirt", "", "", 64));
        BlockLog.Plan plan = BlockLog.rollback(entries);
        assertEquals(2, plan.blocks().size());
        BlockLog.BlockChange lowest = plan.blocks().get(0);
        assertEquals(63, lowest.y(), "lowest first");
        BlockLog.BlockChange chest = plan.blocks().get(1);
        assertEquals("minecraft:chest[facing=north]", chest.state());
        assertEquals("minecraft:tnt", chest.expected(), "only if the last change is still there");
        assertEquals(2, plan.items().size());
        assertTrue(plan.items().stream().anyMatch(item -> item.item().equals("diamonds") && item.add() && item.count() == 3));
        assertTrue(plan.items().stream().anyMatch(item -> item.item().equals("dirt") && !item.add() && item.count() == 64));

        BlockLog.Plan redo = BlockLog.restore(entries);
        BlockLog.BlockChange again = redo.blocks().stream().filter(change -> change.y() == 64).findFirst().orElseThrow();
        assertEquals("minecraft:tnt", again.state());
        assertEquals("minecraft:chest[facing=north]", again.expected());
        assertTrue(redo.items().stream().anyMatch(item -> item.item().equals("diamonds") && !item.add()));
        assertTrue(BlockLog.rollback(List.of()).isEmpty());
    }

    @Test
    void prunesOldDays(@TempDir Path dir) throws Exception {
        Path region = dir.resolve("minecraft_overworld").resolve("0.0");
        Files.createDirectories(region);
        Path old = Files.writeString(region.resolve("2000-01-01.log"), "x\n");
        Path today = Files.writeString(region.resolve(LocalDate.now(ZoneOffset.UTC) + ".log"), "x\n");
        BlockLog log = new BlockLog(dir, ZoneOffset.UTC);
        log.prune(30);
        log.close();
        assertFalse(Files.exists(old));
        assertTrue(Files.exists(today));
    }

    @Test
    void namesDimensionFoldersSafely() {
        assertEquals("minecraft_the_nether", BlockLog.safe("minecraft:the_nether"));
        assertEquals("aether_the_aether", BlockLog.safe("aether:The_Aether"));
        assertEquals("______", BlockLog.safe("../../"));
    }
}
