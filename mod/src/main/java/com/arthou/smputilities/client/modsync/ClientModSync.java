package com.arthou.smputilities.client.modsync;

import com.arthou.smputilities.client.ClientConfig;
import com.arthou.smputilities.core.modsync.ModManifest;
import com.arthou.smputilities.core.modsync.SyncPlan;
import com.arthou.smputilities.core.modsync.SyncPlanner;
import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Client half of mod sync. When a server offers files, the mods folder is
 * compared in the background; if something differs the player is asked
 * before anything is downloaded.
 */
public final class ClientModSync {
    private static volatile ModManifest manifest;
    private static volatile SyncPlan plan;
    private static volatile Set<String> allowedHosts = Set.of();
    private static volatile long maxBytes;
    private static volatile boolean promptPending;

    private ClientModSync() {
    }

    public static Path modsDir() {
        return FMLPaths.GAMEDIR.get().resolve("mods");
    }

    public static Path stateDir() {
        return FMLPaths.CONFIGDIR.get().resolve("smputilities").resolve("modsync-client");
    }

    public static void received(Payloads.Manifest payload) {
        ModManifest raw = payload.manifest();
        String hostBase = raw.hostBase().replace("://@:", "://" + connectedHost() + ":");
        manifest = new ModManifest(hostBase, raw.token(), raw.entries());
        Set<String> hosts = new LinkedHashSet<>(payload.allowedHosts());
        hosts.addAll(ClientConfig.get().modSync.allowedHosts);
        allowedHosts = hosts;
        maxBytes = payload.maxBytes();
        Thread thread = new Thread(() -> {
            try {
                SyncPlan result = SyncPlanner.plan(manifest, modsDir(), ClientConfig.get().modSync.managed, allowedHosts);
                plan = result;
                if (!result.upToDate() && ClientConfig.get().modSync.prompt) {
                    promptPending = true;
                }
            } catch (Exception e) {
                SuLog.warn("Mod sync check failed", e);
            }
        }, "SMPutilities-modsync-plan");
        thread.setDaemon(true);
        thread.start();
    }

    private static String connectedHost() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        if (server == null) {
            return "127.0.0.1";
        }
        return ServerAddress.parseString(server.ip).getHost();
    }

    /** Opens the screen once the player is not in another menu. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (promptPending && minecraft.player != null && minecraft.screen == null && plan != null) {
            promptPending = false;
            minecraft.setScreen(new ModSyncScreen(manifest, plan, allowedHosts, maxBytes));
        }
    }

    public static boolean hasUpdates() {
        return plan != null && !plan.upToDate();
    }

    public static void openScreen() {
        if (manifest != null && plan != null) {
            Minecraft.getInstance().setScreen(new ModSyncScreen(manifest, plan, allowedHosts, maxBytes));
        }
    }

    public static void reset() {
        manifest = null;
        plan = null;
        promptPending = false;
    }
}
