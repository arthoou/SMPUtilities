package com.arthou.smputilities.server;

import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.perm.PermissionRules;
import com.arthou.smputilities.platform.NeoForgePermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

/**
 * Permission checks. Players go through the loader's permission API (so
 * LuckPerms and similar plugins can decide); everything else - console,
 * command blocks, functions - falls back to the source's op level.
 */
public final class Perms {
    private Perms() {
    }

    public static boolean has(ServerPlayer player, Perm perm) {
        return NeoForgePermissions.check(player, perm);
    }

    public static boolean has(CommandSourceStack source, Perm perm) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            return has(player, perm);
        }
        return source.hasPermission(PermissionRules.requiredLevel(SuServer.config(), perm));
    }

    public static Predicate<CommandSourceStack> require(Perm perm) {
        return source -> has(source, perm);
    }

    /** Op level the vanilla server gives this player (0-4). */
    public static int opLevel(ServerPlayer player) {
        return player.server.getProfilePermissions(player.getGameProfile());
    }

    public static int homeLimit(ServerPlayer player) {
        if (has(player, Perm.HOME_UNLIMITED)) {
            return Integer.MAX_VALUE;
        }
        return NeoForgePermissions.homeLimit(player);
    }

    public static int profileLimit(ServerPlayer player) {
        if (has(player, Perm.PROFILE_BYPASS_LIMIT)) {
            return Integer.MAX_VALUE;
        }
        return NeoForgePermissions.profileLimit(player);
    }
}
