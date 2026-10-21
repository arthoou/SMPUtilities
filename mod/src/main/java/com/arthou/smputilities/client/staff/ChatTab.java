package com.arthou.smputilities.client.staff;

import com.arthou.smputilities.client.gui.Gfx;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.client.ClientState;
import com.arthou.smputilities.client.gui.SuButton;
import com.arthou.smputilities.client.gui.SuTextField;
import com.arthou.smputilities.client.gui.TableView;
import com.arthou.smputilities.client.gui.Theme;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.network.Dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chat settings, moderation and announcements, and the chat history: every
 * public message of a day, read from the server's files a page at a time.
 */
final class ChatTab extends PanelTab {
    enum View { OPTIONS, HISTORY }

    private static final int VIEW_BAR = 22;
    private static View view = View.OPTIONS;
    // History state survives the widget rebuilds; rows are only the pages loaded so far.
    private static String day = "";
    private static final List<Dto.Row> HISTORY = new ArrayList<>();
    private static boolean exhausted;
    private static boolean loading;
    private static String historyFilter = "";
    private TableView mutes;
    private TableView history;
    private Map<String, String> lastFlags = Map.of();

    @Override
    void opened() {
        if (view == View.HISTORY) {
            openHistory();
            return;
        }
        panel.request("table", "mutes|");
        if (ClientState.has(Perm.ADMIN_SETTINGS)) {
            panel.request("settings", "");
        }
    }

    @Override
    void refresh() {
        if (view == View.OPTIONS) {
            panel.request("table", "mutes|");
        }
    }

    private void openHistory() {
        panel.request("table", "chatdays|");
        loadDay(day);
    }

    private void loadDay(String value) {
        day = value;
        HISTORY.clear();
        exhausted = false;
        loading = true;
        panel.request("table", "chatlog|" + day + "|");
    }

    private void loadOlder() {
        if (loading || exhausted || HISTORY.isEmpty()) {
            return;
        }
        loading = true;
        panel.request("table", "chatlog|" + day + "|" + HISTORY.get(HISTORY.size() - 1).id());
    }

    /** Days with history, newest first; today is always there. */
    private static List<String> days() {
        Dto.Table table = ClientState.table("chatdays", "");
        List<String> days = new ArrayList<>();
        if (table != null) {
            for (Dto.Row row : table.rows()) {
                days.add(row.id());
            }
        }
        return days;
    }

    private String shownDay() {
        List<String> days = days();
        return !day.isEmpty() ? day : days.isEmpty() ? "" : days.get(0);
    }

    private void viewBar() {
        View[] views = View.values();
        int barWidth = Math.min(110, (width() - 6) / views.length);
        for (int i = 0; i < views.length; i++) {
            View value = views[i];
            SuButton button = panel.add(new SuButton(left() + i * (barWidth + 3), top(), barWidth, 18,
                tr("chat.view." + value.name().toLowerCase(Locale.ROOT)), b -> {
                    view = value;
                    opened();
                    panel.refreshTab();
                }).kind(view == value ? SuButton.Kind.PRIMARY : SuButton.Kind.SUBTLE));
            if (value == View.HISTORY) {
                button.active = ClientState.has(Perm.CHAT_HISTORY);
            }
        }
    }

    private void historyView() {
        int y = top() + VIEW_BAR;
        List<String> days = days();
        String shown = shownDay();
        int index = days.indexOf(shown);
        panel.add(new SuButton(left(), y, 20, 18, Texts.literal("◀"), b -> {
            loadDay(days.get(index + 1));
            panel.refreshTab();
        }).enabled(index >= 0 && index + 1 < days.size()).tooltip(tr("history.older_day")));
        panel.add(new SuButton(left() + 104, y, 20, 18, Texts.literal("▶"), b -> {
            loadDay(days.get(index - 1));
            panel.refreshTab();
        }).enabled(index > 0).tooltip(tr("history.newer_day")));
        panel.add(new SuButton(right() - 20, y, 20, 18, Texts.literal("⟳"), b -> {
            loadDay(day);
            panel.refreshTab();
        }).tooltip(tr("history.reload")));
        SuTextField search = field(right() - 176, y, 152, "search");
        search.setValue(historyFilter);
        search.setResponder(value -> {
            historyFilter = value;
            if (history != null) {
                history.setFilter(value);
            }
        });
        int tableTop = y + 24;
        history = table(left(), tableTop, width(), bottom() - tableTop - 22, List.of(column("time", 0.55F), column("player", 0.9F),
            column("message", 3.6F)));
        history.remember("chat.history." + shown);
        history.emptyText = tr(loading ? "loading" : "history.empty");
        history.onSelect = row -> panel.message(tr("history.message", row.cells().get(1), row.cells().get(0)), Texts.literal(row.cells().get(2)));
        fillHistory();
        panel.add(new SuButton(left(), bottom() - 18, width(), 18, tr(exhausted ? "history.start" : "history.older"), b -> loadOlder())
            .enabled(!exhausted && !loading && !HISTORY.isEmpty()));
    }

