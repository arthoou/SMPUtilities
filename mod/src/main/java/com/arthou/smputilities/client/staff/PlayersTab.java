package com.arthou.smputilities.client.staff;

import com.arthou.smputilities.client.gui.Gfx;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.client.ClientState;
import com.arthou.smputilities.client.gui.Dialog;
import com.arthou.smputilities.client.gui.Heads;
import com.arthou.smputilities.client.gui.SuButton;
import com.arthou.smputilities.client.gui.SuTextField;
import com.arthou.smputilities.client.gui.TableView;
import com.arthou.smputilities.client.gui.Theme;
import com.arthou.smputilities.core.chat.StyledText;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.network.Dto;
import com.arthou.smputilities.server.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Player list with search, and everything staff can see or do for the selected player. */
final class PlayersTab extends PanelTab {
    enum Section { INFO, ACTIONS, NOTES, HISTORY }

    private static final String[] TROLLS = {"explosion", "lightning", "sound", "jumpscare", "nausea", "darkness", "levitate", "slow",
        "glow", "tiny_jump", "spin", "launch", "fake_message", "fake_join", "fake_quit"};

    private static String search = "";
    private static boolean offline;
    private static Section section = Section.INFO;
    private TableView list;
    private TableView sub;
    private String lastSignature = "";
    private int splitX;

    @Override
    void opened() {
        if (panel.selectedPlayer != null) {
            panel.request("player", panel.selectedPlayer.toString());
        }
        if (offline) {
            panel.request("table", "known|");
        }
    }

    @Override
    void refresh() {
        if (panel.selectedPlayer != null) {
            panel.request("player", panel.selectedPlayer.toString());
        }
        if (section == Section.HISTORY && panel.selectedPlayer != null) {
            panel.request("table", "history|" + panel.selectedPlayer);
        }
    }

    @Override
    void init() {
        int listWidth = Math.max(130, (int) (width() * 0.36));
        splitX = left() + listWidth + 8;
        SuTextField searchField = field(left(), top(), listWidth - 44, "search");
        searchField.setValue(search);
        searchField.setResponder(value -> {
            search = value;
            if (list != null) {
                list.setFilter(value);
            }
        });
        panel.add(new SuButton(left() + listWidth - 40, top(), 40, 18, tr(offline ? "list.online" : "list.offline"), button -> {
            offline = !offline;
            if (offline) {
                panel.request("table", "known|");
            }
            panel.refreshTab();
        }).kind(offline ? SuButton.Kind.PRIMARY : SuButton.Kind.GHOST).tooltip(tr("list.offline.tip")));

        list = table(left(), top() + 24, listWidth, bottom() - top() - 24, List.of());
        list.rowHeight = 20;
        list.remember("players." + offline);
        list.emptyText = tr(offline ? "list.empty_known" : "list.empty_online");
        list.selectedId = panel.selectedPlayer == null ? "" : panel.selectedPlayer.toString();
        list.rowRenderer = this::renderPlayerRow;
        list.onSelect = row -> {
            panel.selectedPlayer = UUID.fromString(row.id());
            panel.request("player", row.id());
            if (section == Section.HISTORY) {
                panel.request("table", "history|" + row.id());
            }
            panel.refreshTab();
        };
        list.setRows(rows());
        list.setFilter(search);

        if (panel.selectedPlayer == null) {
            return;
        }
        // section switcher
        int x = splitX;
        int tabWidth = (right() - splitX - 9) / 4;
        Section[] sections = Section.values();
        for (int i = 0; i < sections.length; i++) {
            Section value = sections[i];
            panel.add(new SuButton(x + i * (tabWidth + 3), top() + 34, tabWidth, 16, tr("section." + value.name().toLowerCase(java.util.Locale.ROOT)), button -> {
                section = value;
                if (value == Section.HISTORY) {
                    panel.request("table", "history|" + panel.selectedPlayer);
                }
                panel.refreshTab();
            }).kind(section == value ? SuButton.Kind.PRIMARY : SuButton.Kind.SUBTLE));
        }
        Dto.PlayerDetail detail = ClientState.detail(panel.selectedPlayer);
        lastSignature = signature();
        int contentTop = top() + 56;
        switch (section) {
            case ACTIONS -> actions(detail, contentTop);
            case NOTES -> notes(detail, contentTop);
            case HISTORY -> history(contentTop);
            default -> {
            }
        }
    }

