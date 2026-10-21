package com.arthou.smputilities.fabric;

import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Fabric side of {@link Platform}. Permissions go through the
 * fabric-permissions-api when a permission mod (such as LuckPerms) ships
 * it; it is called reflectively, so SMPutilities does not require it.
 */
public final class FabricPlatform implements Platform {
    private static final String PERMISSIONS_MOD = "fabric-permissions-api-v0";
    private static MethodHandle check;
    private static MethodHandle option;
    private static boolean looked;

    private static synchronized void lookUp() {
        if (looked) {
            return;
        }
        looked = true;
        if (!FabricLoader.getInstance().isModLoaded(PERMISSIONS_MOD)) {
            return;
        }
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        try {
            Class<?> permissions = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            check = lookup.findStatic(permissions, "check", MethodType.methodType(boolean.class, Entity.class, String.class, boolean.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            SuLog.warn("fabric-permissions-api found but unusable, using op levels", e);
        }
        try {
            Class<?> options = Class.forName("me.lucko.fabric.api.permissions.v0.Options");
            option = lookup.findStatic(options, "get", MethodType.methodType(Optional.class, Entity.class, String.class));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Options arrived in a later version of the API; integer limits then come from the config.
        }
    }

    @Override
    public String loader() {
        return "fabric";
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public boolean hasPermission(ServerPlayer player, Perm perm, boolean fallback) {
        lookUp();
        if (check == null) {
            return fallback;
        }
        try {
            return (boolean) check.invokeExact((Entity) player, perm.node(), fallback);
        } catch (Throwable e) {
            SuLog.warn("Permission check for " + perm.node() + " failed, using op levels", e);
            return fallback;
        }
    }

    @Override
    public OptionalInt intPermission(ServerPlayer player, String node) {
        lookUp();
        if (option == null) {
            return OptionalInt.empty();
        }
        try {
            Optional<?> value = (Optional<?>) option.invokeExact((Entity) player, SmpUtilities.MOD_ID + "." + node);
            return value.isPresent() ? OptionalInt.of(Integer.parseInt(String.valueOf(value.get()).trim())) : OptionalInt.empty();
        } catch (Throwable e) {
            return OptionalInt.empty();
        }
    }

    @Override
    public void refreshNames(ServerPlayer player) {
        // Fabric does not cache display names (see the Player mixin); only the tab list needs a push.
        Mc.broadcastTabListName(player);
    }

    @Override
    public boolean canSend(ServerPlayer player) {
        return FabricNetwork.canSend(player);
    }

    @Override
    public void send(ServerPlayer player, byte[] message) {
        FabricNetwork.send(player, message);
    }

    @Override
    public boolean canSendToServer() {
        return FabricClient.canSendToServer();
    }

    @Override
    public void sendToServer(byte[] message) {
        FabricClient.sendToServer(message);
    }
}
