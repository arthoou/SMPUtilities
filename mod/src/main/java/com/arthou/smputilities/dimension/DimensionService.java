package com.arthou.smputilities.dimension;

import com.arthou.smputilities.core.chat.Placeholders;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.dimension.DimensionRules;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocks travel into disabled dimensions. Only new travel is stopped: the
 * dimension stays registered and loaded, and nothing in it is touched.
 */
public final class DimensionService {
    private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();

    private DimensionService() {
    }

    public static boolean isBlocked(ResourceKey<Level> dimension) {
        SuConfig config = SuServer.config();
        return config.features.dimensions && DimensionRules.isBlocked(config.dimensions, dimension.location().toString());
    }

    public static boolean mayEnter(ServerPlayer player, ResourceKey<Level> dimension) {
        return !isBlocked(dimension) || Perms.has(player, Perm.DIMENSION_BYPASS);
    }

    /** @return true when the travel must be cancelled */
    public static boolean onTravel(Entity entity, ResourceKey<Level> dimension) {
        if (!isBlocked(dimension)) {
            return false;
        }
        if (entity instanceof ServerPlayer player) {
            if (Perms.has(player, Perm.DIMENSION_BYPASS)) {
                return false;
            }
            sendBlockedMessage(player, dimension);
            return true;
        }
        return SuServer.config().dimensions.blockEntities;
    }

    /** Throttled, because a player standing in a portal retries every tick. */
    public static void sendBlockedMessage(ServerPlayer player, ResourceKey<Level> dimension) {
        long now = System.currentTimeMillis();
        Long last = LAST_MESSAGE.get(player.getUUID());
        if (last != null && now - last < 3000) {
            return;
        }
        LAST_MESSAGE.put(player.getUUID(), now);
        SuConfig.Dimensions config = SuServer.config().dimensions;
        String name = DimensionRules.shortName(config, dimension.location().toString());
        player.sendSystemMessage(Msg.styled(Placeholders.apply(config.blockedMessage, Map.of("dimension", name))));
    }

    public static void forget(ServerPlayer player) {
        LAST_MESSAGE.remove(player.getUUID());
    }
}
