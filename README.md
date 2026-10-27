<p align="center"><img src="mod/src/main/resources/smputilities_logo.png" alt="SMPutilities" width="256"></p>

# SMPutilities

One management mod for SMP and roleplay servers: staff panel, chat, moderation, vanish, inventories (viewing,
backups and profiles), RP nicknames, TTS with speech bubbles, portal limits, dimension access control,
quality-of-life commands, staff pranks and mod sync.

Made by [Arthou](https://discord.com/users/990003604725321808). All rights reserved (see [LICENSE](LICENSE)).

*Português: [README.pt-BR.md](README.pt-BR.md).*

## Supported versions

One jar per game version and loader (`SMPutilities-<version>-<loader>.jar`), all from the same code:

| Minecraft | Fabric | Forge | NeoForge |
| --- | --- | --- | --- |
| 1.18.2 · 1.19.2 · 1.19.4 · 1.20.1 | ✔ | ✔ | — (does not exist; on NeoForge 1.20.1 use the Forge 1.20.1 jar) |
| 1.20.4 · 1.20.6 · 1.21.1 · 1.21.3 · 1.21.4 · 1.21.5 · 1.21.8 · 1.21.10 · 1.21.11 | ✔ | ✔ | ✔ |

Each jar covers its own version line (the 1.21.4 jar only accepts 1.21.4; the 1.18.2 jar, 1.18.x). CI
compiles all 35 targets and boots a dedicated server for each one (smoke test: it loads every class the mixins
change, runs commands and stops). On the 13 Fabric targets a real vanilla client
([mineflayer](https://github.com/PrismarineJS/mineflayer), in `tools/client-smoke`) joins the server, reads the
chunks it receives and checks the anti-xray. On Forge 1.21.11 the test uses a Forge server set up by the official
installer with the jar in `mods/`, because Loom's development environment does not start the game on that
version. None of this replaces testing in game — see [Known limitations](#known-limitations).

## Installation

* **Server:** put the jar for your loader and version in `mods/`. Fabric needs Fabric API.
* **Clients:** optional. Without the mod on the client, players join normally and get chat, messages, chest
  menus, vanish, nicknames in chat and the tab list, mention sounds and so on. The client mod is needed for the
  staff panel, nicknames above heads, speech bubbles, TTS and mod sync. Client and server need the same
  SMPutilities protocol version, not the same loader.
* **Language:** screens and messages follow each player's game language: English, Portuguese (`pt_br`;
  `pt_pt` uses the same text), French, German, Spanish, Russian, Ukrainian and Polish (regional variants such as
  `fr_ca`, `de_at` or `es_mx` use their language's text). Any other language, and any text a language lacks,
  shows English. `language` in the config forces one language for server messages. The mod's screens show
  "Made by arthou" in the bottom right corner.
* **Optional integrations** (the mod works without them): Simple Voice Chat (voice mute), Curios and Cosmetic
  Armor (inventories and backups), LuckPerms (the Forge/NeoForge Permission API; on Fabric through the
  fabric-permissions-api that LuckPerms ships), Aether (dimension and portal names).

## Files

| Path | Contents |
| --- | --- |
| `config/smputilities/server.json` | The whole server configuration (created with defaults; `/su reload` reloads it) |
| `config/smputilities/client.json` | Client preferences (bubbles, TTS, mod sync, private credentials) |
| `config/smputilities/modsync/` | Jars offered by mod sync |
| `<world>/smputilities/players/<uuid>.json` | Per-player data (nickname, homes, notes, settings) |
| `<world>/smputilities/backups/<uuid>.dat` | Inventory backups |
| `<world>/smputilities/profiles/<uuid>.dat` | Inventory profiles |
| `<world>/smputilities/punishments.json` | Punishment history |
| `<world>/smputilities/reports.json` | Reports (`/report`) |
| `<world>/smputilities/logs/audit-YYYY-MM.jsonl` | Administrative log (who, what, to whom, when) |

Everything is keyed by UUID; changing a name or a nickname never loses data.

## Systems

### Staff panel — `/su staff [player]`
Its own interface (translucent dark background, sidebar, green accent, short animations) with nine categories:
**Dashboard** (players, TPS, MSPT, memory, uptime, active bans and mutes, quick chat controls), **Players**
(search by real name or nickname, online and offline; info, actions, notes/statistics and the player's history),
**Chat**, **Inventories** (view/edit, backups with restore, profiles), **Punishments** (bans, mutes, reports,
x-ray alerts, history, staff log), **World** (dimensions and portals), **Utilities** (pranks, announcements,
warps, mod sync), **Server** (performance, lag clean-up, maintenance and whitelist) and **Settings** (the whole
config, editable live). A player's actions are grouped by category (Punishments, Tools, Inventory, Name & chat,
Pranks); an inventory, ender chest, backups or profiles opened from the panel go back to the panel on Esc. Text
that doesn't fit a button is cut with "…" and shown in full on hover. Critical actions (ban, kick, restore,
delete) ask for confirmation. The client only asks; the server checks the permission of every request. There
is also a configurable key ("Open staff panel", unbound by default).

### Chat
Vanilla chat (`<Name> message`, signed) is the default. With `chat.customFormat` on, the QSMP format applies:
`Nick » Message`, `[+] Name`, `[-] Name`, all configurable (`&` colors and `&#RRGGBB`, placeholders `{name}`,
`{realname}`, `{message}`, `{prefix}`). Also: mentions with a sound sent by the server (each player sets it with
`/su mentions on|off|sound <id>|volume <0-1>`), mention highlighting, staff and admin prefixes, global chat lock
(`/su chat lock|unlock`), clear chat, cooldown, spam protection (rate, repeats, CAPS), individual mutes, join and
quit messages that can be turned off globally or per player (`/su silentjoin|silentquit`), `/msg` `/tell` `/w`
`/reply` `/r` with private messages allowed or not during a lock, `/ignore` and social spy for staff. Notices
such as "chat locked by <nick>" and bans only show who did it to staff; players see the version without the name.

**Chat history** (panel → Chat → History, `smputilities.chat.history`): all public chat goes to
`<world>/smputilities/chat/YYYY-MM-DD.log`, written by a separate thread, with nothing kept in RAM. In the panel
you pick the day, read from newest to oldest loading 100 messages at a time, filter, and click a message to see
it in full. `chat.historyDays` (30) sets how many days are kept.

### Moderation
`/su ban|mute|voicemute <player> [duration] [reason]`, `/su unban|unmute|voiceunmute`, `/su kick`,
`/su warn`, `/su history`. Durations: `30m`, `2h`, `7d`, `1mo`, `perm`. Bans also go to the vanilla ban list
(with expiry), so the login check is the game's own and `/pardon` keeps working. Staff can't punish someone with
a higher OP level.

* **Freeze** — `/su freeze <player>` (or the panel button, `smputilities.staff.freeze`): the player can't move,
  break or use blocks, or teleport until unfrozen. They stay frozen if they leave and come back; staff are told
  when a frozen player leaves.
* **Jail** — `/su jail set <name>` sets a jail where you stand; `/su jail put <player> <jail> [time] [reason]`
  jails (online or offline), `/su jail release <player>` releases, `/su jail list|remove`. A jailed player can't
  get further than `jail.radius` blocks from the jail and can't teleport; when the time is up they go back to
  where they were (`smputilities.staff.jail`).
* **Alt accounts** — the mod keeps each player's recent IPs (`alts.maxIps`). Joining from an IP another account
  used warns staff with `smputilities.staff.alts` (in red when that account is banned); `alts.blockBannedAlts`
  refuses the join in that case. `/su alts <player>` and the player page in the panel list the accounts.
* **Chat filter** — `chat.filter.words` (comma separated; `=word` only matches the whole word) with
  `chat.filter.mode` `censor` (replaced by `***`) or `block` (not sent). It catches variants with digits and
  accents (`w0rd`). `chat.filter.blockAdvertising` blocks IPs and Discord invites. Staff see what was caught;
  `smputilities.chat.bypass.filter` skips the filter.

### Server: performance, maintenance and whitelist
The panel's **Server** tab shows TPS, MSPT and, per dimension, loaded chunks, entities, dropped items, XP orbs and
hostile mobs, plus the 15 chunks with the most entities (with the most common kind and a teleport button). There
are buttons to clear items, XP orbs or hostile mobs (named and persistent mobs stay), with
`smputilities.staff.lag`.

**Maintenance** — `/su maintenance on [message]|off` or the panel switch (`smputilities.admin.maintenance`): only
players with `smputilities.maintenance.bypass` (OP 2 by default) can join, and online players without it are
kicked (`maintenance.kickOnline`). At login the OP level, `permissions.grants` and what the permission plugin
said at the player's last login count (LuckPerms is only asked once the player is in). The same tab turns the
vanilla whitelist on and off and adds or removes names (`smputilities.staff.whitelist`), through the game's own
commands.

### Discord
Two optional webhooks (`discord.webhookUrl` and `discord.staffWebhookUrl`; only
`https://discord.com/api/webhooks/…` addresses are accepted). The public one gets chat, joins and quits (vanished
players are left out), deaths and server start/stop; the staff one gets staff actions, reports, suspicious x-ray
alerts and alt accounts. Each kind can be turned on and off under `discord.*`. Messages go out from a separate
thread, batched every 2 seconds within Discord's limits, and never ping @everyone or roles.

### Anti-xray (based on DrexHD's AntiXray) and x-ray alerts — `/su xray`
The anti-xray follows the engine of [AntiXray](https://github.com/DrexHD/AntiXray) (MIT), which is itself Paper's
anti-xray. In the chunks sent to players, hidden blocks that touch nothing transparent (air, water, glass…) show
as something else, so x-ray texture packs and hacked clients find nothing. The modes are AntiXray's: `1` replaces
hidden ores with stone, deepslate, netherrack or end stone; `2` fills every buried block with random fake ores;
`3` does the same with one block per layer (the Overworld default). When a block is broken or opened, the real
blocks around it (`updateRadius`) are sent again. The defaults are AntiXray's: the Overworld in mode 3 up to
height 256 and the Nether in mode 1 up to 128, with lava hiding. Everything lives in `antiXray.worlds` (block ids
or `#tags`; a leading `!` removes one from the list). With `antiXray.usePermission`, holders of
`smputilities.antixray.bypass` see the real blocks.

The difference from AntiXray is only how it hooks into the game. AntiXray puts the fake blocks in the world's
own palettes and holds packets back on the connection. Here only the packet changes: the chunk is remembered when
the packet is created, and blocks are hidden when the network thread writes the packet. The saved world is never
changed, and packet order stays the same. The hooks (chunk packet, chunk sending, block breaking, `setBlock`) are
the same from 1.18.2 to 1.21.11; only the packet format changes (from 1.21.5 the array length is no longer in
the packet). In CI every version builds chunk packets of the spawn, hides the blocks and checks the result; on
Fabric, a vanilla client walks into a netherrack room in the Nether and checks that buried ores arrive as
netherrack and exposed ones as themselves.

X-ray alerts count the veins of valuable ores each player finds (`xrayAlerts.thresholds`: diamond, ancient debris
and emerald by default). Staff with `smputilities.xray.alerts` are told about each vein and get a red alert, with a
sound, when someone goes over the limit within `xrayAlerts.windowMinutes`. Alerts have a teleport button, and
`/su xray` and the **X-ray** view under Punishments list the latest 50.

### Block and container log (CoreProtect style) — `/su log`
Every block change made while the server handles a player's action is logged with who, when and where. That
includes breaking, placing, buckets, flint and steel, hoes, shovels and axes. Explosions are logged as
`#explosion`. Changes that keep the block (opening a door, a lever, crops growing) are left out. Chests, barrels,
furnaces, hoppers, shulker boxes and so on are compared when the player closes what they opened: what went in and
what came out is logged item by item. Everything goes to disk, in
`<world>/smputilities/blocklog/<dimension>/<region>/<day>.log`, from a separate thread; nothing is kept in
memory, and days older than `blockLog.days` (30) are deleted.

* `/su log inspect` turns on the inspector: hitting or right-clicking a block shows its history without breaking
  or opening it.
* `/su log lookup u:<players> t:<time> r:<radius|#world> a:<break|place|explode|container>` searches changes (by
  default within 10 blocks of the searcher), 10 per page, with a teleport button.
* `/su log rollback …` undoes what the search finds (needs `t:`): each block goes back to how it was before the
  first change, with chest and sign contents, but only if nobody touched it since. Items taken from chests come
  back and items put in go out. It runs in slices (`blockLog.changesPerTick` per tick, up to
  `blockLog.maxChanges`). `/su log restore …` redoes, and `/su log undo` reverts the last rollback.
* Permissions: `smputilities.staff.blocklog` (inspect and search, OP 2) and `smputilities.staff.rollback` (OP 3).

### Vanish — `/vanish [on|off|<player>]`
Done on the server: a mixin in entity tracking keeps the vanished player from being sent to anyone without
`smputilities.staff.vanish.see` (no position, equipment, sounds or particles), removal from the tab list, silent
joins and quits (staff see them with a `[vanished]` tag), mobs don't target them, item pickup can be blocked, an
indicator in the action bar and HUD, and it lasts across logins. They are also left out of `/list`, the player
count and sample in the server list, mentions and name suggestions, `/msg`, `/seen` and TPA; their death and
advancement messages only reach those who can see them, they don't hold back the night and, in Simple Voice Chat,
only those who can see them hear them.

### Inventories
* `/su inventory <player>`, `/su enderchest <player>` (and `curios`/`cosmetic`): online players are edited live;
  offline editing only touches the item lists in the player's file (position, dimension and the rest stay
  intact). If the player joins while the edit is open, the edited copy is applied to them and the window closes —
  without duplicating or losing items. A backup is made before an offline edit is saved.
* Automatic (join, quit, death, dimension change, periodic) and manual backups:
  `/su inventory backup <player>`, `/su inventory list <player>`, `/su inventory backups <player>`
  (Inventory Rollback Plus's GUI browser), `/su inventory restore <player> <backup> [full|inventory|ender]`.
  Every restore first backs up the current state.
* **Profiles (Inv Profile built in):** the original Quick Change (`/invprofile` or `/su profile quick`) with the
  same behavior, plus named profiles for characters: `/su profile [create|switch|delete|list|nick]`. They store
  inventory, armor, ender chest, XP, health, hunger, effects and Curios/Cosmetic Armor slots (each part
  configurable, location optional). A switch saves the outgoing state before applying the new one and the player
  file remembers which profile is loaded, so a server crash mid-switch is fixed at the next login. Staff:
  `/su profile view|switchfor`, and through the panel.

### Nicknames (based on HiddenNames) — `/hiddennames setName …` and `/su nick <name>`
The system follows [HiddenNames](https://github.com/pitbox46/HiddenNames) (MIT): each player has a colored name and
a **nameplate animation** — `none`, `hidden` (nameplate hidden), `breathe`, `rainbow`, `cycle` or `random` (the
animations were ported from HiddenNames). The commands are HiddenNames' own, except that the name uses `&` colors
instead of JSON (so players without the mod can still join):
* `/hiddennames setName <color> <animation> <name>`, `/hiddennames setName name|color|animation|reset …`,
  and for others `/hiddennames setName <players> …` (`smputilities.nickname.others`).
* `/hiddennames nameplateVisible <players> <true|false>`, `/hiddennames defaultVisible <true|false>`
  (nameplates visible by default), `/hiddennames blocksHideName <true|false>` (blocks hide nameplates).
* `/su nick <name>`, `/su nick clear` and `/su nick set <player> <name>` remain as shortcuts.

As in HiddenNames, the team color replaces the nickname's (`nicknames.teamColorOverride`), and each client can show
its own nameplate in third person and turn the animations off (`nametags.showOwn`, `nametags.animations` in
`client.json`). The name shows in chat, death messages, the tab list (with the team prefix/suffix), `/msg`
(configurable) and above the head; animations, hidden nameplates and nameplates hidden behind blocks are drawn by
the client, so they apply to players with the mod. Optional uniqueness, real name hidden from non-staff,
`/su realnames` shows real names only to you; `/su whois`. Commands accept the real name, the nickname or the UUID,
and always store the UUID. Don't use HiddenNames alongside: SMPutilities replaces it (and imports
`hiddennames/data.json`).

### TTS and speech bubbles — `/su tts`
DuTTS's menu (gender, variant, language). With a voice active, what the player types becomes speech: the server
sends the text to their client, whose voice app (*SMPutilities Voice*, a separate jar embedded in the mod and
started only when needed) speaks into a virtual microphone used by the voice chat or Discord. As in DuTTS, the
message doesn't show in chat; `tts.showInChat` (off by default) shows it as well. Everyone nearby with the mod sees
NEXEL/LeafTalk's speech bubble (same textures and geometry, one bubble per message stacked above the head, nearly
transparent while sneaking) and, while the player types, the animated "..." bubble.

The voice app has its own interface, in Portuguese or English depending on the system: **Overview** (animated
voice meter, number of lines spoken, voices, player, a "Ready to play" checklist and a field to test the voice),
**Microphone** (four steps: create the virtual microphone, send the voice to it, also listen, pick it in the voice
chat) and **Activity** (log). On Linux it creates the `SMPutilities_TTS_Cable` cable and the **SMPutilities TTS
Microphone** microphone (pick that one in the voice chat). Windows and macOS can't create a virtual microphone without
a driver: on Windows install VB-Cable, choose "CABLE Input" as the voice output in the app and "CABLE Output" as the
microphone in the voice chat; on macOS install VB-Cable (the default) or BlackHole 2ch (free, or
`brew install blackhole-2ch`), choose the same device ("VB-Cable" or "BlackHole 2ch") both as the voice output in the
app and as the microphone in the voice chat, and allow Minecraft to use the microphone in System Settings. "Hear it yourself" plays a copy on your headphones on every system. Without the
app, only TTS is unavailable.

### World
* `/su portal limit <nether|end|aether|id> <n|unlimited>`, `/su portal count|list|remove|clear`. Nether portals are
  counted when lit; End portals when the last eye would complete the frame. Destroyed portals are detected (block
  breaking and a periodic check) and free their slot. A permission skips the limit.
* `/su dimension <dimension> on|off`, `/su dimension list`: blocks new travel (portals, `/tp`, optionally
  entities) without unloading anything or touching data or builds. Exception for holders of the permission.

### Quality of life (from TPA)
`/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny` (with clickable buttons), `/tpacancel` (withdraws the requests you
sent), `/tpatoggle` (refuses everyone's requests; staff still can), `/home`, `/sethome`, `/delhome`, `/homes`,
`/setdefaulthome`, `/spawn`, `/setspawn`, `/back`, `/warp`, `/warps`, `/setwarp`, `/delwarp`, `/rtp`, `/tpall`,
`/tplogout`, `/hat`, `/sit`. Warmup cancelled by moving or taking damage, per-command cooldowns, home limit
(config or the numeric permission `smputilities.teleport.home.limit`), commands that can be blocked per dimension.

### SMP: AFK, reports, staff chat, announcements and restarts
Everything works with clients without the mod and each part can be turned off under `features`.
* **AFK — `/afk [reason]`:** also automatic after `afk.autoMinutes` (5) without looking around, walking or talking
  (being carried by water or a minecart doesn't count). Shows `[AFK]` in the tab list, tells everyone (except for
  vanished players), tells whoever `/msg`s an AFK player and, with `afk.ignoreForSleep`, AFK players out of bed
  don't stop the night from being skipped (the sleeping percentage ignores them). Optional kick after
  `afk.kickMinutes` (off by default; `smputilities.afk.bypass.kick` is exempt).
* **Reports — `/report <player> <reason>`:** online staff get the notice with buttons to teleport to where the
  report was made and to close it; staff who join later see how many are open. `/su reports`,
  `/su reports close <id>`, `/su reports tp <id>`, or in the panel (Punishments → Reports, with the same buttons;
  each player's page shows how many open reports they have and whether they are AFK). Stored in
  `<world>/smputilities/reports.json`, with a minimum interval between reports and an audit entry; the reporter is
  told when staff close it.
* **Staff chat — `/sc <message>`** (or `/staffchat`); `/sc` alone toggles the mode where normal messages go to
  staff only.
* **Automatic announcements:** `announcements.messages` (a list with `&` colors and `{online}`) every
  `announcements.intervalMinutes`, in order or random; `https://` links become clickable.
* **Scheduled restart — `/su restart <time> [reason]`**, `/su restart cancel`, `/su restart` (status), and daily
  times in `restart.dailyTimes` (e.g. `["06:00"]`). Warns in chat and with a sound at the `restart.warningSeconds`
  times (15 min, 10, 5, 2, 1 min, 30 s, 10…1 s, also in the action bar), then disconnects everyone with the
  message and stops the server, saving the world. Starting it again is up to the host or the start script, as with
  `/stop`.
* **Welcome:** announcement of each player's first join ("player no. N"), a per-player MOTD on join
  (`welcome.motd`, with `{name}`, `{online}`, `{max}`) and `/rules` (`welcome.rules`). Links are clickable.
* **Info:** `/playtime [player]`, `/seen <player>` (respects vanish) and, on death, only the player sees the
  coordinates and dimension, with a `[/back]` shortcut.

### Staff pranks — `/su troll <action> <player> [value]`
They follow [TrollBoss](https://github.com/minesuchtiiii/troll-boss)'s list (as a reference only: it is GPL-3.0,
so none of its code was copied). In the panel they come in five groups (player actions → Pranks, and the Utilities
tab):
* **Visual:** fake explosion and lightning, harmless creeper (explodes without damage), sound, jumpscare, alien
  abduction, cow stampede, squid rain (invulnerable mobs that vanish on their own), pumpkin head (the helmet goes
  back to the inventory), glow and DUSMP's titles.
* **Effects:** nausea, darkness, dizziness, infection, freeze (stuck in place), no mining, slowness, float, super
  jump.
* **Movement:** launch, push, spin, turn around, random teleports, world border, swap places with another player,
  sky, bury (with Resistance, no suffocation), glass cage and web trap.
* **Chat and kicks:** fake messages/joins/quits, fake op/deop, spam, garbled chat (toggle), troll kick, fake crash
  and fake restart.
* **Damage:** burn, real lightning, explosion (without breaking blocks), hurt (down to half a heart), starve,
  spank, free fall, anvil and doom countdown.

Pranks that place blocks only take air and are undone when they end, when the player leaves and when the server
stops. Damage and kick pranks ask for confirmation in the panel. Each group can be turned off in the config
(`troll.harmful`, `troll.kicks`, `troll.worldChanges`…), and players with `smputilities.troll.bypass` can only be
targeted by themselves. Left out are TrollBoss's pranks that delete or swap the inventory (potato troll, drop
inventory), those that depend on special items (bad apple, bows, AK-47) and the tasteless ones.

### Mod sync (optional, off by default)
Put jars in `config/smputilities/modsync/` and/or list external downloads in `modSync.external`. Clients with the
mod compare them (SHA-512) and see a screen with what is missing, where it comes from and its size; they only
download if the player accepts. Downloads only over HTTPS from allowed hosts or from the server's built-in host
(which serves only those files, only to connected players, with a token), checked before going into `mods/`, never
run; removals go to a backup folder. Private repository credentials stay only in the player's `client.json`. It
says when a restart is needed and can be cancelled.

### Permissions and logs
All nodes (`smputilities.staff.panel`, `smputilities.staff.ban`, `smputilities.staff.inventory.edit`,
`smputilities.staff.vanish`, `smputilities.staff.voice.mute`, `smputilities.staff.realnames`,
`smputilities.chat.manage`, `smputilities.nickname.change`, `smputilities.portal.manage`,
`smputilities.dimension.manage`, … — full list in
[`Perm.java`](core/src/main/java/com/arthou/smputilities/core/perm/Perm.java)) are registered with the Forge/NeoForge
Permission API; on Fabric they are checked through fabric-permissions-api (which LuckPerms installs). With a
permission plugin, it decides; without one the default OP levels apply, adjustable in
`permissions.levelOverrides`, plus per-player grants in `permissions.grants`. Every administrative action goes to
the audit log (also visible in the panel).

### Data from the old mods
On first start SMPutilities imports (read only, never changed) the data of Inv Profile (becomes Quick Change),
Inventory Rollback Plus (backups), TPA (homes, warps, spawn), DUSMP (silent join/quit, voice), DuTTS (voices) and
HiddenNames (names and nameplate animations). The old commands `/invprofile`, `/view`, `/inventoryrollback`,
`/dutts` and `/hiddennames` keep working.

## Deliberately left out

* TPA's DuCurrency compatibility (teleport cost), which depends on an external mod that is not in the base
  repository.

## Known limitations

* Nickname, animation and hidden nameplate above the head only for clients with the mod (the nameplate is drawn by
  the client; HiddenNames has the same limitation). Command suggestions and the game's "social interactions"
  screen still show account names.
* Speech bubbles: Forge has no world render event from 1.21.3, so on Forge 1.21.3–1.21.8 they are drawn by a mixin
  and on Forge 1.21.9+ by a frame graph pass of their own (`AddFramePassEvent`); on the other loaders and
  versions, by the loader's event. Those two Forge paths have not been seen in game yet.
* Portal limits: destination portals generated by the game are not counted; other mods' portals are only limited
  if the mod fires Forge/NeoForge's `PortalSpawnEvent` (on Fabric, Nether portals only). Dimension blocking works
  for any dimension.
* Before 1.19.4 there are no Text Displays: `/sit` uses an invisible area effect cloud. Effects that don't exist in
  the version (darkness before 1.19) are simply skipped by the pranks.
* Backups and profiles keep the format of the version they were made in and are updated by the game's *data
  fixers* when moving up a version; going back to an older version doesn't convert items.
* Curios/Cosmetic Armor: editing only with the player online.
* Mod sync: a jar with the same name that Windows keeps locked is left in
  `config/smputilities/modsync-client/pending/` to be replaced by hand.
* **Tested in CI, not in game**: the 35 jars compile and each server starts, applies the mixins and runs commands
  (on Fabric, with a real vanilla client joining), but the visual part (panel, bubbles, HUD) and play with real
  players need testing on a test server before production.

## Multi-loader and multi-version

```
core/                         plain Java 17 logic (config, permissions, chat, punishments, audit,
                              portals, dimensions, teleport, mod sync), with tests
mod/src/main/java             all the game code, shared by every target
  mc/                         the calls that change between versions (NBT, items, text, ids, effects,
                              teleport, OP permissions, bans, tab list...) live only here
  platform/                   Platform (what each loader provides) and CommonEvents (all events)
  client/gui/                 Gfx and our own widgets: the panel draws the same from 1.18 (PoseStack) to
                              1.21.11 (new GuiGraphics, new input events)
mod/src/{fabric,forge,neoforge}/   only the glue with each loader (events, network, permissions, mixins)
mod/swaps.txt                 Mojang renames applied per version (ResourceLocation→Identifier...)
buildSrc/                     preprocessor: //#if MC>=1.21.5 && FABRIC ... //#else ... //#endif
gradle/target.gradle          the build of one target (Architectury Loom, Mojang mappings)
versions/<version>-<loader>/  exact Minecraft/loader/Fabric API versions (tools/gen_targets.py)
tts-bridge/                   TTS helper app (embedded jar)
tools/                        translations (tools/lang → genlang.py), CI (errors, smoke test), API probes
```

The differences between versions were found by comparing the real signatures of the game's classes in each
version (`tools/probe`, the `probe` workflow), not from memory.

## Building

```sh
./gradlew -Psu.targets=1.21.1-fabric build             # one target; jar in dist/
./gradlew -Psu.targets=1.20.1-forge,1.21.11-neoforge build
./gradlew -Psu.targets=all build                       # all 35 (each downloads its own Minecraft)
./gradlew -Psu.coreOnly=true :core:test                # only the plain logic, without downloading Minecraft
tools/smoke.sh 1.21.1-fabric                           # after the build: starts the target's server (smoke test)
```

Needs JDK 21 (and JDK 17 to run the 1.18.2 to 1.20.4 servers). Translations: English and Portuguese are in
`tools/lang/*.tsv`, the other languages in `tools/lang/locales/<code>.lang`; then run
`python3 tools/genlang.py tools/lang mod/src/main/resources/assets/smputilities/lang`.

## License

SMPutilities is proprietary software: Copyright (c) 2026 arthou, all rights reserved ([LICENSE](LICENSE)). No
permission is granted to use, copy, modify, redistribute or create derivative works without prior written
authorization. Third-party components keep their own licenses, listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) (HiddenNames and AntiXray, MIT; JLayer, LGPL). TrollBoss (GPL-3.0)
was only used as a behavior reference: none of its code was copied, so the mod doesn't have to be GPL. Both files
ship inside every jar.
