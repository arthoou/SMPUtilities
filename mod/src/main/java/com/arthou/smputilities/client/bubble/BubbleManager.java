package com.arthou.smputilities.client.bubble;

import com.arthou.smputilities.client.ClientConfig;
import com.arthou.smputilities.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Active speech bubbles per player, as in NEXEL and LeafTalk: every message
 * keeps its own bubble, the newest nearest the head, and stays fully visible
 * until its time is up, then fades. A player who is typing shows Nexel's
 * animated "..." bubble until the message arrives.
 */
public final class BubbleManager {
    public record Bubble(String text, boolean tts, long arrivedAt, float seconds, float fadeSeconds, int maxWidth,
                         int textColor, int fillColor, int borderColor, String style) {
    }

    /** Bounds memory when someone floods the chat; older bubbles are dropped first. */
    private static final int MAX_PER_PLAYER = 8;
    private static final int MAX_TEXT = 2048;
    /** The typing bubble disappears when its heartbeat stops (Nexel's timeout). */
    private static final long TYPING_TIMEOUT_MILLIS = 1250;
    private static final Map<UUID, List<Bubble>> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> TYPING = new ConcurrentHashMap<>();

    private BubbleManager() {
    }

    public static void add(Payloads.Bubble payload) {
        String text = clean(payload.text());
        if (!ClientConfig.get().bubbles.enabled || text.isEmpty()) {
            return;
        }
        TYPING.remove(payload.speaker());
        List<Bubble> list = ACTIVE.computeIfAbsent(payload.speaker(), ignored -> new ArrayList<>());
        synchronized (list) {
            list.add(0, new Bubble(text, payload.tts(), System.currentTimeMillis(), Math.max(1, payload.seconds()),
                Math.max(0.05F, payload.fadeSeconds()), Mth.clamp(payload.maxWidth(), 40, 400), payload.textColor(),
                payload.fillColor(), payload.borderColor(), payload.style()));
            while (list.size() > MAX_PER_PLAYER) {
                list.remove(list.size() - 1);
            }
        }
    }

    public static void typing(UUID speaker, boolean active) {
        if (active && ClientConfig.get().bubbles.enabled) {
            TYPING.put(speaker, System.currentTimeMillis());
        } else {
            TYPING.remove(speaker);
        }
    }

    public static boolean isTyping(UUID speaker, long now) {
        Long since = TYPING.get(speaker);
        return since != null && now - since <= TYPING_TIMEOUT_MILLIS;
    }

    public static List<Bubble> bubbles(UUID player) {
        List<Bubble> list = ACTIVE.get(player);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    /** Fully visible until the bubble's time is up, then a linear fade. */
    public static float alpha(Bubble bubble, long now) {
        long fadeStart = bubble.arrivedAt() + (long) (bubble.seconds() * 1000);
        if (now <= fadeStart) {
            return 1.0F;
        }
        return Mth.clamp(1.0F - (now - fadeStart) / (bubble.fadeSeconds() * 1000F), 0, 1);
    }

    public static void tick() {
        if (ACTIVE.isEmpty() && TYPING.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Minecraft minecraft = Minecraft.getInstance();
        ACTIVE.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                entry.getValue().removeIf(bubble -> now > bubble.arrivedAt() + (long) ((bubble.seconds() + bubble.fadeSeconds()) * 1000));
                return entry.getValue().isEmpty() || minecraft.level == null || minecraft.level.getPlayerByUUID(entry.getKey()) == null;
            }
        });
        TYPING.entrySet().removeIf(entry -> now - entry.getValue() > TYPING_TIMEOUT_MILLIS || minecraft.level == null
            || minecraft.level.getPlayerByUUID(entry.getKey()) == null);
    }

    /** Cheap check for the renderer, which runs every frame. */
    public static boolean hasAny() {
        return !ACTIVE.isEmpty() || !TYPING.isEmpty();
    }

    public static void clear() {
        ACTIVE.clear();
        TYPING.clear();
    }

    /** Control characters and § codes cannot change the bubble's fixed look (LeafTalk). */
    static String clean(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder clean = new StringBuilder();
        value.codePoints().filter(codePoint -> codePoint == '\n' || !Character.isISOControl(codePoint)).limit(MAX_TEXT)
            .forEach(clean::appendCodePoint);
        return clean.toString().replaceAll("§.", "").trim();
    }
}
