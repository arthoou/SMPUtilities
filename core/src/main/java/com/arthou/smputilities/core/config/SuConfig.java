package com.arthou.smputilities.core.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server configuration, stored as {@code config/smputilities/server.json}.
 *
 * <p>Every field has a safe default, so a missing or partial file never breaks
 * startup: unknown keys are ignored and missing ones keep the value below.
 * Fields are public and mutable on purpose; {@link ConfigPaths} edits them by
 * dotted path from the staff panel.
 */
public final class SuConfig {
    public int configVersion = 1;

    /** "auto" uses each player's client language; otherwise "en_us" or "pt_br". */
    public String language = "auto";

    public Features features = new Features();
    public Chat chat = new Chat();
    public Staff staff = new Staff();
    public Vanish vanish = new Vanish();
    public Inventory inventory = new Inventory();
    public Profiles profiles = new Profiles();
    public Nicknames nicknames = new Nicknames();
    public Tts tts = new Tts();
    public Troll troll = new Troll();
    public ModSync modSync = new ModSync();
    public Portals portals = new Portals();
    public Dimensions dimensions = new Dimensions();
    public Teleport teleport = new Teleport();
    public Qol qol = new Qol();
    public Permissions permissions = new Permissions();

    /** Master switches. A disabled system registers no listeners or commands. */
    public static final class Features {
        public boolean chat = true;
        public boolean staffPanel = true;
        public boolean vanish = true;
        public boolean inventory = true;
        public boolean profiles = true;
        public boolean nicknames = true;
        public boolean tts = true;
        public boolean troll = true;
        public boolean modSync = false;
        public boolean portals = true;
        public boolean dimensions = true;
        public boolean teleport = true;
        public boolean qol = true;
        public boolean voiceChat = true;
    }

    public static final class Chat {
        /** False keeps the untouched vanilla chat ({@code <Player> message}). */
        public boolean customFormat = false;
        /** Placeholders: {prefix} {name} {nick} {realname} {message} {role}. */
        public String messageFormat = "&e{name}&8 » &f{message}";
        public String joinFormat = "&7[&a+&7] &f{name}";
        public String quitFormat = "&7[&c-&7] &f{name}";
        public String staffPrefix = "&c[Staff] ";
        public String adminPrefix = "&4[Admin] ";
        public String serverPrefix = "&6[Server] ";
        /** Prefix staff/admin names in the custom format. */
        public boolean roleColors = false;
        public boolean joinMessages = true;
        public boolean quitMessages = true;
        /** Lets players use &-color codes in their own messages (needs smputilities.chat.color). */
        public boolean playerColors = false;
        public boolean globalChatEnabled = true;
        public boolean allowPrivateMessagesWhileLocked = true;
        public boolean privateMessagesEnabled = true;
        public boolean privateMessagesUseNicknames = true;
        public String privateFormatOutgoing = "&7[&dyou &8→ &d{target}&7] &f{message}";
        public String privateFormatIncoming = "&7[&d{sender} &8→ &dyou&7] &f{message}";
        public double cooldownSeconds = 0.0;
        public Spam spam = new Spam();
        public Mentions mentions = new Mentions();
    }

    public static final class Spam {
        public boolean enabled = true;
        /** Messages allowed inside {@link #windowSeconds}. */
        public int maxMessages = 6;
        public double windowSeconds = 8.0;
        /** Blocks the same text repeated within this many seconds. */
        public double duplicateSeconds = 15.0;
        /** Uppercase ratio above which the message is lowercased (0 disables). */
        public double capsRatio = 0.0;
    }

    public static final class Mentions {
        public boolean enabled = true;
        public String sound = "minecraft:entity.experience_orb.pickup";
        public float volume = 0.6F;
        public float pitch = 1.6F;
        /** Highlight mentioned names in the broadcast line (custom format only). */
        public boolean highlight = true;
        public String highlightColor = "&b";
        /** Also mention by "@name" with the @ sign only. */
        public boolean requireAtSign = false;
    }

    public static final class Staff {
        /** Ask for confirmation in the panel before bans, kicks, deletions and restores. */
        public boolean confirmCriticalActions = true;
        /** Seconds between automatic dashboard refreshes while the panel is open. */
        public int refreshSeconds = 2;
        public boolean broadcastPunishments = true;
        public String defaultBanReason = "Banned by staff.";
        public String defaultKickReason = "Kicked by staff.";
        public String defaultMuteReason = "Muted by staff.";
        public int auditRetentionDays = 90;
    }

    public static final class Vanish {
        public boolean hideFromTabList = true;
        public boolean silentJoinQuit = true;
        /** Broadcast a fake quit/join line when someone toggles vanish. */
        public boolean fakeMessagesOnToggle = false;
        public boolean preventMobTargeting = true;
        public boolean preventItemPickup = false;
        public boolean actionBarIndicator = true;
        /** Keep vanish on across reconnects. */
        public boolean persistent = true;
    }

    public static final class Inventory {
        public boolean backupOnJoin = true;
        public boolean backupOnQuit = true;
        public boolean backupOnDeath = true;
        public boolean backupOnDimensionChange = true;
        /** Minutes between automatic backups of online players (0 disables). */
        public int periodicBackupMinutes = 0;
        public int maxBackupsPerType = 10;
        public int maxDeathBackups = 50;
        public int maxManualBackups = 50;
        public boolean backupBeforeRestore = true;
        public boolean allowOfflineEdit = true;
    }