    private String signature() {
        Dto.PlayerDetail detail = panel.selectedPlayer == null ? null : ClientState.detail(panel.selectedPlayer);
        if (detail == null) {
            return "";
        }
        return detail.id() + detail.get("online") + detail.get("mute") + detail.get("voiceMute") + detail.get("ban") + detail.get("vanished")
            + detail.get("voiceDeafened") + detail.get("silentJoin") + detail.get("silentQuit") + detail.get("nick") + detail.customStats() + detail.notes().size();
    }

    private List<Dto.Row> rows() {
        List<Dto.Row> rows = new ArrayList<>();
        if (offline) {
            Dto.Table known = ClientState.table("known", "");
            if (known != null) {
                rows.addAll(known.rows());
            }
            return rows;
        }
        Dto.Dashboard dashboard = ClientState.dashboard();
        if (dashboard != null) {
            for (Dto.PlayerRow player : dashboard.players()) {
                rows.add(new Dto.Row(player.id().toString(), List.of(player.name(), StyledText.strip(player.nick()), player.ping() + "ms",
                    player.vanished() ? "vanished" : "", player.muted() ? "muted" : "", player.staff() ? "staff" : "")));
            }
        }
        return rows;
    }

    private boolean renderPlayerRow(Gfx graphics, Dto.Row row, int x, int y, int width, int height, boolean hovered, boolean selected) {
        UUID id = UUID.fromString(row.id());
        String name = row.cells().get(0);
        String nick = row.cells().size() > 1 ? row.cells().get(1) : "";
        Heads.draw(graphics, id, name, x + 2, y + 2, 16);
        boolean showReal = ClientState.realNames() || nick.isEmpty();
        String primary = nick.isEmpty() ? name : nick;
        graphics.drawString(font, Theme.clip(font, primary, width - 60), x + 22, y + 2, Theme.TEXT, false);
        String secondary = showReal && !nick.isEmpty() ? name : offline ? row.cells().get(2) : "";
        if (!secondary.isEmpty()) {
            graphics.drawString(font, Theme.clip(font, secondary, width - 60), x + 22, y + 11, Theme.TEXT_OFF, false);
        }
        if (!offline) {
            int badgeX = x + width - 4;
            if (row.cells().size() > 3 && !row.cells().get(3).isEmpty()) {
                badgeX -= 10;
                graphics.drawString(font, "◐", badgeX, y + 6, Theme.ACCENT, false);
            }
            if (row.cells().size() > 4 && !row.cells().get(4).isEmpty()) {
                badgeX -= 10;
                graphics.drawString(font, "✕", badgeX, y + 6, Theme.WARN, false);
            }
            String ping = row.cells().get(2);
            graphics.drawString(font, ping, badgeX - font.width(ping) - 4, y + 6, Theme.TEXT_OFF, false);
        }
        return true;
    }

    @Override
    void dataChanged(String what) {
        if (what.equals("dashboard") && list != null && !offline) {
            list.setRows(rows());
        } else if (what.equals("table:known") && offline) {
            list.setRows(rows());
        } else if (what.equals("detail")) {
            // Buttons depend on the player's state (muted, banned, ...); rebuild only when it changed.
            String signature = signature();
            if (section != Section.INFO && !signature.equals(lastSignature)) {
                lastSignature = signature;
                panel.refreshTab();
            }
        } else if (what.equals("table:history") && sub != null && panel.selectedPlayer != null) {
            Dto.Table table = ClientState.table("history", panel.selectedPlayer.toString());
            if (table != null) {
                sub.setRows(table.rows());
            }
        }
    }

