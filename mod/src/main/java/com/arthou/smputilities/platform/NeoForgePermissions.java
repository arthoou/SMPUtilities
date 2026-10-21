package com.arthou.smputilities.platform;

import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.perm.PermissionRules;
import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registers every {@link Perm} with NeoForge's permission API. The default
 * resolver implements SMPutilities' own rules (config grants and op levels);
 * a permission plugin such as LuckPerms replaces it transparently.
 */
public final class NeoForgePermissions {
    private static final Map<Perm, PermissionNode<Boolean>> NODES = new EnumMap<>(Perm.class);
    private static final PermissionNode<Integer> HOME_LIMIT = new PermissionNode<>(SmpUtilities.MOD_ID, "teleport.home.limit",
        PermissionTypes.INTEGER, (player, uuid, context) -> SuServer.config().teleport.maxHomes);
    private static final PermissionNode<Integer> PROFILE_LIMIT = new PermissionNode<>(SmpUtilities.MOD_ID, "profile.limit",
        PermissionTypes.INTEGER, (player, uuid, context) -> SuServer.config().profiles.maxProfilesPerPlayer);
    private static volatile boolean registered;

    static {
        for (Perm perm : Perm.values()) {
            NODES.put(perm, new PermissionNode<>(SmpUtilities.MOD_ID, perm.path(), PermissionTypes.BOOLEAN,
                (player, uuid, context) -> player != null && fallback(player, perm)));
        }
    }

    private NeoForgePermissions() {
    }

    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(NODES.values().toArray(new PermissionNode<?>[0]));
        event.addNodes(HOME_LIMIT, PROFILE_LIMIT);
        registered = true;
    }

    public static boolean check(ServerPlayer player, Perm perm) {
        if (registered) {
            try {
                return PermissionAPI.getPermission(player, NODES.get(perm));
            } catch (RuntimeException e) {
                SuLog.warn("Permission check for " + perm.node() + " failed, using op levels", e);
            }
        }
        return fallback(player, perm);
    }

    public static int homeLimit(ServerPlayer player) {
        return integer(player, HOME_LIMIT, SuServer.config().teleport.maxHomes);
    }

    public static int profileLimit(ServerPlayer player) {
        return integer(player, PROFILE_LIMIT, SuServer.config().profiles.maxProfilesPerPlayer);
    }

    private static int integer(ServerPlayer player, PermissionNode<Integer> node, int fallback) {
        if (registered) {
            try {
                Integer value = PermissionAPI.getPermission(player, node);
                return value == null ? fallback : value;
            } catch (RuntimeException ignored) {
                // fall through to the config value
            }
        }
        return fallback;
    }

    private static boolean fallback(ServerPlayer player, Perm perm) {
        return PermissionRules.fallback(SuServer.config(), perm, player.getUUID(), player.getGameProfile().getName(), Perms.opLevel(player));
    }
}
