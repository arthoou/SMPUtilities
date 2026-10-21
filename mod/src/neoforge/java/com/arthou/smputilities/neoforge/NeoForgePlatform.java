package com.arthou.smputilities.neoforge;

import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.platform.Platform;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * NeoForge side of {@link Platform}. Every {@link Perm} is registered with
 * NeoForge's permission API; its default resolver applies SMPutilities' own
 * rules, and a permission plugin such as LuckPerms replaces it transparently.
 */
public final class NeoForgePlatform implements Platform {
    private static final Map<Perm, PermissionNode<Boolean>> NODES = new EnumMap<>(Perm.class);
    private static final PermissionNode<Integer> HOME_LIMIT = new PermissionNode<>(SmpUtilities.MOD_ID, "teleport.home.limit",
        PermissionTypes.INTEGER, (player, uuid, context) -> SuServer.config().teleport.maxHomes);
    private static final PermissionNode<Integer> PROFILE_LIMIT = new PermissionNode<>(SmpUtilities.MOD_ID, "profile.limit",
        PermissionTypes.INTEGER, (player, uuid, context) -> SuServer.config().profiles.maxProfilesPerPlayer);
    private static volatile boolean registered;

    static {
        for (Perm perm : Perm.values()) {
            NODES.put(perm, new PermissionNode<>(SmpUtilities.MOD_ID, perm.path(), PermissionTypes.BOOLEAN,
                (player, uuid, context) -> player != null && Perms.fallback(player, perm)));
        }
    }

    static boolean physicalClient() {
        //#if MC>=1.21.9
        return FMLEnvironment.getDist() == Dist.CLIENT;
        //#else
        //$$ return FMLEnvironment.dist == Dist.CLIENT;
        //#endif
    }

    static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(NODES.values().toArray(new PermissionNode<?>[0]));
        event.addNodes(HOME_LIMIT, PROFILE_LIMIT);
        registered = true;
    }

    @Override
    public String loader() {
        return "neoforge";
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get() != null && ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isClient() {
        return physicalClient();
    }

    @Override
    public boolean hasPermission(ServerPlayer player, Perm perm, boolean fallback) {
        if (registered) {
            try {
                return PermissionAPI.getPermission(player, NODES.get(perm));
            } catch (RuntimeException e) {
                SuLog.warn("Permission check for " + perm.node() + " failed, using op levels", e);
            }
        }
        return fallback;
    }

    @Override
    public OptionalInt intPermission(ServerPlayer player, String node) {
        PermissionNode<Integer> permission = node.equals("teleport.home.limit") ? HOME_LIMIT : node.equals("profile.limit") ? PROFILE_LIMIT : null;
        if (permission == null || !registered) {
            return OptionalInt.empty();
        }
        try {
            Integer value = PermissionAPI.getPermission(player, permission);
            return value == null ? OptionalInt.empty() : OptionalInt.of(value);
        } catch (RuntimeException e) {
            return OptionalInt.empty();
        }
    }

    @Override
    public void refreshNames(ServerPlayer player) {
        player.refreshDisplayName();
        player.refreshTabListName();
    }

    @Override
    public CompoundTag persistentData(ServerPlayer player) {
        return player.getPersistentData();
    }

    @Override
    public boolean canSend(ServerPlayer player) {
        return NeoForgeNetwork.canSend(player);
    }

    @Override
    public void send(ServerPlayer player, byte[] message) {
        NeoForgeNetwork.send(player, message);
    }

    @Override
    public boolean canSendToServer() {
        return NeoForgeClient.canSendToServer();
    }

    @Override
    public void sendToServer(byte[] message) {
        NeoForgeClient.sendToServer(message);
    }
}
