package com.arthou.smputilities.core;

import com.arthou.smputilities.core.chat.MentionFinder;
import com.arthou.smputilities.core.chat.Placeholders;
import com.arthou.smputilities.core.chat.SpamGuard;
import com.arthou.smputilities.core.chat.StyledText;
import com.arthou.smputilities.core.config.ConfigPaths;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.dimension.DimensionRules;
import com.arthou.smputilities.core.nick.NicknameRules;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.perm.PermissionRules;
import com.arthou.smputilities.core.teleport.Cooldowns;
import com.arthou.smputilities.core.teleport.TpaRequests;
import com.arthou.smputilities.core.util.Durations;
import com.arthou.smputilities.core.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreLogicTest {
    @Test
    void durations() {
        assertEquals(90_000L, Durations.parse("1m30s"));
        assertEquals(86_400_000L + 2 * 3_600_000L, Durations.parse("1d2h"));
        assertEquals(-1L, Durations.parse("perm"));
        assertEquals(2_592_000_000L, Durations.parse("1mo"));
        assertNull(Durations.parse("abc"));
        assertNull(Durations.parse("10x"));
        assertNull(Durations.parse(""));
        assertEquals("1d 2h", Durations.format(86_400_000L + 2 * 3_600_000L));
        assertEquals("0s", Durations.format(0));
        assertEquals("∞", Durations.format(-1));
    }

    @Test
    void styledText() {
        StyledText text = StyledText.parse("&aHello &c&lWorld&r!");
        assertEquals("Hello World!", text.plain());
        assertEquals(3, text.runs().size());
        assertEquals(0x55FF55, text.runs().get(0).color());
        assertEquals(0xFF5555, text.runs().get(1).color());
        assertTrue(text.runs().get(1).bold());
        assertEquals(-1, text.runs().get(2).color());

        StyledText hex = StyledText.parse("&#ff8800Orange");
        assertEquals(0xFF8800, hex.runs().get(0).color());
        assertEquals("Orange", hex.plain());

        assertEquals("a&b", StyledText.parse("a&&b").plain());
        assertEquals("&zx", StyledText.parse("&zx").plain());
        assertEquals("&aHi", StyledText.parse("&aHi", false).plain());
    }

    @Test
    void placeholdersEscapePlayerInput() {
        String line = Placeholders.apply("&e{name}&8 » &f{message}", Map.of("name", "Steve", "message", Placeholders.escapeCodes("&cred?")));
        assertEquals("Steve » &cred?", StyledText.parse(line).plain());
        assertEquals("{unknown}", Placeholders.apply("{unknown}", Map.of()));
    }

    @Test
    void mentions() {
        List<MentionFinder.Candidate<String>> players = List.of(
            new MentionFinder.Candidate<>("a", List.of("Steve", "Capitão Jack")),
            new MentionFinder.Candidate<>("b", List.of("Alex")),
            new MentionFinder.Candidate<>("c", List.of("Al")));
        assertEquals(Set.of("a"), MentionFinder.find("hey steve, how are you", players, false));
        assertEquals(Set.of("a", "b"), MentionFinder.find("@Alex and capitão jack!", players, false));
        assertEquals(Set.of(), MentionFinder.find("Steven is here", players, false));
        assertEquals(Set.of(), MentionFinder.find("steve without at", players, true));
        assertEquals(Set.of("a"), MentionFinder.find("hi @steve", players, true));
        assertEquals(Set.of("c"), MentionFinder.find("al?", players, false));
    }

    @Test
    void spamGuard() {
        SpamGuard guard = new SpamGuard();
        UUID player = UUID.randomUUID();
        SpamGuard.Settings settings = new SpamGuard.Settings(1000, true, 3, 10_000, 5000);
        assertTrue(guard.check(player, "one", settings, 0).allowed());
        assertEquals(SpamGuard.Verdict.COOLDOWN, guard.check(player, "two", settings, 500).verdict());
        assertTrue(guard.check(player, "two", settings, 1500).allowed());
        assertEquals(SpamGuard.Verdict.DUPLICATE, guard.check(player, "TWO ", settings, 3000).verdict());
        assertTrue(guard.check(player, "three", settings, 4000).allowed());
        assertEquals(SpamGuard.Verdict.RATE_LIMIT, guard.check(player, "four", settings, 5000).verdict());
        assertTrue(guard.check(player, "four", settings, 11_000).allowed());
        assertEquals("hello world", SpamGuard.limitCaps("HELLO WORLD", 0.7));
        assertEquals("Hello World", SpamGuard.limitCaps("Hello World", 0.7));
    }

    @Test
    void configPaths() {
        SuConfig config = new SuConfig();
        assertNull(ConfigPaths.set(config, "chat.customFormat", "true"));
        assertTrue(config.chat.customFormat);
        assertNull(ConfigPaths.set(config, "teleport.maxHomes", "7"));
        assertEquals(7, config.teleport.maxHomes);
        assertNull(ConfigPaths.set(config, "tts.bubbleFillColor", "0xFFEEDD"));
        assertEquals(0xFFEEDD, config.tts.bubbleFillColor);
        assertEquals("invalid_value", ConfigPaths.set(config, "teleport.maxHomes", "lots"));
        assertEquals("unknown_setting", ConfigPaths.set(config, "chat.nope", "1"));
        assertEquals("unknown_setting", ConfigPaths.set(config, "portals.limits", "1"));
        assertNotNull(ConfigPaths.get(config, "chat.spam.maxMessages"));
        assertTrue(ConfigPaths.list(config).stream().anyMatch(entry -> entry.path().equals("chat.mentions.sound")));
    }

    @Test
    void partialConfigKeepsDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("server.json");
        Files.writeString(file, "{\"chat\":{\"customFormat\":true},\"teleport\":{\"maxHomes\":9}}");
        SuConfig config = Json.read(file, SuConfig.class, new SuConfig());
        assertTrue(config.chat.customFormat);
        assertEquals("&7[&a+&7] &f{name}", config.chat.joinFormat);
        assertEquals(9, config.teleport.maxHomes);
        assertEquals(3, config.teleport.warmupSeconds);
        assertEquals("minecraft:the_nether", config.dimensions.aliases.get("nether"));

        Files.writeString(file, "{ broken");
        SuConfig fallback = Json.read(file, SuConfig.class, new SuConfig());
        assertFalse(fallback.chat.customFormat);
        assertTrue(Files.exists(dir.resolve("server.json.broken")));
    }

    @Test
    void permissions() {
        SuConfig config = new SuConfig();
        UUID id = UUID.randomUUID();
        assertFalse(PermissionRules.fallback(config, Perm.STAFF_BAN, id, "Mod", 2));
        assertTrue(PermissionRules.fallback(config, Perm.STAFF_BAN, id, "Mod", 3));
        config.permissions.levelOverrides.put(Perm.STAFF_BAN.node(), 2);
        assertTrue(PermissionRules.fallback(config, Perm.STAFF_BAN, id, "Mod", 2));
        config.permissions.grants.put(Perm.STAFF_TROLL.node(), List.of("mod"));
        assertTrue(PermissionRules.fallback(config, Perm.STAFF_TROLL, id, "Mod", 0));
        assertEquals("staff.panel", Perm.STAFF_PANEL.path());
        assertEquals(Perm.STAFF_PANEL, Perm.byNode("smputilities.staff.panel"));
    }

    @Test
    void dimensionsAndNicknames() {
        SuConfig config = new SuConfig();
        assertEquals("minecraft:the_nether", DimensionRules.resolve(config.dimensions, "Nether"));
        assertEquals("minecraft:the_end", DimensionRules.resolve(config.dimensions, "the_end"));
        assertEquals("aether:the_aether", DimensionRules.resolve(config.dimensions, "aether"));
        assertEquals("nether", DimensionRules.shortName(config.dimensions, "minecraft:the_nether"));
        assertTrue(DimensionRules.setBlocked(config.dimensions, "minecraft:the_end", true));
        assertFalse(DimensionRules.setBlocked(config.dimensions, "minecraft:the_end", true));
        assertTrue(DimensionRules.isBlocked(config.dimensions, "minecraft:the_end"));

        assertEquals("capitão jack", NicknameRules.key("&6Capitão  &lJack "));
        assertNull(NicknameRules.validate(config.nicknames, "&6Capitão Jack", true));
        assertEquals("nick.too_short", NicknameRules.validate(config.nicknames, "J", true));
        assertEquals("nick.invalid_chars", NicknameRules.validate(config.nicknames, "Jack<script>", true));
        assertEquals("nick.no_colors", NicknameRules.validate(config.nicknames, "&cJack", false));
    }

    @Test
    void tpaAndCooldowns() {
        TpaRequests requests = new TpaRequests();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        requests.add(new TpaRequests.Request(a, "A", c, TpaRequests.Kind.TO_TARGET, 0));
        requests.add(new TpaRequests.Request(b, "B", c, TpaRequests.Kind.HERE, 10));
        assertEquals(b, requests.take(c, null, 20, 1000).orElseThrow().requester());
        assertTrue(requests.take(c, a, 2000, 1000).isEmpty());

        Cooldowns cooldowns = new Cooldowns();
        cooldowns.start(a, "home", 5000, 0);
        assertEquals(3000, cooldowns.remaining(a, "home", 2000));
        assertEquals(0, cooldowns.remaining(a, "home", 6000));
        assertEquals(0, cooldowns.remaining(b, "home", 0));
    }
}
