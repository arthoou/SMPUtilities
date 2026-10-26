package com.arthou.smputilities.core.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatFilterTest {
    @Test
    void censorsWordsWithLookAlikesAndSeparators() {
        ChatFilter filter = new ChatFilter(List.of("idiota", "noob"), true, false);
        ChatFilter.Result result = filter.check("Você é um 1d10t4, n.o.o.b!");
        assertEquals(ChatFilter.Verdict.CENSORED, result.verdict());
        assertEquals("Você é um ******, *******!", result.text());
        assertEquals("1d10t4", result.match());
        assertEquals(ChatFilter.Verdict.CLEAN, filter.check("hello there").verdict());
        assertEquals("IDIÓTA", filter.check("IDIÓTA").match(), "case and accents fold");
    }

    @Test
    void wholeWordsAndWordBoundaries() {
        ChatFilter filter = new ChatFilter(List.of("=ass"), true, false);
        assertEquals(ChatFilter.Verdict.CLEAN, filter.check("first class assignment").verdict());
        assertEquals("you ***", filter.check("you ass").text());
        ChatFilter parts = new ChatFilter(List.of("ass"), true, false);
        assertEquals("cl*** ***ignment", parts.check("class assignment").text(), "parts of words without =");
        assertEquals(ChatFilter.Verdict.CLEAN, parts.check("a ss").verdict(), "matches never cross words");
    }

    @Test
    void blocksInsteadOfCensoring() {
        ChatFilter filter = new ChatFilter(List.of("bad"), false, false);
        ChatFilter.Result result = filter.check("this is B.A.D");
        assertEquals(ChatFilter.Verdict.BLOCKED, result.verdict());
        assertEquals("this is B.A.D", result.text());
    }

    @Test
    void blocksAdvertising() {
        ChatFilter filter = new ChatFilter(List.of(), true, true);
        assertEquals(ChatFilter.Verdict.BLOCKED, filter.check("join 123.45.67.89 now").verdict());
        assertEquals(ChatFilter.Verdict.BLOCKED, filter.check("join 123,45,67,89").verdict());
        assertEquals(ChatFilter.Verdict.BLOCKED, filter.check("discord.gg/abcdef").verdict());
        assertEquals(ChatFilter.Verdict.CLEAN, filter.check("version 1.21.11 is out").verdict());
        assertEquals(ChatFilter.Verdict.CLEAN, filter.check("999.999.999.999").verdict());
        assertTrue(new ChatFilter(List.of(" ", ""), true, false).isEmpty());
    }
}
