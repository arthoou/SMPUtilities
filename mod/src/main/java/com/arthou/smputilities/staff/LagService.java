package com.arthou.smputilities.staff;

import com.arthou.smputilities.mc.Ids;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.network.Dto;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The panel's performance view: tick times, what each dimension holds, the busiest chunks, and clean-ups. */
final class LagService {
    private static final int HOTSPOTS = 15;

    private LagService() {
    }

    /** One row per dimension: chunks, entities, items, XP orbs, hostile mobs; the first row is the server. */
    static Dto.Table overview(MinecraftServer server) {
        List<Dto.Row> rows = new ArrayList<>();
        double mspt = Mc.millisPerTick(server);
        double tps = Math.min(Mc.targetTickRate(server), mspt <= 0 ? Mc.targetTickRate(server) : 1000.0 / mspt);
        rows.add(new Dto.Row("server", List.of("server", String.format(Locale.ROOT, "%.1f", tps), String.format(Locale.ROOT, "%.1f", mspt),
            String.valueOf(server.getPlayerCount()), "", "", "", "")));
        for (ServerLevel level : server.getAllLevels()) {
            int entities = 0;
            int items = 0;
            int orbs = 0;
            int hostile = 0;
            for (Entity entity : level.getAllEntities()) {
                entities++;
                if (entity instanceof ItemEntity) {
                    items++;
                } else if (entity instanceof ExperienceOrb) {
                    orbs++;
                } else if (entity instanceof Enemy) {
                    hostile++;
                }
            }
            String dimension = Ids.key(level.dimension());
            rows.add(new Dto.Row(dimension, List.of(dimension, "", "", String.valueOf(level.players().size()),
                String.valueOf(level.getChunkSource().getLoadedChunksCount()), String.valueOf(entities), String.valueOf(items),
                orbs + " / " + hostile)));
        }
        return new Dto.Table("lag", "", List.of("name", "tps", "mspt", "players", "chunks", "entities", "items", "orbs_hostile"), rows);
    }

    /** The chunks with the most entities, with the most common kind. */
    static Dto.Table hotspots(MinecraftServer server) {
        record Spot(String dimension, int x, int z, int count, String top) {
        }
        List<Spot> spots = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            Map<Long, Map<String, Integer>> chunks = new HashMap<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Player) {
                    continue;
                }
                long key = (((long) entity.getBlockX() >> 4) << 32) | (((long) entity.getBlockZ() >> 4) & 0xFFFFFFFFL);
                chunks.computeIfAbsent(key, k -> new HashMap<>()).merge(entity.getType().getDescription().getString(), 1, Integer::sum);
            }
            String dimension = Ids.key(level.dimension());
            for (Map.Entry<Long, Map<String, Integer>> chunk : chunks.entrySet()) {
                int total = chunk.getValue().values().stream().mapToInt(Integer::intValue).sum();
                String top = chunk.getValue().entrySet().stream().max(Map.Entry.comparingByValue())
                    .map(entry -> entry.getKey() + " ×" + entry.getValue()).orElse("");
                spots.add(new Spot(dimension, (int) (chunk.getKey() >> 32), (int) (long) chunk.getKey(), total, top));
            }
        }
        spots.sort(Comparator.comparingInt(Spot::count).reversed());
        List<Dto.Row> rows = new ArrayList<>();
        for (Spot spot : spots.subList(0, Math.min(HOTSPOTS, spots.size()))) {
            int blockX = spot.x() * 16 + 8;
            int blockZ = spot.z() * 16 + 8;
            rows.add(new Dto.Row(spot.dimension() + " " + blockX + " " + blockZ, List.of(spot.dimension(), blockX + " " + blockZ,
                String.valueOf(spot.count()), spot.top())));
        }
        return new Dto.Table("hotspots", "", List.of("dimension", "position", "count", "top"), rows);
    }

    /** Removes dropped items, XP orbs or hostile mobs that are not named or kept on purpose; returns how many. */
    static int clear(MinecraftServer server, String what) {
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> doomed = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                boolean match = switch (what) {
                    case "items" -> entity instanceof ItemEntity;
                    case "xp" -> entity instanceof ExperienceOrb;
                    case "hostile" -> entity instanceof Enemy && entity instanceof Mob mob && !mob.isPersistenceRequired()
                        && !mob.hasCustomName() && !mob.isPassenger() && !mob.isVehicle();
                    default -> false;
                };
                if (match) {
                    doomed.add(entity);
                }
            }
            for (Entity entity : doomed) {
                entity.discard();
                removed++;
            }
        }
        return removed;
    }
}