    // --------------------------------------------------------------- info

    @Override
    void renderBack(Gfx graphics, int mouseX, int mouseY, float partialTick) {
        if (panel.selectedPlayer == null) {
            graphics.drawCenteredString(font, tr("players.select"), (splitX + right()) / 2, top() + 60, Theme.TEXT_OFF);
            return;
        }
        Dto.PlayerDetail detail = ClientState.detail(panel.selectedPlayer);
        if (detail == null) {
            graphics.drawString(font, tr("loading"), splitX, top() + 4, Theme.TEXT_DIM, false);
            return;
        }
        Theme.roundBox(graphics, splitX, top(), right() - splitX, 30, Theme.CARD, Theme.BORDER);
        Heads.draw(graphics, detail.id(), detail.name(), splitX + 5, top() + 5, 20);
        String nick = detail.get("nick");
        Component title = nick.isEmpty() ? Texts.literal(detail.name()) : Msg.styled(nick);
        graphics.drawString(font, Theme.clip(font, title, right() - splitX - 110), splitX + 30, top() + 6, Theme.TEXT, false);
        String subtitle = nick.isEmpty() ? detail.id().toString() : detail.name() + " · " + detail.id();
        graphics.drawString(font, Theme.clip(font, subtitle, right() - splitX - 40), splitX + 30, top() + 17, Theme.TEXT_OFF, false);
        String status = detail.online() ? tr("status.online").getString() : tr("status.offline").getString();
        int pillWidth = font.width(status) + 16;
        Theme.pill(graphics, font, right() - pillWidth - 5, top() + 5, status, detail.online() ? Theme.ACCENT : Theme.TEXT_DIM);

        if (section != Section.INFO) {
            if (section == Section.NOTES) {
                renderNotes(graphics, detail);
            }
            return;
        }
        int y = top() + 58;
        int columnWidth = (right() - splitX) / 2;
        List<String[]> left = new ArrayList<>();
        List<String[]> rightColumn = new ArrayList<>();
        left.add(new String[] {"info.dimension", detail.get("dimension")});
        left.add(new String[] {"info.position", detail.get("x").isEmpty() ? "-" : detail.get("x") + " " + detail.get("y") + " " + detail.get("z")});
        left.add(new String[] {"info.health", detail.get("health").isEmpty() ? "-" : detail.get("health") + " / " + detail.get("maxHealth")});
        left.add(new String[] {"info.food", detail.get("food").isEmpty() ? "-" : detail.get("food") + " (" + detail.get("saturation") + ")"});
        left.add(new String[] {"info.gamemode", detail.get("gamemode").isEmpty() ? "-" : detail.get("gamemode")});
        left.add(new String[] {"info.ping", detail.get("ping").isEmpty() ? "-" : detail.get("ping") + " ms"});
        left.add(new String[] {"info.level", detail.get("xpLevel").isEmpty() ? "-" : detail.get("xpLevel")});
        left.add(new String[] {"info.profile", detail.get("activeProfile")});
        left.add(new String[] {"info.voice", tr("voice." + detail.get("voiceStatus")).getString()});
        left.add(new String[] {"info.vanished", yesNo(detail.get("vanished"))});
        left.add(new String[] {"info.afk", yesNo(detail.get("afk"))});
        rightColumn.add(new String[] {"info.deaths", detail.get("deaths")});
        rightColumn.add(new String[] {"info.player_kills", detail.get("playerKills")});
        rightColumn.add(new String[] {"info.mob_kills", detail.get("mobKills")});
        rightColumn.add(new String[] {"info.play_time", detail.get("playTime")});
        rightColumn.add(new String[] {"info.first_join", detail.get("firstJoin")});
        rightColumn.add(new String[] {"info.last_seen", detail.get("lastSeen").equals("online") ? tr("status.online").getString() : detail.get("lastSeen")});
        rightColumn.add(new String[] {"info.ban", punishment(detail.get("ban"))});
        rightColumn.add(new String[] {"info.mute", punishment(detail.get("mute"))});
        rightColumn.add(new String[] {"info.voice_mute", punishment(detail.get("voiceMute"))});
        rightColumn.add(new String[] {"info.records", detail.get("records") + " · " + tr("info.warnings", detail.get("warnings")).getString()});
        rightColumn.add(new String[] {"info.backups", detail.get("backups")});
        rightColumn.add(new String[] {"info.reports", detail.get("reports").isEmpty() ? "-" : detail.get("reports")});
        drawColumn(graphics, left, splitX, y, columnWidth - 6);
        drawColumn(graphics, rightColumn, splitX + columnWidth, y, columnWidth);
    }

