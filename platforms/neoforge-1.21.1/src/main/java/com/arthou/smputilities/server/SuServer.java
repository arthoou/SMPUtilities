package com.arthou.smputilities.server;

import com.arthou.smputilities.core.audit.AuditLog;
import com.arthou.smputilities.core.config.ConfigManager;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.player.PlayerStore;
import com.arthou.smputilities.core.portal.PortalRegistry;
import com.arthou.smputilities.core.punish.PunishmentStore;
import com.arthou.smputilities.core.teleport.TeleportStore;
import com.arthou.smputilities.core.util.SuLog;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Everything that lives as long as one running server: config, stores and the
 * audit log. Created when the server starts and dropped when it stops, so an
 * integrated server opened twice in one session starts clean.
 */
public final class SuServer {
    private static volatile SuServer instance;
    /** Config survives between integrated-server sessions, so it is loaded once. */
    private static final ConfigManager CONFIG = new ConfigManager(FMLPaths.CONFIGDIR.get().resolve("smputilities").resolve("server.json"));
    private static boolean configLoaded;

    private final MinecraftServer server;
    private final Path dataDir;
    private final PlayerStore players;
    private final PunishmentStore punishments;
    private final TeleportStore teleports;
    private final PortalRegistry portals;
    private final AuditLog audit;

    private SuServer(MinecraftServer server) {
        this.server = server;
        this.dataDir = server.getWorldPath(LevelResource.ROOT).resolve("smputilities");
        this.players = new PlayerStore(dataDir.resolve("players"));
        this.punishments = new PunishmentStore(dataDir.resolve("punishments.json"));
        this.teleports = new TeleportStore(dataDir.resolve("teleport.json"));
        this.portals = new PortalRegistry(dataDir.resolve("portals.json"));
        this.audit = new AuditLog(dataDir.resolve("logs"));
    }

    public static synchronized SuServer start(MinecraftServer server) {
        config();
        SuServer created = new SuServer(server);
        created.players.loadAll();
        created.punishments.load();
        created.teleports.load();
        created.portals.load();
        created.audit.prune(CONFIG.get().staff.auditRetentionDays);
        instance = created;
        SuLog.info("SMPutilities data folder: " + created.dataDir);
        return created;
    }

    public static synchronized void stop() {
        SuServer current = instance;
        if (current != null) {
            current.players.flush();
            current.punishments.save();
            current.teleports.save();
            current.portals.save();
            current.audit.close();
        }
        instance = null;
    }

    /** @return the running server context, or null between sessions */
    public static SuServer get() {
        return instance;
    }

    public static ConfigManager configManager() {
        config();
        return CONFIG;
    }

    public static SuConfig config() {
        synchronized (SuServer.class) {
            if (!configLoaded) {
                configLoaded = true;
                CONFIG.load();
            }
        }
        return CONFIG.get();
    }

    public MinecraftServer server() {
        return server;
    }

    public Path dataDir() {
        return dataDir;
    }

    public PlayerStore players() {
        return players;
    }

    public PunishmentStore punishments() {
        return punishments;
    }

    public TeleportStore teleports() {
        return teleports;
    }

    public PortalRegistry portals() {
        return portals;
    }

    public AuditLog audit() {
        return audit;
    }

    /** Records an administrative action with the acting player (null for console). */
    public void log(ServerPlayer actor, String action, UUID target, String targetName, String details) {
        audit.record(actor == null ? null : actor.getUUID(), actor == null ? "Console" : actor.getGameProfile().getName(),
            action, target, targetName, details);
    }
}
