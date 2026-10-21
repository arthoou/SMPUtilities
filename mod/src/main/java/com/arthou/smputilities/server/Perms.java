package com.arthou.smputilities.server;

import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.perm.PermissionRules;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.platform.Platform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.OptionalInt;
import java.util.function.Predicate;

/**
 * Permission checks. Players go through the loader's permission system (so
 * LuckPerms and similar plugins can decide), with SMPutilities' own rules
 * (config grants, op levels) as the default; everything else - console,
 * command blocks, functions - falls back to the source's op level.
 */
public final class Perms {
    private Perms() {
    }

    public static boolean has(ServerPlayer player, Perm perm) {
        return PermCache.get(player.getUUID(), perm, () -> Platform.get().hasPermission(player, perm, fallback(player, perm)));
    }

    public static boolean has(CommandSourceStack source, Perm perm) {
        ServerPlayer player = Mc.player(source);
        if (player != null) {
            return has(player, perm);
        }
        return Mc.hasOpLevel(source, PermissionRules.requiredLevel(SuServer.config(), perm));
    }

    public static Predicate<CommandSourceStack> require(Perm perm) {
        return source -> has(source, perm);
    }

    /** SMPutilities' own answer: config grants and op levels. */
    public static boolean fallback(ServerPlayer player, Perm perm) {
        return PermissionRules.fallback(SuServer.config(), perm, player.getUUID(), Mc.name(player), opLevel(player));
    }

    /** Op level the vanilla server gives this player (0-4). */
    public static int opLevel(ServerPlayer player) {
        return Mc.opLevel(player);
    }

    public static int homeLimit(ServerPlayer player) {
        if (has(player, Perm.HOME_UNLIMITED)) {
            return Integer.MAX_VALUE;
        }
        OptionalInt value = Platform.get().intPermission(player, "teleport.home.limit");
        return value.orElse(SuServer.config().teleport.maxHomes);
    }

    public static int profileLimit(ServerPlayer player) {
        if (has(player, Perm.PROFILE_BYPASS_LIMIT)) {
            return Integer.MAX_VALUE;
        }
        OptionalInt value = Platform.get().intPermission(player, "profile.limit");
        return value.orElse(SuServer.config().profiles.maxProfilesPerPlayer);
    }
}
