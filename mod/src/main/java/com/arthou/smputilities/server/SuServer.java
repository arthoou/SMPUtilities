package com.arthou.smputilities.server;

import com.arthou.smputilities.core.audit.AuditLog;
import com.arthou.smputilities.core.blocklog.BlockLog;
import com.arthou.smputilities.core.chat.ChatHistory;
import com.arthou.smputilities.core.config.ConfigManager;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.player.PlayerStore;
import com.arthou.smputilities.core.portal.PortalRegistry;
import com.arthou.smputilities.core.punish.PunishmentStore;
import com.arthou.smputilities.core.report.ReportStore;
import com.arthou.smputilities.core.teleport.TeleportStore;
import com.arthou.smputilities.core.util.SuLog;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.platform.Platform;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Everything that lives as long as one running server: config, stores and the
 * audit log. Created when the server starts and dropped when it stops, so an
 * integrated server opened twice in one session starts clean.
 */
public final class SuServer {
    private static volatile SuServer instance;
    /** Config survives between integrated-server sessions, so it is loaded once. */
    private static volatile ConfigManager config;

    private final MinecraftServer server;
    private final Path dataDir;
    private final PlayerStore players;
    private final PunishmentStore punishments;
    private final TeleportStore teleports;
    private final PortalRegistry portals;
    private final ReportStore reports;
    private final AuditLog audit;
    private final ChatHistory chatHistory;
    private final BlockLog blockLog;

    private SuServer(MinecraftServer server) {
        this.server = server;
        this.dataDir = server.getWorldPath(LevelResource.ROOT).resolve("smputilities");
        this.players = new PlayerStore(dataDir.resolve("players"));
        this.punishments = new PunishmentStore(dataDir.resolve("punishments.json"));
        this.teleports = new TeleportStore(dataDir.resolve("teleport.json"));
        this.portals = new PortalRegistry(dataDir.resolve("portals.json"));
        this.reports = new ReportStore(dataDir.resolve("reports.json"));
        this.audit = new AuditLog(dataDir.resolve("logs"));
        this.chatHistory = new ChatHistory(dataDir.resolve("chat"), ZoneId.systemDefault());
        this.blockLog = new BlockLog(dataDir.resolve("blocklog"), ZoneId.systemDefault());
    }

    public static synchronized SuServer start(MinecraftServer server) {
        config();
        SuServer created = new SuServer(server);
        created.players.loadAll();
        created.punishments.load();
        created.teleports.load();
        created.portals.load();
        created.reports.load();
        created.audit.prune(config().staff.auditRetentionDays);
        created.chatHistory.prune(config().chat.historyDays);
        created.blockLog.prune(config().blockLog.days);
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
            current.reports.save();
            current.audit.close();
            current.chatHistory.close();
            current.blockLog.close();
        }
        instance = null;
    }

    /** @return the running server context, or null between sessions */
    public static SuServer get() {
        return instance;
    }

    public static ConfigManager configManager() {
        config();
        return config;
    }

    /** The server config, loaded from config/smputilities/server.json on first use. */
    public static SuConfig config() {
        ConfigManager manager = config;
        if (manager == null) {
            synchronized (SuServer.class) {
                if (config == null) {
                    ConfigManager created = new ConfigManager(Platform.get().configDir().resolve("smputilities").resolve("server.json"));
                    created.load();
                    config = created;
                }
                manager = config;
            }
        }
        return manager.get();
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

    public ReportStore reports() {
        return reports;
    }

    public AuditLog audit() {
        return audit;
    }

    public ChatHistory chatHistory() {
        return chatHistory;
    }

    public BlockLog blockLog() {
        return blockLog;
    }

    /** Records an administrative action with the acting player (null for console). */
    public void log(ServerPlayer actor, String action, UUID target, String targetName, String details) {
        audit.record(actor == null ? null : actor.getUUID(), actor == null ? "Console" : Mc.name(actor),
            action, target, targetName, details);
    }
}
