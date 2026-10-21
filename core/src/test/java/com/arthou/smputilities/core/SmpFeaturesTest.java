package com.arthou.smputilities.core;

import com.arthou.smputilities.core.afk.AfkTracker;
import com.arthou.smputilities.core.report.Report;
import com.arthou.smputilities.core.report.ReportStore;
import com.arthou.smputilities.core.restart.RestartSchedule;
import com.arthou.smputilities.core.teleport.Location;
import com.arthou.smputilities.core.teleport.TpaRequests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmpFeaturesTest {
    private static final AfkTracker.Settings FIVE_MINUTES = new AfkTracker.Settings(300_000, 0);

    @Test
    void afkAfterIdleTimeAndBackOnActivity() {
        AfkTracker afk = new AfkTracker();
        UUID id = UUID.randomUUID();
        afk.join(id, 0);
        assertEquals(AfkTracker.Change.NONE, afk.sample(id, 0, 0, 0, 0, true, 1000, FIVE_MINUTES));
        assertEquals(AfkTracker.Change.NONE, afk.sample(id, 0, 0, 0, 0, true, 299_000, FIVE_MINUTES));
        assertEquals(AfkTracker.Change.BECAME_AFK, afk.sample(id, 0, 0, 0, 0, true, 300_000, FIVE_MINUTES));
        assertTrue(afk.isAfk(id));
        assertFalse(afk.isManual(id));
        // Pushed by water (not walking) is not activity.
        assertEquals(AfkTracker.Change.NONE, afk.sample(id, 0, 0, 5, 5, false, 301_000, FIVE_MINUTES));
        assertTrue(afk.isAfk(id));
        // Looking around is.
        assertEquals(AfkTracker.Change.RETURNED, afk.sample(id, 45, 0, 5, 5, false, 302_000, FIVE_MINUTES));
        assertFalse(afk.isAfk(id));
    }

    @Test
    void manualAfkEndsWithChatAndKicksAfterIdleTime() {
        AfkTracker afk = new AfkTracker();
        UUID id = UUID.randomUUID();
        afk.join(id, 0);
        afk.sample(id, 0, 0, 0, 0, true, 1000, FIVE_MINUTES);
        assertTrue(afk.set(id, true, "lunch", 2000));
        assertTrue(afk.isManual(id));
        assertEquals("lunch", afk.reason(id));
        assertEquals(AfkTracker.Change.NONE, afk.sample(id, 0, 0, 0, 0, true, 3000, FIVE_MINUTES));
        afk.activity(id, 4000);
        assertEquals(AfkTracker.Change.RETURNED, afk.sample(id, 0, 0, 0, 0, true, 5000, FIVE_MINUTES));

        AfkTracker.Settings kick = new AfkTracker.Settings(60_000, 120_000);
        afk.join(id, 0);
        afk.sample(id, 0, 0, 0, 0, true, 0, kick);
        assertEquals(AfkTracker.Change.BECAME_AFK, afk.sample(id, 0, 0, 0, 0, true, 60_000, kick));
        assertEquals(AfkTracker.Change.NONE, afk.sample(id, 0, 0, 0, 0, true, 90_000, kick));
        assertEquals(AfkTracker.Change.KICK, afk.sample(id, 0, 0, 0, 0, true, 120_000, kick));
    }

    @Test
    void reportsKeepOpenOnesAndPruneClosed(@TempDir Path dir) {
        ReportStore store = new ReportStore(dir.resolve("reports.json"));
        store.load();
        UUID target = UUID.randomUUID();
        Location where = new Location("minecraft:overworld", 1, 2, 3, 0, 0);
        Report first = store.create(UUID.randomUUID(), "A", target, "Griefer", "broke my house", null, 10);
        Report second = store.create(UUID.randomUUID(), "B", target, "Griefer", "stole", null, 20);
        Report third = store.create(UUID.randomUUID(), "C", UUID.randomUUID(), "Other", "spam", where, 30);
        assertEquals(List.of(1, 2, 3), List.of(first.id, second.id, third.id));
        assertEquals(2, store.openAgainst(target));
        assertTrue(store.close(first.id, "Mod", 40, 1));
        assertFalse(store.close(first.id, "Mod", 41, 1));
        assertTrue(store.close(second.id, "Mod", 50, 1));
        // Only the newest closed report is kept.
        assertTrue(store.get(first.id).isEmpty());
        assertTrue(store.get(second.id).isPresent());

        ReportStore reloaded = new ReportStore(dir.resolve("reports.json"));
        reloaded.load();
        assertEquals(1, reloaded.open().size());
        assertEquals(third.id, reloaded.open().get(0).id);
        assertEquals(where, reloaded.open().get(0).where);
        assertEquals(4, reloaded.create(UUID.randomUUID(), "D", target, "Griefer", "again", null, 60).id);
    }

    @Test
    void restartWarningsAndDailyTimes() {
        List<Integer> warnings = List.of(600, 60, 10, 5, 1);
        assertEquals(List.of(600), RestartSchedule.crossed(warnings, 601, 600));
        assertEquals(List.of(), RestartSchedule.crossed(warnings, 600, 599));
        assertEquals(List.of(10, 5), RestartSchedule.crossed(warnings, 11, 4));

        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 21, 5, 30, 0, 0, zone);
        long next = RestartSchedule.nextDaily(List.of("06:00", "bad", "18:00"), now);
        assertEquals(ZonedDateTime.of(2026, 10, 21, 6, 0, 0, 0, zone).toInstant().toEpochMilli(), next);
        long tomorrow = RestartSchedule.nextDaily(List.of("05:00"), now);
        assertEquals(ZonedDateTime.of(2026, 10, 22, 5, 0, 0, 0, zone).toInstant().toEpochMilli(), tomorrow);
        assertEquals(-1, RestartSchedule.nextDaily(List.of(), now));
    }

    @Test
    void tpaCancelWithdrawsOnlyTheRequestersRequests() {
        TpaRequests requests = new TpaRequests();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID carol = UUID.randomUUID();
        requests.add(new TpaRequests.Request(alice, "Alice", bob, TpaRequests.Kind.TO_TARGET, 0));
        requests.add(new TpaRequests.Request(alice, "Alice", carol, TpaRequests.Kind.HERE, 0));
        requests.add(new TpaRequests.Request(carol, "Carol", bob, TpaRequests.Kind.TO_TARGET, 0));
        assertEquals(2, requests.cancel(alice, 1000, 60_000).size());
        assertFalse(requests.hasPending(alice, bob, 1000, 60_000));
        assertTrue(requests.hasPending(carol, bob, 1000, 60_000));
        assertEquals(0, requests.cancel(alice, 1000, 60_000).size());
    }
}