    private void drawColumn(Gfx graphics, List<String[]> rows, int x, int y, int width) {
        int labelWidth = Math.min(80, width / 2);
        for (String[] row : rows) {
            if (y + 9 > bottom()) {
                break;
            }
            graphics.drawString(font, Theme.clip(font, tr(row[0]), labelWidth - 4), x, y, Theme.TEXT_OFF, false);
            String value = row[1] == null || row[1].isEmpty() ? "-" : row[1];
            int color = value.startsWith("⛔") ? Theme.DANGER : Theme.TEXT;
            graphics.drawString(font, Theme.clip(font, value, width - labelWidth - 4), x + labelWidth, y, color, false);
            y += 12;
        }
    }

    private static String yesNo(String value) {
        return Boolean.parseBoolean(value) ? tr("yes").getString() : tr("no").getString();
    }

    /** "time|by|reason" from the server, or empty. */
    private static String punishment(String value) {
        if (value == null || value.isEmpty()) {
            return tr("none").getString();
        }
        String[] parts = value.split("\\|", 3);
        String time = parts[0].equals("perm") ? tr("permanent").getString() : parts[0];
        return "⛔ " + time + (parts.length > 2 && !parts[2].isEmpty() ? " · " + parts[2] : "");
    }

    // ------------------------------------------------------------ actions

