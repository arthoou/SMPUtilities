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
    public Afk afk = new Afk();
    public Reports reports = new Reports();
    public StaffChat staffChat = new StaffChat();
    public Announcements announcements = new Announcements();
    public Restart restart = new Restart();
    public Welcome welcome = new Welcome();
    public AntiXray antiXray = new AntiXray();
    public XrayAlerts xrayAlerts = new XrayAlerts();
    public BlockLog blockLog = new BlockLog();
    public Alts alts = new Alts();
    public Discord discord = new Discord();
    public Maintenance maintenance = new Maintenance();
    public Jail jail = new Jail();
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
        public boolean afk = true;
        public boolean reports = true;
        public boolean staffChat = true;
        public boolean announcements = true;
        public boolean restart = true;
        public boolean welcome = true;
        /** Hide ores from x-ray clients (after DrexHD's AntiXray); see antiXray. */
        public boolean antiXray = true;
        /** Tell staff when a player finds valuable ores suspiciously often; see xrayAlerts. */
        public boolean xrayAlerts = true;
        /** Log who breaks and places blocks and moves items in containers, with lookups and rollbacks; see blockLog. */
        public boolean blockLog = true;
        /** Tell staff when a player joins from an IP another account used; see alts. */
        public boolean alts = true;
        /** Blocked words and advertising in chat; see chat.filter. */
        public boolean chatFilter = true;
        /** Freeze and jail players (/su freeze, /su jail). */
        public boolean freezeJail = true;
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
        /** Keep every public chat line on disk for the panel's chat history. */
        public boolean history = true;
        /** Days of chat history kept (0 keeps everything). */
        public int historyDays = 30;
        public Filter filter = new Filter();
        public Spam spam = new Spam();
        public Mentions mentions = new Mentions();
    }

    public static final class Filter {
        /** Words to catch: matched inside words, or whole words only with "=" in front ("=ass"). */
        public List<String> words = new ArrayList<>();
        /** "censor" sends the line with the words as ***, "block" does not send it. */
        public String mode = "censor";
        /** Also block IPv4 addresses and Discord invites. */
        public boolean blockAdvertising = false;
        /** Show staff what was caught. */
        public boolean notifyStaff = true;
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
        /** Like HiddenNames: a team color replaces the nickname's own color. */
        public boolean teamColorOverride = true;
        /** Nameplates of players without an explicit setting are shown (HiddenNames' default_visible). */
        public boolean defaultNameplateVisible = true;
        /** Nameplates are hidden behind blocks (HiddenNames' blocks_hide); clients with SMPutilities only. */
        public boolean blocksHideNameplates = false;
        /** Import names and animations from the HiddenNames mod (world/hiddennames/data.json) on first start. */
        public boolean importHiddenNames = true;
    }

    public static final class Tts {
        public int maxLength = 300;
        /** Also post a TTS player's lines in chat; off, they are only spoken (and shown as bubbles), as in DuTTS. */
        public boolean showInChat = false;
        public boolean bubbles = true;
        /** Show speech bubbles for regular chat too, not only TTS. */
        public boolean bubblesForAllChat = false;
        // NEXEL's bubble defaults.
        public double bubbleSeconds = 8.0;
        public double bubbleFadeSeconds = 0.6;
        public double bubbleRange = 32.0;
        public int bubbleMaxWidth = 180;
        public int bubbleTextColor = 0x000000;
        public int bubbleFillColor = 0xFFFFFF;
        public int bubbleBorderColor = 0x000000;
        public String bubbleStyle = "rounded";
    }

    public static final class Troll {
        public boolean fakeExplosions = true;
        public boolean fakeSounds = true;
        public boolean visualEffects = true;
        public boolean fakeMessages = true;
        public boolean jumpscares = true;
        public boolean funnyEffects = true;
        /** Sky, bury, glass cage and web trap; their blocks are put back afterwards. */
        public boolean worldChanges = true;
        /** Burn, lightning, explosion, hurt, starve, spank, free fall, anvil and doom countdown. */
        public boolean harmful = true;
        /** Troll kick, fake crash and fake restart disconnect the player. */
        public boolean kicks = true;
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
        /** Tells the player where they died (only them). */
        public boolean deathCoordinates = true;
        public boolean playtime = true;
        public boolean seen = true;
    }

    public static final class Afk {
        /** Minutes without looking around, walking or chatting before a player is AFK (0 = only /afk). */
        public int autoMinutes = 5;
        /** Minutes idle before an AFK player is kicked (0 = never); smputilities.afk.bypass.kick is exempt. */
        public int kickMinutes = 0;
        /** Tell everyone when someone goes AFK or comes back. */
        public boolean broadcast = true;
        public boolean showInTabList = true;
        public String tabPrefix = "&7[AFK] ";
        /** AFK players do not hold back the night: the sleep percentage counts only the others. */
        public boolean ignoreForSleep = true;
    }

    public static final class Reports {
        /** Seconds between two reports by the same player. */
        public int cooldownSeconds = 60;
        public int maxReasonLength = 200;
        /** Closed reports kept in reports.json (open ones are always kept). */
        public int keepClosed = 200;
        public String notifySound = "minecraft:block.note_block.pling";
    }

    public static final class StaffChat {
        /** Placeholders: {name} {realname} {message}. */
        public String format = "&8[&cStaff&8] &f{name}&7: &f{message}";
    }

    public static final class Announcements {
        /** Lines sent to everyone in turn ("&"-colors, {online}); links become clickable. Empty = off. */
        public List<String> messages = new ArrayList<>();
        public int intervalMinutes = 10;
        public boolean random = false;
        public String prefix = "&6[Info] &r";
        /** Skip announcements when fewer players are online. */
        public int minPlayers = 1;
    }

    public static final class Restart {
        /** Daily restart times in the server's time zone, e.g. ["06:00"]. Empty = only /su restart. */
        public List<String> dailyTimes = new ArrayList<>();
        /** Seconds left at which everyone is warned. */
        public List<Integer> warningSeconds = new ArrayList<>(List.of(900, 600, 300, 120, 60, 30, 10, 5, 4, 3, 2, 1));
        public String sound = "minecraft:block.note_block.bell";
        /** Shown to players when the server stops; empty = the built-in translated text. */
        public String kickMessage = "";
    }

    public static final class Welcome {
        /** Announce a player's very first join to everyone. */
        public boolean firstJoinBroadcast = true;
        /** {name} {count}; empty = the built-in translated text. */
        public String firstJoinMessage = "";
        /** Lines sent to each player when they join ({name} {online} {max}); links become clickable. */
        public List<String> motd = new ArrayList<>();
        /** Lines shown by /rules; links become clickable. */
        public List<String> rules = new ArrayList<>();
    }

    /**
     * The anti-xray, after DrexHD's AntiXray (MIT) and Paper's anti-xray: the
     * chunks sent to players show hidden blocks that touch no air, water,
     * glass or other see-through block as something else, so x-ray texture
     * packs and cheat clients find nothing. Only the packets change, never the
     * world. Breaking or opening up blocks sends the real blocks around them
     * again (updateRadius).
     */
    public static final class AntiXray {
        /** Players with smputilities.antixray.bypass (op level 2 by default) see the real blocks. */
        public boolean usePermission = false;
        /** Dimension id -> settings; dimensions not listed are sent unchanged. AntiXray's defaults. */
        public Map<String, AntiXray.World> worlds = new LinkedHashMap<>(Map.of(
            "minecraft:overworld", World.overworld(),
            "minecraft:the_nether", World.nether()));

        public static final class World {
            public boolean enabled = true;
            /**
             * 1 = hide: hidden blocks show as stone, deepslate, netherrack or end stone.
             * 2 = obfuscate: hidden and replacement blocks all show as random hidden blocks.
             * 3 = obfuscate, with one random block per layer (less traffic than 2).
             */
            public int engineMode = 3;
            /** Only sections below this height change (rounded down to a multiple of 16). */
            public int maxBlockHeight = 256;
            /** Blocks around a broken or uncovered block that are sent for real again. */
            public int updateRadius = 2;
            /** Still lava hides what is behind it too. */
            public boolean lavaObscures = false;
            /** Block ids or #tags to hide; "!" in front takes one out again. Unknown ones are skipped. */
            public List<String> hiddenBlocks = new ArrayList<>();
            /** Engine modes 2 and 3: blocks that are shown as random hidden blocks as well. */
            public List<String> replacementBlocks = new ArrayList<>();

            static World overworld() {
                World world = new World();
                world.hiddenBlocks = new ArrayList<>(List.of("#c:ores", "#forge:ores", "#minecraft:coal_ores", "#minecraft:iron_ores",
                    "#minecraft:copper_ores", "#minecraft:gold_ores", "#minecraft:redstone_ores", "#minecraft:lapis_ores",
                    "#minecraft:diamond_ores", "#minecraft:emerald_ores", "raw_copper_block", "raw_iron_block", "raw_gold_block",
                    "!nether_gold_ore", "!nether_quartz_ore", "!ancient_debris"));
                world.replacementBlocks = new ArrayList<>(List.of("stone", "deepslate", "andesite", "calcite", "diorite", "dirt",
                    "granite", "gravel", "sand", "tuff", "mossy_cobblestone", "obsidian", "clay", "infested_stone",
                    "amethyst_block", "budding_amethyst", "chest"));
                return world;
            }

            static World nether() {
                World world = new World();
                world.engineMode = 1;
                world.maxBlockHeight = 128;
                world.lavaObscures = true;
                world.hiddenBlocks = new ArrayList<>(List.of("ancient_debris", "nether_quartz_ore", "nether_gold_ore", "gold_block", "gilded_blackstone"));
                world.replacementBlocks = new ArrayList<>(List.of("netherrack"));
                return world;
            }
        }
    }

    public static final class XrayAlerts {
        /**
         * Ores to watch (block ids or #tags) -> veins found within windowMinutes
         * after which staff get a warning. Ores next to each other count as one vein.
         */
        public Map<String, Integer> thresholds = new LinkedHashMap<>(Map.of(
            "#minecraft:diamond_ores", 4,
            "ancient_debris", 4,
            "#minecraft:emerald_ores", 3));
        public int windowMinutes = 10;
        /** Also tell staff about every vein of a watched ore, not only past the threshold. */
        public boolean everyVein = true;
        public String sound = "minecraft:block.note_block.bass";
    }

    public static final class BlockLog {
        /** Days of changes kept on disk. */
        public int days = 30;
        /** Log what players put into and take out of containers. */
        public boolean containers = true;
        /** Log blocks destroyed by explosions (as "#explosion"). */
        public boolean explosions = true;
        /** Most block and item changes one rollback or restore may make. */
        public int maxChanges = 100_000;
        /** Changes made per tick while a rollback runs. */
        public int changesPerTick = 500;
    }

    public static final class Alts {
        public boolean notifyStaff = true;
        /** Refuse players who share an IP with a banned account (ban evasion). */
        public boolean blockBannedAlts = false;
        /** IPs remembered per player. */
        public int maxIps = 10;
    }

    public static final class Discord {
        /** Webhook for the public channel: chat, joins and quits, deaths, server start and stop. Empty = off. */
        public String webhookUrl = "";
        /** Webhook for the staff channel: staff actions, reports, x-ray alerts, alts. Empty = off. */
        public String staffWebhookUrl = "";
        /** Name the messages are posted as; empty = the webhook's own name. */
        public String username = "";
        public boolean chat = true;
        public boolean joinQuit = true;
        public boolean deaths = true;
        public boolean serverStatus = true;
        public boolean staffActions = true;
        public boolean reports = true;
        public boolean xrayAlerts = true;
        public boolean altAlerts = true;
    }

    public static final class Maintenance {
        /** Only players with smputilities.maintenance.bypass can join. */
        public boolean enabled = false;
        public String message = "&cThe server is under maintenance. Come back soon!";
        /** Kick players without the bypass when maintenance starts. */
        public boolean kickOnline = true;
    }

    public static final class Jail {
        /** Jail name -> location, set with /su jail set <name>. */
        public Map<String, com.arthou.smputilities.core.teleport.Location> jails = new LinkedHashMap<>();
        /** Jailed players are brought back when they walk further than this from the jail. */
        public int radius = 6;
    }

    public static final class Permissions {
        /** Node -> required operator level (0-4) when no permission plugin answers. */
        public Map<String, Integer> levelOverrides = new LinkedHashMap<>();
        /** Node -> players (UUID or name) granted the node regardless of op level. */
        public Map<String, List<String>> grants = new LinkedHashMap<>();
    }
}
