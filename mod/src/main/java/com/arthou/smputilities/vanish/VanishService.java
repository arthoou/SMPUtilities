package com.arthou.smputilities.vanish;

import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.chat.ChatService;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.player.PlayerRecord;
import com.arthou.smputilities.mixin.ChunkMapAccessor;
import com.arthou.smputilities.network.SuNetwork;
import com.arthou.smputilities.server.Lang;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Administrative invisibility enforced by the server: vanished players are
 * not sent to unauthorised clients at all (see TrackedEntityMixin), are
 * removed from their tab list and produce no join/quit lines.
 */
public final class VanishService {
    private static final Set<UUID> VANISHED = ConcurrentHashMap.newKeySet();

    private VanishService() {
    }

    /** Restores persisted vanish state when the server starts, before anyone joins. */
    public static void onServerStart(SuServer context) {
        VANISHED.clear();
        if (!SuServer.config().vanish.persistent) {
            return;
        }
        for (PlayerRecord record : context.players().all()) {
            if (record.vanished && record.uuid != null) {
                VANISHED.add(record.uuid);
            }
        }
    }

    public static boolean isVanished(UUID id) {
        return SuServer.config().features.vanish && VANISHED.contains(id);
    }

    public static Set<UUID> vanished() {
        return Set.copyOf(VANISHED);
    }

    public static boolean canSee(ServerPlayer viewer, ServerPlayer target) {
        return viewer == target || !isVanished(target.getUUID()) || Perms.has(viewer, Perm.STAFF_VANISH_SEE);
    }

    /** Hot path for entity tracking; only does the permission check for vanished targets. */
    public static boolean isHiddenFrom(ServerPlayer viewer, ServerPlayer target) {
        return !VANISHED.isEmpty() && VANISHED.contains(target.getUUID()) && SuServer.config().features.vanish
            && !Perms.has(viewer, Perm.STAFF_VANISH_SEE);
    }

    public static void set(ServerPlayer player, boolean vanish, ServerPlayer actor) {
        SuServer context = SuServer.get();
        if (context == null) {
            return;
        }
        SuConfig.Vanish config = SuServer.config().vanish;
        boolean changed = vanish ? VANISHED.add(player.getUUID()) : VANISHED.remove(player.getUUID());
        context.players().update(player.getUUID(), record -> record.vanished = vanish);
        if (!changed) {
            return;
        }
        refresh(player);
        if (config.fakeMessagesOnToggle) {
            ChatService.broadcastPresence(player, !vanish, true);
        }
        if (vanish) {
            Msg.ok(player, "vanish.enabled");
        } else {
            Msg.ok(player, "vanish.disabled");
            player.displayClientMessage(Texts.empty(), true);
        }
        context.log(actor, vanish ? "vanish.on" : "vanish.off", player.getUUID(), player.getGameProfile().getName(), "");
        SuNetwork.syncStateToStaff(context.server());
    }

    /** Re-applies visibility of {@code target} for every online viewer. */
    public static void refresh(ServerPlayer target) {
        MinecraftServer server = Mc.server(target);
        boolean hideTab = SuServer.config().vanish.hideFromTabList;
        Object tracked = ((ChunkMapAccessor) target.serverLevel().getChunkSource().chunkMap).smputilities$entityMap().get(target.getId());
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer == target) {
                continue;
            }
            boolean hidden = isHiddenFrom(viewer, target);
            if (hidden && hideTab) {
                viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(target.getUUID())));
            } else if (!hidden) {
                // The client only spawns a player entity it has tab info for.
                viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(target)));
            }
            if (tracked instanceof SuTrackedEntity entity) {
                entity.smputilities$update(viewer);
            }
        }
    }

    /** A player just joined: hide every vanished player from them, and them from others if vanished. */
    public static void onLogin(ServerPlayer player) {
        if (!SuServer.config().features.vanish) {
            return;
        }
        boolean hideTab = SuServer.config().vanish.hideFromTabList;
        for (ServerPlayer other : Mc.server(player).getPlayerList().getPlayers()) {
            if (other != player && isHiddenFrom(player, other) && hideTab) {
                player.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(other.getUUID())));
            }
        }
        if (isVanished(player.getUUID())) {
            refresh(player);
            Msg.info(player, "vanish.still_on");
        }
    }

    public static void onLogout(ServerPlayer player) {
        if (!SuServer.config().vanish.persistent) {
            VANISHED.remove(player.getUUID());
            SuServer context = SuServer.get();
            if (context != null) {
                context.players().update(player.getUUID(), record -> record.vanished = false);
            }
        }
    }

    /** Called every second: action bar reminder for vanished players. */
    public static void tickIndicator(MinecraftServer server) {
        if (!SuServer.config().vanish.actionBarIndicator || VANISHED.isEmpty()) {
            return;
        }
        for (UUID id : VANISHED) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.displayClientMessage(Texts.literal(Lang.tr(player, "vanish.indicator"))
                    .withStyle(Style.EMPTY.withColor(Msg.ACCENT).withBold(true)), true);
            }
        }
    }

    public static boolean shouldBlockTarget(LivingEntity target) {
        return target instanceof ServerPlayer player && SuServer.config().vanish.preventMobTargeting && isVanished(player.getUUID());
    }

    public static boolean shouldBlockPickup(ServerPlayer player) {
        return SuServer.config().vanish.preventItemPickup && isVanished(player.getUUID());
    }
}