    private void actions(Dto.PlayerDetail detail, int y) {
        UUID id = panel.selectedPlayer;
        boolean online = detail != null && detail.online();
        boolean muted = detail != null && !detail.get("mute").isEmpty();
        boolean voiceMuted = detail != null && !detail.get("voiceMute").isEmpty();
        boolean banned = detail != null && !detail.get("ban").isEmpty();
        String name = detail == null ? id.toString() : detail.name();
        List<SuButton> buttons = new ArrayList<>();

        buttons.add(button(0, 0, 10, "action.teleport", Perm.STAFF_TELEPORT, b -> panel.action("tp", id)).icon("➜"));
        buttons.add(onlineOnly(button(0, 0, 10, "action.bring", Perm.STAFF_TELEPORT, b -> panel.action("tphere", id)).icon("⇦"), online));
        buttons.add(onlineOnly(button(0, 0, 10, "action.spectate", Perm.STAFF_SPECTATE, b -> panel.action("spectate", id)).icon("◉"), online));
        buttons.add(button(0, 0, 10, "action.inventory", Perm.STAFF_INVENTORY, b -> panel.action("inventory", id)).icon("▤"));
        buttons.add(button(0, 0, 10, "action.enderchest", Perm.STAFF_INVENTORY, b -> panel.action("enderchest", id)).icon("◆"));
        buttons.add(button(0, 0, 10, "action.backups", Perm.STAFF_INVENTORY, b -> panel.action("backups_open", id)).icon("⟲"));
        buttons.add(onlineOnly(button(0, 0, 10, "action.vanish", detail != null && Boolean.parseBoolean(detail.get("vanished")) ? Perm.STAFF_VANISH_OTHERS : Perm.STAFF_VANISH_OTHERS,
            b -> panel.action("vanish", id)).icon("◐"), online));
        buttons.add(button(0, 0, 10, "action.nickname", Perm.NICKNAME_OTHERS, b -> panel.form(tr("action.nickname"),
            List.of(fieldDef("nickname", detail == null ? "" : detail.get("nick"))), tr("button.save"), false, tr("nickname.note"), values -> {
                if (values.get(0).isBlank()) {
                    panel.action("nick_clear", id);
                } else {
                    panel.action("nick_set", id, values.get(0));
                }
            })).icon("✎"));
        buttons.add(onlineOnly(button(0, 0, 10, "action.gamemode", Perm.STAFF_GAMEMODE, b -> panel.choice(tr("action.gamemode"), List.of(
            new Dialog.Choice.Option("survival", Texts.translatable("gameMode.survival"), SuButton.Kind.GHOST),
            new Dialog.Choice.Option("creative", Texts.translatable("gameMode.creative"), SuButton.Kind.GHOST),
            new Dialog.Choice.Option("adventure", Texts.translatable("gameMode.adventure"), SuButton.Kind.GHOST),
            new Dialog.Choice.Option("spectator", Texts.translatable("gameMode.spectator"), SuButton.Kind.GHOST)),
            mode -> panel.action("gamemode", id, mode))).icon("⚒"), online));
        buttons.add(onlineOnly(button(0, 0, 10, "action.heal", Perm.STAFF_STATS_EDIT, b -> panel.action("heal", id)).icon("❤"), online));
        buttons.add(onlineOnly(button(0, 0, 10, "action.feed", Perm.STAFF_STATS_EDIT, b -> panel.action("feed", id)).icon("☕"), online));
        buttons.add(button(0, 0, 10, "action.profiles", Perm.STAFF_PROFILES, b -> panel.action("profiles_open", id)).icon("☰"));

        buttons.add(button(0, 0, 10, "action.warn", Perm.STAFF_WARN, b -> panel.form(tr("action.warn"), List.of(fieldDef("reason", "")),
            tr("action.warn"), false, null, values -> panel.action("warn", id, values.get(0)))).kind(SuButton.Kind.WARN).icon("⚠"));
        buttons.add(onlineOnly(button(0, 0, 10, "action.kick", Perm.STAFF_KICK, b -> panel.form(tr("action.kick"), List.of(fieldDef("reason", "")),
            tr("action.kick"), true, tr("kick.note", name), values -> panel.action("kick", id, values.get(0)))).kind(SuButton.Kind.DANGER).icon("⏏"), online));
        buttons.add(muted
            ? button(0, 0, 10, "action.unmute", Perm.STAFF_MUTE, b -> panel.confirm(tr("action.unmute"), tr("confirm.unmute", name), false,
                () -> panel.action("unmute", id))).icon("✓")
            : button(0, 0, 10, "action.mute", Perm.STAFF_MUTE, b -> punishForm("mute", "action.mute", id, name)).kind(SuButton.Kind.WARN).icon("✕"));
        buttons.add(voiceMuted
            ? button(0, 0, 10, "action.voice_unmute", Perm.STAFF_VOICE_MUTE, b -> panel.confirm(tr("action.voice_unmute"), tr("confirm.voice_unmute", name), false,
                () -> panel.action("voice_unmute", id))).icon("✓")
            : button(0, 0, 10, "action.voice_mute", Perm.STAFF_VOICE_MUTE, b -> punishForm("voice_mute", "action.voice_mute", id, name)).kind(SuButton.Kind.WARN).icon("♫"));
        buttons.add(button(0, 0, 10, detail != null && Boolean.parseBoolean(detail.get("voiceDeafened")) ? "action.undeafen" : "action.deafen",
            Perm.STAFF_VOICE_MUTE, b -> panel.action("voice_deafen", id)).icon("☊"));
        buttons.add(banned
            ? button(0, 0, 10, "action.unban", Perm.STAFF_BAN, b -> panel.confirm(tr("action.unban"), tr("confirm.unban", name), false,
                () -> panel.action("unban", id))).icon("✓")
            : button(0, 0, 10, "action.ban", Perm.STAFF_BAN, b -> punishForm("ban", "action.ban", id, name)).kind(SuButton.Kind.DANGER).icon("⛔"));

        buttons.add(button(0, 0, 10, "action.backup_now", Perm.STAFF_INVENTORY, b -> panel.action("backup_create", id)).icon("✚"));
        buttons.add(button(0, 0, 10, detail != null && Boolean.parseBoolean(detail.get("silentJoin")) ? "action.silent_join_off" : "action.silent_join",
            Perm.CHAT_MANAGE, b -> panel.action("silent_join", id)).icon("⇥"));
        buttons.add(button(0, 0, 10, detail != null && Boolean.parseBoolean(detail.get("silentQuit")) ? "action.silent_quit_off" : "action.silent_quit",
            Perm.CHAT_MANAGE, b -> panel.action("silent_quit", id)).icon("⇤"));
        buttons.add(onlineOnly(button(0, 0, 10, "action.troll", Perm.STAFF_TROLL, b -> trollMenu(id)).icon("☻"), online));
        int columns = right() - splitX > 300 ? 3 : 2;
        grid(splitX, y, right() - splitX, columns, 20, buttons);
    }

