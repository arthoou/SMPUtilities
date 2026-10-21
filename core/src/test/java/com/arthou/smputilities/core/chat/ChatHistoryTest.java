package com.arthou.smputilities.core.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatHistoryTest {
    private static final long DAY_START = LocalDate.of(2026, 10, 21).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

    @Test
    void pagesWalkBackThroughTheWholeDay(@TempDir Path dir) {
        UUID sender = UUID.randomUUID();
        ChatHistory writer = new ChatHistory(dir, ZoneOffset.UTC);
        for (int i = 0; i < 2500; i++) {
            writer.append(DAY_START + i, sender, "Arthou", "message " + i + " çãé ♪ " + "x".repeat(i % 40));
        }
        writer.close();

        ChatHistory history = new ChatHistory(dir, ZoneOffset.UTC);
        List<String> seen = new ArrayList<>();
        long before = -1;
        while (true) {
            List<ChatHistory.Line> page = history.read("2026-10-21", before, 100);
            if (page.isEmpty()) {
                break;
            }
            for (ChatHistory.Line line : page) {
                seen.add(line.text());
                assertEquals("Arthou", line.name());
                assertEquals(sender.toString(), line.sender());
            }
            before = page.get(page.size() - 1).offset();
            if (before == 0) {
                break;
            }
        }
        assertEquals(2500, seen.size());
        assertTrue(seen.get(0).startsWith("message 2499 "));
        assertTrue(seen.get(2499).startsWith("message 0 "));
        assertEquals(List.of("2026-10-21"), history.days(10));
    }

    @Test
    void tabsAndNewlinesStayInsideTheirMessage(@TempDir Path dir) {
        ChatHistory writer = new ChatHistory(dir, ZoneOffset.UTC);
        writer.append(DAY_START, null, "A\tB", "one\ttwo\nthree \\ four");
        writer.close();
        List<ChatHistory.Line> lines = new ChatHistory(dir, ZoneOffset.UTC).read("2026-10-21", -1, 10);
        assertEquals(1, lines.size());
        assertEquals("one\ttwo\nthree \\ four", lines.get(0).text());
        assertEquals("A B", lines.get(0).name());
    }

    @Test
    void anUnfinishedLineAndBadDaysAreIgnored(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("2026-10-21.log"), DAY_START + "\t\tA\tdone\n" + DAY_START + "\t\tB\thalf", StandardCharsets.UTF_8,
            StandardOpenOption.CREATE);
        ChatHistory history = new ChatHistory(dir, ZoneOffset.UTC);
        List<ChatHistory.Line> lines = history.read("2026-10-21", -1, 10);
        assertEquals(1, lines.size());
        assertEquals("done", lines.get(0).text());
        assertTrue(history.read("../../etc/passwd", -1, 10).isEmpty());
        assertTrue(history.read("2026-10-22", -1, 10).isEmpty());
    }
}
