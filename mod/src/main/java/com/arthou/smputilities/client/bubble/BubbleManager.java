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

/** Active speech bubbles per player (from Bubbles4All), newest first. */
public final class BubbleManager {
    public record Bubble(String text, boolean tts, long arrivedAt, float seconds, float fadeSeconds, int maxWidth,
                         int textColor, int fillColor, int borderColor, String style) {
    }

    private static final int MAX_PER_PLAYER = 3;
    private static final Map<UUID, List<Bubble>> ACTIVE = new ConcurrentHashMap<>();

    private BubbleManager() {
    }

    public static void add(Payloads.Bubble payload) {
        if (!ClientConfig.get().bubbles.enabled || payload.text().isBlank()) {
            return;
        }
        List<Bubble> list = ACTIVE.computeIfAbsent(payload.speaker(), ignored -> new ArrayList<>());
        synchronized (list) {
            list.add(0, new Bubble(payload.text(), payload.tts(), System.currentTimeMillis(), Math.max(1, payload.seconds()),
                Math.max(0.05F, payload.fadeSeconds()), Mth.clamp(payload.maxWidth(), 40, 400), payload.textColor(),
                payload.fillColor(), payload.borderColor(), payload.style()));
            while (list.size() > MAX_PER_PLAYER) {
                list.remove(list.size() - 1);
            }
        }
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

    public static float alpha(Bubble bubble, long now) {
        long fadeStart = bubble.arrivedAt() + (long) (bubble.seconds() * 1000);
        long fadeMillis = (long) (bubble.fadeSeconds() * 1000);
        if (now <= fadeStart) {
            // Short pop-in.
            return Math.min(1.0F, (now - bubble.arrivedAt()) / 120.0F);
        }
        return Mth.clamp(1.0F - (now - fadeStart) / (float) fadeMillis, 0, 1);
    }

    public static void tick() {
        if (ACTIVE.isEmpty()) {
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
    }

    /** Cheap check for the renderer, which runs every frame. */
    public static boolean hasAny() {
        return !ACTIVE.isEmpty();
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