    private SuButton onlineOnly(SuButton button, boolean online) {
        if (!online) {
            button.active = false;
            button.tooltip(tr("needs_online"));
        }
        return button;
    }

    private void punishForm(String action, String titleKey, UUID id, String name) {
        panel.form(tr(titleKey), List.of(fieldDef("duration", ""), fieldDef("reason", "")), tr(titleKey), true, tr("duration.note", name),
            values -> panel.action(action, id, values.get(0).trim(), values.get(1)));
    }

    private void trollMenu(UUID id) {
        List<Dialog.Choice.Option> options = new ArrayList<>();
        for (String troll : TROLLS) {
            options.add(new Dialog.Choice.Option(troll, tr("troll." + troll), SuButton.Kind.GHOST));
        }
        panel.choice(tr("action.troll"), options, troll -> {
            switch (troll) {
                case "sound" -> panel.form(tr("troll.sound"), List.of(fieldDef("sound", "minecraft:entity.creeper.primed")), tr("button.send"), false, null,
                    values -> panel.action("troll", id, troll, values.get(0)));
                case "fake_message" -> panel.form(tr("troll.fake_message"), List.of(fieldDef("entity", "Herobrine"), fieldDef("message", "")), tr("button.send"),
                    false, null, values -> panel.action("troll", id, troll, values.get(0).trim() + " " + values.get(1)));
                case "fake_join", "fake_quit" -> panel.form(tr("troll." + troll), List.of(fieldDef("entity", "Herobrine")), tr("button.send"), false, null,
                    values -> panel.action("troll", id, troll, values.get(0)));
                default -> panel.action("troll", id, troll, "");
            }
        });
    }

    // -------------------------------------------------------------- notes

