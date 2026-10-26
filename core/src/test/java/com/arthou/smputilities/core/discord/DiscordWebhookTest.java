package com.arthou.smputilities.core.discord;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordWebhookTest {
    private static final String URL = "https://discord.com/api/webhooks/123456789/abc-DEF_123";

    @Test
    void acceptsOnlyDiscordWebhooks() {
        assertTrue(DiscordWebhook.validUrl(URL));
        assertTrue(DiscordWebhook.validUrl("https://ptb.discordapp.com/api/webhooks/1/x"));
        assertFalse(DiscordWebhook.validUrl("http://discord.com/api/webhooks/1/x"));
        assertFalse(DiscordWebhook.validUrl("https://discord.com.evil.net/api/webhooks/1/x"));
        assertFalse(DiscordWebhook.validUrl("https://example.com/api/webhooks/1/x"));
        assertFalse(DiscordWebhook.validUrl(""));
        assertThrows(IllegalArgumentException.class, () -> new DiscordWebhook("https://example.com", "x", null));
    }

    @Test
    void writesJsonWithoutPings() {
        String json = DiscordWebhook.json("hi \"all\" @everyone\nnext", "Server");
        assertEquals("{\"content\":\"hi \\\"all\\\" @everyone\\nnext\",\"username\":\"Server\",\"allowed_mentions\":{\"parse\":[]}}", json);
        assertEquals("\\*\\*bold\\*\\* \\@here", DiscordWebhook.escape("**bold** @here"));
    }

    @Test
    void batchesLinesWithinTheLimit() {
        List<String> lines = List.of("aaaa", "bbbb", "cccc", "d".repeat(30));
        List<String> messages = DiscordWebhook.batch(lines, 10);
        assertEquals(List.of("aaaa\nbbbb", "cccc", "ddddddddd…"), messages);
        assertTrue(DiscordWebhook.batch(List.of(), 10).isEmpty());
    }

    @Test
    void sendsQueuedLinesTogetherAndWaitsWhenAsked() throws Exception {
        List<String> posted = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger calls = new AtomicInteger();
        DiscordWebhook webhook = new DiscordWebhook(URL, "Server", (url, json) -> {
            if (calls.getAndIncrement() == 0) {
                return new DiscordWebhook.Response(429, 0.01);
            }
            posted.add(json);
            return new DiscordWebhook.Response(204, 0);
        });
        webhook.send("one");
        webhook.send("two");
        webhook.send(" ");
        webhook.close();
        assertEquals(1, posted.size());
        assertTrue(posted.get(0).contains("\"one\\ntwo\""), posted.get(0));
        assertEquals(2, calls.get(), "retried after the 429");
    }
}
