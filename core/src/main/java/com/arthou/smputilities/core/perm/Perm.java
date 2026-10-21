package com.arthou.smputilities.core.perm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every permission node SMPutilities checks, with the operator level that
 * grants it when no permission plugin answers. Checks always run on the
 * server; the client only uses the synced result to hide buttons.
 */
public enum Perm {
    STAFF_PANEL("smputilities.staff.panel", 2),
    STAFF_TELEPORT("smputilities.staff.teleport", 2),
    STAFF_SPECTATE("smputilities.staff.spectate", 2),
    STAFF_BAN("smputilities.staff.ban", 3),
    STAFF_KICK("smputilities.staff.kick", 2),
    STAFF_MUTE("smputilities.staff.mute", 2),
    STAFF_WARN("smputilities.staff.warn", 2),
    STAFF_VOICE_MUTE("smputilities.staff.voice.mute", 2),
    STAFF_HISTORY("smputilities.staff.history", 2),
    STAFF_INVENTORY("smputilities.staff.inventory", 2),
    STAFF_INVENTORY_EDIT("smputilities.staff.inventory.edit", 3),
    STAFF_INVENTORY_RESTORE("smputilities.staff.inventory.restore", 3),
    STAFF_PROFILES("smputilities.staff.profiles", 3),
    STAFF_VANISH("smputilities.staff.vanish", 2),
    STAFF_VANISH_OTHERS("smputilities.staff.vanish.others", 3),
    STAFF_VANISH_SEE("smputilities.staff.vanish.see", 2),
    STAFF_REALNAMES("smputilities.staff.realnames", 2),
    STAFF_STATS_EDIT("smputilities.staff.stats.edit", 3),
    STAFF_GAMEMODE("smputilities.staff.gamemode", 2),
    STAFF_TROLL("smputilities.staff.troll", 4),
    STAFF_LOGS("smputilities.staff.logs", 3),
    STAFF_ANNOUNCE("smputilities.staff.announce", 3),
    STAFF_TPALL("smputilities.staff.tpall", 3),

    ADMIN_SETTINGS("smputilities.admin.settings", 4),
    ADMIN_RELOAD("smputilities.admin.reload", 4),

    CHAT_MANAGE("smputilities.chat.manage", 3),
    CHAT_BYPASS_LOCK("smputilities.chat.bypass.lock", 2),
    CHAT_BYPASS_COOLDOWN("smputilities.chat.bypass.cooldown", 2),
    CHAT_COLOR("smputilities.chat.color", 2),
    CHAT_MSG("smputilities.chat.msg", 0),

    NICKNAME_CHANGE("smputilities.nickname.change", 0),
    NICKNAME_COLOR("smputilities.nickname.color", 0),
    NICKNAME_OTHERS("smputilities.nickname.others", 2),

    // Level 2 matches the standalone Inv Profile mod (/invprofile needed op level 2).
    PROFILE_USE("smputilities.profile.use", 2),
    PROFILE_BYPASS_LIMIT("smputilities.profile.bypass.limit", 3),

    TTS_USE("smputilities.tts.use", 0),

    PORTAL_MANAGE("smputilities.portal.manage", 3),
    PORTAL_BYPASS("smputilities.portal.bypass", 3),
    DIMENSION_MANAGE("smputilities.dimension.manage", 3),
    DIMENSION_BYPASS("smputilities.dimension.bypass", 3),

    TPA("smputilities.teleport.tpa", 0),
    HOME("smputilities.teleport.home", 0),
    HOME_UNLIMITED("smputilities.teleport.home.unlimited", 3),
    SPAWN("smputilities.teleport.spawn", 0),
    SET_SPAWN("smputilities.teleport.setspawn", 3),
    BACK("smputilities.teleport.back", 0),
    WARP("smputilities.teleport.warp", 0),
    WARP_MANAGE("smputilities.teleport.warp.manage", 3),
    RTP("smputilities.teleport.rtp", 0),
    TELEPORT_BYPASS_COOLDOWN("smputilities.teleport.bypass.cooldown", 2),
    TELEPORT_BYPASS_WARMUP("smputilities.teleport.bypass.warmup", 2),
    TELEPORT_BYPASS_DIMENSION("smputilities.teleport.bypass.dimension", 3),

    HAT("smputilities.qol.hat", 0),
    SIT("smputilities.qol.sit", 0),
    IGNORE("smputilities.qol.ignore", 0),

    MODSYNC_MANAGE("smputilities.modsync.manage", 4);

    private static final Map<String, Perm> BY_NODE;

    static {
        Map<String, Perm> map = new LinkedHashMap<>();
        for (Perm perm : values()) {
            map.put(perm.node, perm);
        }
        BY_NODE = Collections.unmodifiableMap(map);
    }

    private final String node;
    private final int defaultLevel;

    Perm(String node, int defaultLevel) {
        this.node = node;
        this.defaultLevel = defaultLevel;
    }

    public String node() {
        return node;
    }

    public int defaultLevel() {
        return defaultLevel;
    }

    /** Node path without the "smputilities." namespace, as loader permission APIs register it. */
    public String path() {
        return node.substring("smputilities.".length());
    }

    public static Perm byNode(String node) {
        return BY_NODE.get(node);
    }
}
