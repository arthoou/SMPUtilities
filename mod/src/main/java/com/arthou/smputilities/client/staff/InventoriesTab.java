package com.arthou.smputilities.client.staff;

import com.arthou.smputilities.client.gui.Gfx;
import com.arthou.smputilities.client.ClientState;
import com.arthou.smputilities.client.gui.SuButton;
import com.arthou.smputilities.client.gui.TableView;
import com.arthou.smputilities.client.gui.Theme;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.network.Dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Inventory views, backups with restore, and inventory profiles of the selected player. */
final class InventoriesTab extends PanelTab {
    private TableView backups;
    private TableView profiles;
    private int contentX;
    private int split;

    private PlayerPicker picker() {
        return pickerInstance == null ? (pickerInstance = new PlayerPicker(panel, true)) : pickerInstance;
    }

    private PlayerPicker pickerInstance;

    @Override
    void opened() {
        requestTables();
    }

    @Override
    void refresh() {
        requestTables();
    }

    private void requestTables() {
        UUID id = panel.selectedPlayer;
        if (id != null) {
            panel.request("table", "backups|" + id);
            if (ClientState.has(Perm.STAFF_PROFILES)) {
                panel.request("table", "profiles|" + id);
            }
            panel.request("player", id.toString());
        }
    }

    @Override
    void init() {
        int pickerWidth = Math.max(110, (int) (width() * 0.28));
        contentX = picker().build(left(), top(), bottom(), pickerWidth, () -> {
            requestTables();
            panel.refreshTab();
        });
        UUID id = panel.selectedPlayer;
        if (id == null) {
            return;
        }
        int width = right() - contentX;
        List<SuButton> buttons = new ArrayList<>();
        buttons.add(button(0, 0, 10, "action.inventory", Perm.STAFF_INVENTORY, b -> panel.action("inventory", id)).icon("▤"));
        buttons.add(button(0, 0, 10, "action.enderchest", Perm.STAFF_INVENTORY, b -> panel.action("enderchest", id)).icon("◆"));
        buttons.add(button(0, 0, 10, "action.curios", Perm.STAFF_INVENTORY, b -> panel.action("curios", id)).icon("✧"));
        buttons.add(button(0, 0, 10, "action.cosmetic", Perm.STAFF_INVENTORY, b -> panel.action("cosmetic", id)).icon("♛"));
        buttons.add(button(0, 0, 10, "action.backup_now", Perm.STAFF_INVENTORY, b -> {
            panel.action("backup_create", id);
            panel.request("table", "backups|" + id);
        }).icon("✚"));
        buttons.add(button(0, 0, 10, "action.backups", Perm.STAFF_INVENTORY, b -> panel.action("backups_open", id)).icon("⟲"));
        int y = grid(contentX, top() + 12, width, 3, 20, buttons) + 14;

        boolean showProfiles = ClientState.has(Perm.STAFF_PROFILES);
        split = showProfiles ? y + (bottom() - y) * 3 / 5 : bottom();
        backups = table(contentX, y, width, split - y - (showProfiles ? 16 : 0), List.of(column("index", 0.4F), column("id", 0.6F), column("type", 1.1F),
            column("date", 1.3F)));
        backups.remember("backups");
        backups.emptyText = tr("backups.empty");
        boolean restore = ClientState.has(Perm.STAFF_INVENTORY_RESTORE);
        backups.buttons = row -> restore ? List.of(
            new TableView.RowButton("full", tr("button.restore"), Theme.ACCENT),
            new TableView.RowButton("inventory", tr("button.inv"), Theme.INFO),
            new TableView.RowButton("ender", tr("button.ender"), Theme.INFO),
            new TableView.RowButton("delete", tr("button.delete_short"), Theme.DANGER)) : List.of();
        backups.onButton = (row, button) -> {
            String backupId = row.id();
            if (button.equals("delete")) {
                panel.confirm(tr("button.delete"), tr("confirm.delete_backup", backupId), true, () -> {
                    panel.action("backup_delete", id, backupId);
                    panel.request("table", "backups|" + id);
                });
                return;
            }
            panel.confirm(tr("button.restore"), tr("confirm.restore_" + button, backupId, name(id)), true, () -> {
                panel.action("backup_restore", id, backupId, button);
                panel.request("table", "backups|" + id);
            });
        };
        fillBackups();

        if (showProfiles) {
            profiles = table(contentX, split + 12, width, bottom() - split - 12, List.of(column("name", 1), column("active", 0.6F), column("updated", 1.2F),
                column("nick", 1)));
            profiles.emptyText = tr("profiles.empty");
            profiles.buttons = row -> row.cells().get(1).equals("true") ? List.of() : List.of(
                new TableView.RowButton("switch", tr("button.switch"), Theme.ACCENT),
                new TableView.RowButton("delete", tr("button.delete_short"), Theme.DANGER));
            profiles.onButton = (row, button) -> {
                String profile = row.id();
                if (button.equals("switch")) {
                    panel.confirm(tr("button.switch"), tr("confirm.switch_profile", name(id), profile), false, () -> {
                        panel.action("profile_switch", id, profile);
                        panel.request("table", "profiles|" + id);
                    });
                } else {
                    panel.confirm(tr("button.delete"), tr("confirm.delete_profile", profile), true, () -> {
                        panel.action("profile_delete", id, profile);
                        panel.request("table", "profiles|" + id);
                    });
                }
            };
            fillProfiles();
        }
    }

    private String name(UUID id) {
        Dto.PlayerDetail detail = ClientState.detail(id);
        return detail == null ? id.toString().substring(0, 8) : detail.name();
    }

    private void fillBackups() {
        Dto.Table table = panel.selectedPlayer == null ? null : ClientState.table("backups", panel.selectedPlayer.toString());
        if (table != null && backups != null) {
            List<Dto.Row> rows = new ArrayList<>();
            for (Dto.Row row : table.rows()) {
                rows.add(new Dto.Row(row.id(), List.of(row.cells().get(0), row.cells().get(1), row.cells().get(2), row.cells().get(3))));
            }
            backups.setRows(rows);
        }
    }

    private void fillProfiles() {
        Dto.Table table = panel.selectedPlayer == null ? null : ClientState.table("profiles", panel.selectedPlayer.toString());
        if (table != null && profiles != null) {
            profiles.setRows(table.rows());
        }
    }

    @Override
    void dataChanged(String what) {
        picker().dataChanged(what);
        if (what.equals("table:backups")) {
            fillBackups();
        } else if (what.equals("table:profiles")) {
            fillProfiles();
        }
    }

    @Override
    void renderBack(Gfx graphics, int mouseX, int mouseY, float partialTick) {
        if (panel.selectedPlayer == null) {
            graphics.drawCenteredString(font, tr("players.select"), (contentX + right()) / 2, top() + 60, Theme.TEXT_OFF);
            return;
        }
        section(graphics, "section.inventory_tools", contentX, top(), right() - contentX);
        int y = top() + 12 + 2 * 20 + 2;
        section(graphics, "section.backups", contentX, y, right() - contentX);
        if (profiles != null) {
            section(graphics, "section.profiles", contentX, split, right() - contentX);
        }
    }
}
