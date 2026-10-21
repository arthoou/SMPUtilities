package com.arthou.smputilities.client.staff;

import com.arthou.smputilities.client.gui.Gfx;
import com.arthou.smputilities.client.ClientState;
import com.arthou.smputilities.client.gui.TableView;
import com.arthou.smputilities.client.gui.Theme;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.network.Dto;

import java.util.ArrayList;
import java.util.List;

/** Dimension access and portal limits. */
final class WorldTab extends PanelTab {
    private TableView dimensions;
    private TableView limits;
    private TableView portals;
    private int middle;

    @Override
    void opened() {
        panel.request("table", "dimensions|");
        panel.request("table", "portals|");
    }

    @Override
    void refresh() {
        panel.request("table", "dimensions|");
        panel.request("table", "portals|");
    }

    @Override
    void init() {
        int half = (width() - 8) / 2;
        middle = top() + (bottom() - top()) / 2;
        dimensions = table(left(), top() + 12, half, middle - top() - 16, List.of(column("name", 1), column("open", 0.6F), column("players", 0.5F)));
        dimensions.remember("world.dimensions");
        boolean manageDimensions = ClientState.has(Perm.DIMENSION_MANAGE);
        dimensions.buttons = row -> manageDimensions ? List.of(row.cells().get(3).equals("true")
            ? new TableView.RowButton("block", tr("button.block"), Theme.DANGER)
            : new TableView.RowButton("open", tr("button.open"), Theme.ACCENT)) : List.of();
        dimensions.onButton = (row, button) -> {
            boolean open = button.equals("open");
            panel.confirm(tr(open ? "button.open" : "button.block"), tr(open ? "confirm.open_dimension" : "confirm.block_dimension", row.cells().get(0)),
                !open, () -> {
                    panel.action("dimension", "", row.id(), String.valueOf(open));
                    panel.request("table", "dimensions|");
                });
        };

        limits = table(left() + half + 8, top() + 12, half, middle - top() - 38, List.of(column("type", 1), column("count", 0.8F)));
        boolean managePortals = ClientState.has(Perm.PORTAL_MANAGE);
        limits.buttons = row -> managePortals ? List.of(new TableView.RowButton("set", tr("button.set_limit"), Theme.INFO),
            new TableView.RowButton("unlimited", tr("button.unlimited"), Theme.TEXT_DIM)) : List.of();
        limits.onButton = (row, button) -> {
            if (button.equals("unlimited")) {
                panel.action("portal_limit", "", row.id(), "unlimited");
                panel.request("table", "portals|");
            } else {
                panel.form(tr("button.set_limit"), List.of(fieldDef("limit", "")), tr("button.save"), false, tr("limit.note", row.id()), values -> {
                    panel.action("portal_limit", "", row.id(), values.get(0).trim());
                    panel.request("table", "portals|");
                });
            }
        };
        button(left() + half + 8, middle - 22, half, "button.add_limit", Perm.PORTAL_MANAGE, b -> panel.form(tr("button.add_limit"),
            List.of(fieldDef("dimension_key", ""), fieldDef("limit", "")), tr("button.save"), false, tr("add_limit.note"), values -> {
                if (!values.get(0).isBlank()) {
                    panel.action("portal_limit", "", values.get(0).trim(), values.get(1).trim());
                    panel.request("table", "portals|");
                }
            })).icon("✚");

        portals = table(left(), middle + 12, width(), bottom() - middle - 12, List.of(column("id", 0.5F), column("type", 0.8F),
            column("dimension", 1.4F), column("position", 1.2F), column("creator", 1)));
        portals.remember("world.portals");
        portals.emptyText = tr("portals.empty");
        portals.buttons = row -> managePortals ? List.of(new TableView.RowButton("remove", tr("button.remove"), Theme.DANGER)) : List.of();
        portals.onButton = (row, button) -> panel.confirm(tr("button.remove"), tr("confirm.remove_portal", row.cells().get(0)), true, () -> {
            panel.action("portal_remove", "", row.id());
            panel.request("table", "portals|");
        });
        fill();
    }

    private void fill() {
        Dto.Table dims = ClientState.table("dimensions", "");
        if (dims != null && dimensions != null) {
            List<Dto.Row> rows = new ArrayList<>();
            for (Dto.Row row : dims.rows()) {
                rows.add(new Dto.Row(row.id(), List.of(row.cells().get(0), row.cells().get(2).equals("true") ? tr("dimension.open").getString()
                    : tr("dimension.blocked").getString(), row.cells().get(3), row.cells().get(2))));
            }
            dimensions.setRows(rows);
        }
        // The portals table carries the limit summary in its context.
        Dto.Table latest = ClientState.latestTable("portals");
        if (latest != null) {
            if (portals != null) {
                portals.setRows(latest.rows());
            }
            if (limits != null) {
                List<Dto.Row> rows = new ArrayList<>();
                for (String entry : latest.context().split(",")) {
                    String[] parts = entry.split("=", 2);
                    if (parts.length == 2) {
                        rows.add(new Dto.Row(parts[0], List.of(parts[0], parts[1])));
                    }
                }
                limits.setRows(rows);
            }
        }
    }

    @Override
    void dataChanged(String what) {
        if (what.equals("table:dimensions") || what.equals("table:portals")) {
            fill();
        }
    }

    @Override
    void renderBack(Gfx graphics, int mouseX, int mouseY, float partialTick) {
        int half = (width() - 8) / 2;
        section(graphics, "section.dimensions", left(), top(), half);
        section(graphics, "section.portal_limits", left() + half + 8, top(), half);
        section(graphics, "section.portals", left(), middle, width());
    }
}