    private void notes(Dto.PlayerDetail detail, int y) {
        UUID id = panel.selectedPlayer;
        int width = right() - splitX;
        sub = table(splitX, y, width, 78, List.of(column("stat", 1), column("value", 1)));
        sub.emptyText = tr("stats.empty");
        if (detail != null) {
            List<Dto.Row> rows = new ArrayList<>();
            for (Map.Entry<String, String> entry : detail.customStats().entrySet()) {
                rows.add(new Dto.Row(entry.getKey(), List.of(entry.getKey(), entry.getValue())));
            }
            sub.setRows(rows);
        }
        boolean canEdit = ClientState.has(Perm.STAFF_STATS_EDIT);
        sub.buttons = row -> canEdit ? List.of(new TableView.RowButton("edit", tr("button.edit"), Theme.INFO),
            new TableView.RowButton("remove", tr("button.remove"), Theme.DANGER)) : List.of();
        sub.onButton = (row, button) -> {
            if (button.equals("remove")) {
                panel.confirm(tr("button.remove"), tr("confirm.remove_stat", row.id()), true, () -> panel.action("custom_stat_remove", id, row.id()));
            } else {
                panel.form(tr("stats.edit"), List.of(fieldDef("stat", row.id()), fieldDef("value", row.cells().get(1))), tr("button.save"), false, null,
                    values -> panel.action("custom_stat_set", id, values.get(0), values.get(1)));
            }
        };
        int buttonsY = y + 82;
        List<SuButton> buttons = new ArrayList<>();
        buttons.add(button(0, 0, 10, "stats.add", Perm.STAFF_STATS_EDIT, b -> panel.form(tr("stats.add"), List.of(fieldDef("stat", ""), fieldDef("value", "")),
            tr("button.save"), false, null, values -> panel.action("custom_stat_set", id, values.get(0), values.get(1)))).icon("✚"));
        buttons.add(button(0, 0, 10, "stats.deaths", Perm.STAFF_STATS_EDIT, b -> panel.form(tr("stats.deaths"),
            List.of(fieldDef("value", detail == null ? "" : detail.get("deaths"))), tr("button.save"), false, null,
            values -> panel.action("stat_set", id, "deaths", values.get(0)))));
        buttons.add(button(0, 0, 10, "stats.kills", Perm.STAFF_STATS_EDIT, b -> panel.form(tr("stats.kills"),
            List.of(fieldDef("value", detail == null ? "" : detail.get("playerKills"))), tr("button.save"), false, null,
            values -> panel.action("stat_set", id, "player_kills", values.get(0)))));
        buttons.add(button(0, 0, 10, "notes.add", Perm.STAFF_HISTORY, b -> panel.form(tr("notes.add"), List.of(fieldDef("note", "")), tr("button.save"),
            false, null, values -> {
                if (!values.get(0).isBlank()) {
                    panel.action("note_add", id, values.get(0));
                }
            })).icon("✎"));
        grid(splitX, buttonsY, width, 4, 20, buttons);
    }

    private void renderNotes(Gfx graphics, Dto.PlayerDetail detail) {
        int y = top() + 56 + 108;
        section(graphics, "notes.title", splitX, y, right() - splitX);
        y += 13;
        if (detail.notes().isEmpty()) {
            graphics.drawString(font, tr("notes.empty"), splitX, y, Theme.TEXT_OFF, false);
            return;
        }
        for (int i = detail.notes().size() - 1; i >= 0 && y + 9 <= bottom(); i--) {
            for (var line : font.split(Texts.literal(detail.notes().get(i)).withStyle(ChatFormatting.GRAY), right() - splitX)) {
                if (y + 9 > bottom()) {
                    break;
                }
                graphics.drawString(font, line, splitX, y, Theme.TEXT_DIM, false);
                y += 10;
            }
            y += 2;
        }
    }

    // ------------------------------------------------------------ history

    private void history(int y) {
        UUID id = panel.selectedPlayer;
        sub = table(splitX, y, right() - splitX, bottom() - y, List.of(column("type", 1), column("date", 1.3F), column("by", 1), column("reason", 1.6F),
            column("status", 1.1F)));
        Dto.Table table = ClientState.table("history", id.toString());
        if (table != null) {
            List<Dto.Row> rows = new ArrayList<>();
            for (Dto.Row row : table.rows()) {
                List<String> cells = row.cells();
                rows.add(new Dto.Row(row.id(), List.of(cells.get(1), cells.get(3), cells.get(4), cells.get(5), cells.get(6))));
            }
            sub.setRows(rows);
        }
        sub.buttons = row -> row.cells().get(4).startsWith("active") && ClientState.has(Perm.STAFF_BAN)
            ? List.of(new TableView.RowButton("revoke", tr("button.revoke"), Theme.WARN)) : List.of();
        sub.onButton = (row, button) -> panel.confirm(tr("button.revoke"), tr("confirm.revoke", row.cells().get(0)), false,
            () -> {
                panel.action("revoke", id, row.cells().get(0));
                panel.request("table", "history|" + id);
            });
    }
}