    private void fillHistory() {
        if (history == null) {
            return;
        }
        List<Dto.Row> rows = new ArrayList<>();
        for (Dto.Row row : HISTORY) {
            rows.add(new Dto.Row(row.id(), row.cells().subList(0, 3)));
        }
        history.setRows(rows);
        history.setFilter(historyFilter);
    }

    private String setting(String path, String fallback) {
        for (Dto.Setting setting : ClientState.settings()) {
            if (setting.path().equals(path)) {
                return setting.value();
            }
        }
        Dto.Dashboard dashboard = ClientState.dashboard();
        if (dashboard != null && dashboard.flags().containsKey(path)) {
            return dashboard.flags().get(path);
        }
        return fallback;
    }

    @Override
    void init() {
        viewBar();
        if (view == View.HISTORY) {
            historyView();
            return;
        }
        Dto.Dashboard dashboard = ClientState.dashboard();
        lastFlags = dashboard == null ? Map.of() : dashboard.flags();
        int x = left();
        int half = (width() - 6) / 2;
        int y = top() + VIEW_BAR + 12;
        String[][] toggles = {
            {"chat.globalChatEnabled", "toggle.global_chat"},
            {"chat.customFormat", "toggle.custom_format"},
            {"chat.joinMessages", "toggle.join_messages"},
            {"chat.quitMessages", "toggle.quit_messages"},
            {"chat.privateMessagesEnabled", "toggle.private_messages"},
            {"chat.allowPrivateMessagesWhileLocked", "toggle.pm_while_locked"},
            {"chat.roleColors", "toggle.role_colors"},
            {"chat.mentions.enabled", "toggle.mentions"},
            {"chat.spam.enabled", "toggle.spam"},
            {"chat.playerColors", "toggle.player_colors"}
        };
        for (int i = 0; i < toggles.length; i++) {
            String path = toggles[i][0];
            boolean value = Boolean.parseBoolean(setting(path, "false"));
            toggle(x + (i % 2) * (half + 6), y + (i / 2) * 21, half, toggles[i][1], value, on -> {
                if (path.equals("chat.globalChatEnabled")) {
                    panel.action("chat_lock", "", String.valueOf(!on));
                } else {
                    panel.action("chat_setting", "", path, String.valueOf(on));
                }
            });
        }
        y += 5 * 21 + 4;
        List<SuButton> buttons = new ArrayList<>();
        buttons.add(button(0, 0, 10, "button.formats", b -> panel.form(tr("button.formats"), List.of(
            fieldDef("message_format", setting("chat.messageFormat", "&e{name}&8 » &f{message}")),
            fieldDef("join_format", setting("chat.joinFormat", "&7[&a+&7] &f{name}")),
            fieldDef("quit_format", setting("chat.quitFormat", "&7[&c-&7] &f{name}"))), tr("button.save"), false, tr("formats.note"), values -> {
                panel.action("chat_setting", "", "chat.messageFormat", values.get(0));
                panel.action("chat_setting", "", "chat.joinFormat", values.get(1));
                panel.action("chat_setting", "", "chat.quitFormat", values.get(2));
            })).icon("✎"));
        buttons.add(button(0, 0, 10, "button.cooldown", b -> panel.form(tr("button.cooldown"),
            List.of(fieldDef("seconds", setting("chat.cooldownSeconds", "0"))), tr("button.save"), false, null,
            values -> panel.action("chat_setting", "", "chat.cooldownSeconds", values.get(0).trim()))).icon("⌛"));
        buttons.add(button(0, 0, 10, "button.clear_chat", b -> panel.confirm(tr("button.clear_chat"), tr("confirm.clear_chat"), true,
            () -> panel.action("chat_clear", ""))).kind(SuButton.Kind.DANGER).icon("✎"));
        y = grid(x, y, width(), 3, 21, buttons) + 2;

        SuTextField say = field(x, y, width() - 70, "say_hint");
        say.setMaxLength(256);
        panel.add(new SuButton(right() - 64, y, 64, 18, tr("button.send"), b -> {
            if (!say.getValue().isBlank()) {
                panel.action("say", "", say.getValue());
                say.setValue("");
            }
        }).kind(SuButton.Kind.PRIMARY).enabled(ClientState.has(Perm.STAFF_ANNOUNCE)));
        y += 26;

        mutes = table(x, y + 12, width(), bottom() - y - 12, List.of(column("player", 1.2F), column("type", 0.9F), column("remaining", 0.9F),
            column("by", 0.9F), column("reason", 1.6F)));
        mutes.remember("chat.mutes");
        mutes.emptyText = tr("mutes.empty");
        fillMutes();
        mutes.buttons = row -> List.of(new TableView.RowButton("unmute", tr("action.unmute"), Theme.ACCENT));
        mutes.onButton = (row, button) -> panel.confirm(tr("action.unmute"), tr("confirm.unmute", row.cells().get(0)), false, () -> {
            panel.action(row.cells().get(1).equals("VOICE_MUTE") ? "voice_unmute" : "unmute", row.id());
            panel.request("table", "mutes|");
        });
    }

