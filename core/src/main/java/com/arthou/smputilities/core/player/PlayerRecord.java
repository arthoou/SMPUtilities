package com.arthou.smputilities.core.player;

import com.arthou.smputilities.core.teleport.Location;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything SMPutilities remembers about one player, keyed by UUID so a
 * name change never loses data. Stored as {@code players/<uuid>.json}.
 */
public final class PlayerRecord {
    public UUID uuid;
    public String lastName = "";
    /** RP nickname with optional &-codes; empty means none. */
    public String nickname = "";
    /** Nameplate animation id (see NameAnimation); empty follows nicknames.defaultNameplateVisible. */
    public String nameAnimation = "";
    public long firstJoin;
    public long lastSeen;
    public long lastLogin;
    public int joinCount;

    public List<StaffNote> notes = new ArrayList<>();
    /** Free RP statistics editable by staff, e.g. "Reputação" -> "12". */
    public Map<String, String> customStats = new LinkedHashMap<>();

    public boolean mentionSound = true;
    public String mentionSoundId = "";
    public float mentionVolume = -1;

    public Set<UUID> ignored = new LinkedHashSet<>();
    public UUID lastMessaged;

    public Map<String, Location> homes = new LinkedHashMap<>();
    public String defaultHome = "";
    public Location back;
    public Location logout;

    public boolean vanished;
    /** Per-player overrides from staff (DUSMP /silentjoin and /silentquit). */
    public boolean silentJoin;
    public boolean silentQuit;
    public boolean realNames;
    public boolean socialSpy;
    /** /sc without a message: chat lines go to the staff channel. */
    public boolean staffChatToggled;
    /** /tpatoggle: refuse incoming /tpa and /tpahere requests. */
    public boolean tpaDisabled;
    /** DUSMP /silenthear: the player hears nobody on voice chat. */
    public boolean voiceDeafened;

    public int ttsMode;
    public String ttsLanguage = "";

    public static final class StaffNote {
        public long time;
        public String author = "";
        public String text = "";

        public StaffNote() {
        }

        public StaffNote(long time, String author, String text) {
            this.time = time;
            this.author = author;
            this.text = text;
        }
    }

    public boolean hasNickname() {
        return nickname != null && !nickname.isBlank();
    }
}
