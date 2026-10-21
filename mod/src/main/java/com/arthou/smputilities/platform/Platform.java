package com.arthou.smputilities.platform;

import com.arthou.smputilities.core.perm.Perm;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.OptionalInt;

/**
 * What each loader provides to the shared code. The loader's entry point
 * installs its implementation with {@link #install} before anything else runs.
 */
public interface Platform {
    /** "fabric", "forge" or "neoforge". */
    String loader();

    Path configDir();

    /** The game (or server) directory, which holds the mods folder. */
    Path gameDir();

    boolean isModLoaded(String modId);

    /** True on a physical client (singleplayer or a client connected to a server). */
    boolean isClient();

    /**
     * Permission through the loader's permission system (LuckPerms and others
     * plug in there).
     *
     * @param fallback what SMPutilities' own rules (config grants, op levels) decide
     */
    boolean hasPermission(ServerPlayer player, Perm perm, boolean fallback);

    /** Integer permission option (e.g. home limit), when the permission system provides one. */
    OptionalInt intPermission(ServerPlayer player, String node);

    /** Recomputes the display name used by chat and the tab list name. */
    void refreshNames(ServerPlayer player);

    // ---------------------------------------------------------------- network

    /** Whether the player's client announced SMPutilities' channel. */
    boolean canSend(ServerPlayer player);

    /** Sends one message on SMPutilities' channel. Only call when {@link #canSend} is true. */
    void send(ServerPlayer player, byte[] message);

    /** Client side: whether the server has SMPutilities' channel. */
    boolean canSendToServer();

    /** Client side: sends one message to the server. */
    void sendToServer(byte[] message);

    // ------------------------------------------------------------------ access

    final class Holder {
        private static Platform instance;

        private Holder() {
        }
    }

    static void install(Platform platform) {
        Holder.instance = platform;
    }

    static Platform get() {
        Platform platform = Holder.instance;
        if (platform == null) {
            throw new IllegalStateException("SMPutilities platform not installed");
        }
        return platform;
    }
}
