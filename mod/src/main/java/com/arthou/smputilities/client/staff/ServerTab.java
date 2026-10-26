package com.arthou.smputilities.client.staff;

import com.arthou.smputilities.client.ClientState;
import com.arthou.smputilities.client.gui.Gfx;
import com.arthou.smputilities.client.gui.SuButton;
import com.arthou.smputilities.client.gui.TableView;
import com.arthou.smputilities.client.gui.Theme;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.network.Dto;

import java.util.ArrayList;
import java.util.List;

/** Performance (tick times, what each dimension holds, the busiest chunks, clean-ups) and access (maintenance, whitelist). */
final class ServerTab extends PanelTab {
    private TableView lag;
    private TableView hotspots;
    private TableView whitelist;
    private int lower;
    private int split;
    /** The switches the tab was built with ("whitelist|maintenance"); a change rebuilds it. */
    private String switches = "";

    private void requestAll() {
        if (ClientState.has(Perm.STAFF_LAG)) {
            panel.request("table", "lag|");
            panel.request("table", "hotspots|");
        }
        if (ClientState.has(Perm.STAFF_WHITELIST)) {
            panel.request("table", "whitelist|");
        }
    }

    @Override
    void opened() {
        requestAll();
    }

    @Override
    void refresh() {
        requestAll();
    }

    @Override
    void init() {
        boolean manageLag = ClientState.has(Perm.STAFF_LAG);
        lag = table(left(), top() + 12, width(), 70, List.of(column("name", 1.6F), column("tps", 0.5F), column("mspt", 0.6F),
            column("players", 0.6F), column("chunks", 0.6F), column("entities", 0.7F), column("items", 0.6F), column("orbs_hostile", 0.9F)));
        lag.remember("server.lag");
        int y = top() + 86;
        List<SuButton> clears = new ArrayList<>();
        for (String what : List.of("items", "xp", "hostile")) {
            clears.add(button(0, 0, 10, "button.clear_" + what, Perm.STAFF_LAG, b -> panel.confirm(tr("button.clear_" + what),
                tr("confirm.clear_" + what), true, () -> {
                    panel.action("lag_clear", "", what);
                    panel.request("table", "lag|");
                    panel.request("table", "hotspots|");
                })).icon("✖"));
        }
        grid(left(), y, width(), 3, 22, clears);

        lower = y + 26;
        split = left() + (width() - 8) * 3 / 5;
        hotspots = table(left(), lower + 12, split - left(), bottom() - lower - 12, List.of(column("dimension", 1.2F), column("position", 1),
            column("count", 0.5F), column("top", 1.4F)));
        hotspots.remember("server.hotspots");
        hotspots.emptyText = tr(manageLag ? "empty" : "no_permission");
        hotspots.buttons = row -> manageLag && ClientState.has(Perm.STAFF_TELEPORT)
            ? List.of(new TableView.RowButton("tp", tr("action.report_tp"), Theme.INFO)) : List.of();
        hotspots.onButton = (row, button) -> panel.action("lag_tp", "", row.id());

        int right = split + 8;
        int rightWidth = right() - right;
        String[] state = whitelistState();
        switches = String.join("|", state);
        toggle(right, lower + 12, rightWidth, "toggle.maintenance", state[1].equals("true"), value -> {
            if (value) {
                // The switch keeps showing the server's state; the form decides.
                panel.refreshTab();
                panel.form(tr("toggle.maintenance"), List.of(fieldDef("maintenance_message", "")), tr("button.maintenance_on"), true,
                    tr("maintenance.note"), values -> {
                        panel.action("maintenance_on", "", values.get(0).trim());
                        panel.request("table", "whitelist|");
                    });
            } else {
                panel.action("maintenance_off", "");
                panel.request("table", "whitelist|");
            }
        }).active = ClientState.has(Perm.ADMIN_MAINTENANCE);
        boolean manageWhitelist = ClientState.has(Perm.STAFF_WHITELIST);
        toggle(right, lower + 34, rightWidth, "toggle.whitelist", state[0].equals("true"), value -> {
            if (value) {
                panel.refreshTab();
                panel.confirm(tr("toggle.whitelist"), tr("confirm.whitelist_on"), true, () -> {
                    panel.action("whitelist_on", "");
                    panel.request("table", "whitelist|");
                });
            } else {
                panel.action("whitelist_off", "");
                panel.request("table", "whitelist|");
            }
        }).active = manageWhitelist;
        whitelist = table(right, lower + 56, rightWidth, Math.max(20, bottom() - lower - 80), List.of(column("name", 1)));
        whitelist.remember("server.whitelist");
        whitelist.emptyText = tr(manageWhitelist ? "whitelist.empty" : "no_permission");
        whitelist.buttons = row -> manageWhitelist ? List.of(new TableView.RowButton("remove", tr("button.remove"), Theme.DANGER)) : List.of();
        whitelist.onButton = (row, button) -> panel.confirm(tr("button.remove"), tr("confirm.whitelist_remove", row.id()), true, () -> {
            panel.action("whitelist_remove", "", row.id());
            panel.request("table", "whitelist|");
        });
        button(right, bottom() - 20, rightWidth, "button.whitelist_add", Perm.STAFF_WHITELIST, b -> panel.form(tr("button.whitelist_add"),
            List.of(fieldDef("player", "")), tr("button.save"), false, tr("whitelist.note"), values -> {
                if (!values.get(0).isBlank()) {
                    panel.action("whitelist_add", "", values.get(0).trim());
                    panel.request("table", "whitelist|");
                }
            })).icon("✚");
        fill();
    }

    /** {whitelist on, maintenance on}, as the server last said. */
    private static String[] whitelistState() {
        Dto.Table latest = ClientState.latestTable("whitelist");
        String[] parts = latest == null ? new String[0] : latest.context().split("\\|");
        return new String[] {parts.length > 0 ? parts[0] : "false", parts.length > 1 ? parts[1] : "false"};
    }

    private void fill() {
        Dto.Table data = ClientState.table("lag", "");
        if (data != null && lag != null) {
            List<Dto.Row> rows = new ArrayList<>();
            for (Dto.Row row : data.rows()) {
                List<String> cells = new ArrayList<>(row.cells());
                cells.set(0, row.id().equals("server") ? tr("lag.server").getString() : cells.get(0).replace("minecraft:", ""));
                rows.add(new Dto.Row(row.id(), cells));
            }
            lag.setRows(rows);
        }
        data = ClientState.table("hotspots", "");
        if (data != null && hotspots != null) {
            List<Dto.Row> rows = new ArrayList<>();
            for (Dto.Row row : data.rows()) {
                List<String> cells = new ArrayList<>(row.cells());
                cells.set(0, cells.get(0).replace("minecraft:", ""));
                rows.add(new Dto.Row(row.id(), cells));
            }
            hotspots.setRows(rows);
        }
        Dto.Table list = ClientState.latestTable("whitelist");
        if (list != null && whitelist != null) {
            whitelist.setRows(list.rows());
        }
    }

    @Override
    void dataChanged(String what) {
        if (what.equals("table:whitelist") && !String.join("|", whitelistState()).equals(switches)) {
            panel.refreshTab();
            return;
        }
        if (what.equals("table:lag") || what.equals("table:hotspots") || what.equals("table:whitelist")) {
            fill();
        }
    }

    @Override
    void renderBack(Gfx graphics, int mouseX, int mouseY, float partialTick) {
        section(graphics, "section.performance", left(), top(), width());
        section(graphics, "section.hotspots", left(), lower, split - left());
        section(graphics, "section.access", split + 8, lower, right() - split - 8);
    }
}