    public static final class Profiles {
        public int maxProfilesPerPlayer = 5;
        public boolean storeInventory = true;
        public boolean storeEnderChest = true;
        public boolean storeExperience = true;
        public boolean storeHealth = true;
        public boolean storeHunger = true;
        public boolean storeEffects = true;
        public boolean storeLocation = false;
        /** Includes Curios and Cosmetic Armor slots when those mods are present. */
        public boolean storeCompatSlots = true;
        /** Seconds between two switches by the same player. */
        public int switchCooldownSeconds = 5;
        /** Use the profile name as RP nickname after switching. */
        public boolean switchNickname = false;
        /** Import data from the standalone Inv Profile mod on first start. */
        public boolean importInvProfile = true;
    }

    public static final class Nicknames {
        public boolean showInChat = true;
        public boolean showInTabList = true;
        public boolean showAboveHead = true;
        /** Hide the real account name from players without smputilities.staff.realnames. */
        public boolean hideRealNames = true;
        public boolean uniqueNicknames = true;
        public boolean allowColors = true;
        public int maxLength = 24;
        public int minLength = 2;
        /** Regex applied to the nickname without color codes. */
        public String allowedPattern = "[\\p{L}\\p{N} _.'\\-]+";
        /** Format used in the tab list when hideRealNames is off. */
        public String tabFormat = "{nick}";
    }

    public static final class Tts {
        public int maxLength = 300;
        /** Also print the TTS line in chat (with a ♪ marker). */
        public boolean echoToChat = true;
        public String chatMarker = "&b♪ ";
        public boolean bubbles = true;
        /** Show speech bubbles for regular chat too, not only TTS. */
        public boolean bubblesForAllChat = false;
        public double bubbleSeconds = 6.0;
        public double bubbleFadeSeconds = 0.75;
        public double bubbleRange = 32.0;
        public int bubbleMaxWidth = 180;
        public int bubbleTextColor = 0x1A1A1A;
        public int bubbleFillColor = 0xFFFFFF;
        public int bubbleBorderColor = 0x101010;
        public String bubbleStyle = "rounded";
    }

    public static final class Troll {
        public boolean fakeExplosions = true;
        public boolean fakeSounds = true;
        public boolean visualEffects = true;
        public boolean fakeMessages = true;
        public boolean jumpscares = true;
        public boolean funnyEffects = true;
        public int maxEffectSeconds = 15;
    }

    public static final class ModSync {
        /** Host the files in config/smputilities/modsync on this port (0 disables hosting). */
        public int hostPort = 0;
        /** Public address clients should use to reach the host, e.g. "play.example.com". Empty = connection address. */
        public String publicHost = "";
        public long maxFileBytes = 256L * 1024 * 1024;
        /** Extra files with external URLs (Modrinth, CurseForge CDN, private repositories). */
        public List<ModSync.External> external = new ArrayList<>();
        /** Hosts clients may download from besides the built-in host. */
        public List<String> allowedHosts = new ArrayList<>(List.of("cdn.modrinth.com", "mediafilez.forgecdn.net", "edge.forgecdn.net", "github.com", "objects.githubusercontent.com"));
        public boolean kickIfOutdated = false;

        public static final class External {
            public String fileName = "";
            public String url = "";
            public String sha512 = "";
            public long size = 0;
            /** Optional credential name looked up in the client's own config; never sent by the server. */
            public String credential = "";
            public boolean required = true;
        }
    }

    public static final class Portals {
        /** Limit per dimension key ("nether", "end", "aether" or a full id); -1 = unlimited. */
        public Map<String, Integer> limits = new LinkedHashMap<>();
        /** Portal block id -> limit key, for modded portals that fire the portal spawn event. */
        public Map<String, String> customPortalBlocks = new LinkedHashMap<>(Map.of("aether:aether_portal", "aether"));
        public int validationIntervalMinutes = 5;
        public String limitMessage = "&cThe portal limit for {dimension} was reached ({count}/{limit}).";
    }

    public static final class Dimensions {
        /** Dimension ids whose access is blocked. Data and builds are kept. */
        public List<String> blocked = new ArrayList<>();
        public String blockedMessage = "&cTravel to {dimension} is disabled right now.";
        /** Short names accepted by /su dimension. */
        public Map<String, String> aliases = new LinkedHashMap<>(Map.of(
            "overworld", "minecraft:overworld",
            "nether", "minecraft:the_nether",
            "end", "minecraft:the_end",
            "aether", "aether:the_aether"));
        /** Also stop non-player entities (items, mobs) from crossing into blocked dimensions. */
        public boolean blockEntities = true;
    }

    public static final class Teleport {
        public int maxHomes = 3;
        public int warmupSeconds = 3;
        public boolean cancelOnDamage = true;
        public boolean cancelOnMove = true;
        public int requestTimeoutSeconds = 60;
        public int tpaCooldownSeconds = 10;
        public int homeCooldownSeconds = 5;
        public int spawnCooldownSeconds = 5;
        public int backCooldownSeconds = 10;
        public int warpCooldownSeconds = 5;
        public int rtpCooldownSeconds = 300;
        public int rtpRadius = 3000;
        public boolean backOnDeath = true;
        public boolean respawnAtSpawn = false;
        /** Command -> dimension ids where it cannot be used, e.g. "home": ["minecraft:the_end"]. */
        public Map<String, List<String>> blockedInDimensions = new LinkedHashMap<>();
        /** Import homes, warps and spawn from the standalone TPA mod on first start. */
        public boolean importTpa = true;
    }

    public static final class Qol {
        public boolean hat = true;
        public boolean sit = true;
        public boolean msg = true;
        public boolean ignore = true;
    }

    public static final class Permissions {
        /** Node -> required operator level (0-4) when no permission plugin answers. */
        public Map<String, Integer> levelOverrides = new LinkedHashMap<>();
        /** Node -> players (UUID or name) granted the node regardless of op level. */
        public Map<String, List<String>> grants = new LinkedHashMap<>();
    }
}