    private void fillMutes() {
        Dto.Table table = ClientState.table("mutes", "");
        if (table != null && mutes != null) {
            List<Dto.Row> rows = new ArrayList<>();
            for (Dto.Row row : table.rows()) {
                List<String> cells = row.cells();
                rows.add(new Dto.Row(row.id(), List.of(cells.get(0), cells.get(1), cells.get(3), cells.get(4), cells.get(5))));
            }
            mutes.setRows(rows);
        }
    }

    @Override
    void dataChanged(String what) {
        if (what.equals("table:chatlog")) {
            Dto.Table page = ClientState.latestTable("chatlog");
            if (page == null || !page.context().startsWith(day + "|")) {
                return;
            }
            loading = false;
            String before = page.context().substring(day.length() + 1);
            if (before.isEmpty()) {
                HISTORY.clear();
            } else if (HISTORY.isEmpty() || !HISTORY.get(HISTORY.size() - 1).id().equals(before)) {
                return;
            }
            HISTORY.addAll(page.rows());
            // The first line of the file starts at 0; a short page also means nothing older is left.
            exhausted = page.rows().size() < 100 || page.rows().get(page.rows().size() - 1).id().equals("0");
            if (view == View.HISTORY) {
                panel.refreshTab();
            }
        } else if (what.equals("table:chatdays")) {
            if (view == View.HISTORY) {
                panel.refreshTab();
            }
        } else if (what.equals("table:mutes")) {
            fillMutes();
        } else if (what.equals("settings")) {
            panel.refreshTab();
        } else if (what.equals("dashboard")) {
            Dto.Dashboard dashboard = ClientState.dashboard();
            if (dashboard != null && !dashboard.flags().equals(lastFlags)) {
                panel.refreshTab();
            }
        }
    }

    @Override
    void renderBack(Gfx graphics, int mouseX, int mouseY, float partialTick) {
        if (view == View.HISTORY) {
            String shown = shownDay();
            graphics.drawCenteredString(font, shown.isEmpty() ? "-" : shown, left() + 62, top() + VIEW_BAR + 5, Theme.TEXT);
            return;
        }
        section(graphics, "section.chat_options", left(), top() + VIEW_BAR, width());
        int y = top() + VIEW_BAR + 12 + 5 * 21 + 4 + 21 + 2 + 26;
        section(graphics, "section.muted", left(), y, width());
    }
}
