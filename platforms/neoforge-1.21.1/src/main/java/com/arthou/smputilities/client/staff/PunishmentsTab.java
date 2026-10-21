package com.arthou.smputilities.client.staff;

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

/** Active bans and mutes, the full punishment history and the administrative log. */
final class PunishmentsTab extends PanelTab {
    enum View { BANS, MUTES, HISTORY, LOGS }

    private static View view = View.BANS;
    private static String filter = "";
    private TableView table;

    private String kind() {
        return switch (view) {
            case BANS -> "bans";
            case MUTES -> "mutes";
            case HISTORY -> "history";
            case LOGS -> "logs";
        };
    }

    @Override
    void opened() {
        panel.request("table", kind() + "|");
    }

    @Override
    void refresh() {
        panel.request("table", kind() + "|");
    }

    @Override
    void init() {
        int x = left();
        View[] views = View.values();
        int tabWidth = Math.min(90, (width() - 160) / views.length);
        for (int i = 0; i < views.length; i++) {
            View value = views[i];
            SuButton button = panel.add(new SuButton(x + i * (tabWidth + 3), top(), tabWidth, 18, tr("punish.view." + value.name().toLowerCase(Locale.ROOT)), b -> {
                view = value;
                panel.request("table", kind() + "|");
                panel.refreshTab();
            }).kind(view == value ? SuButton.Kind.PRIMARY : SuButton.Kind.SUBTLE));
            button.active = value != View.LOGS || ClientState.has(Perm.STAFF_LOGS);
        }
        SuTextField search = field(right() - 150, top(), 150, "search");
        search.setValue(filter);
        search.setResponder(value -> {
            filter = value;
            if (table != null) {
                table.setFilter(value);
            }
        });

        List<TableView.Column> columns = switch (view) {
            case BANS, MUTES -> List.of(column("player", 1.1F), column("type", 0.8F), column("since", 1.1F), column("remaining", 0.8F),
                column("by", 0.8F), column("reason", 1.6F));
            case HISTORY -> List.of(column("id", 0.6F), column("type", 0.8F), column("player", 1), column("date", 1.1F), column("by", 0.8F),
                column("reason", 1.4F), column("status", 1));
            case LOGS -> List.of(column("date", 1.1F), column("by", 0.9F), column("action", 1.1F), column("target", 1), column("details", 2));
        };
        table = table(x, top() + 24, width(), bottom() - top() - 24, columns);
        table.remember("punish." + view);
        table.emptyText = tr("empty");
        fill();
        table.setFilter(filter);
        boolean canRevoke = ClientState.has(Perm.STAFF_BAN);
        table.buttons = row -> switch (view) {
            case BANS -> ClientState.has(Perm.STAFF_BAN) ? List.of(new TableView.RowButton("unban", tr("action.unban"), Theme.ACCENT)) : List.of();
            case MUTES -> ClientState.has(Perm.STAFF_MUTE) ? List.of(new TableView.RowButton("unmute", tr("action.unmute"), Theme.ACCENT)) : List.of();
            default -> List.of();
        };
        table.onButton = (row, button) -> {
            if (button.equals("unban")) {
                panel.confirm(tr("action.unban"), tr("confirm.unban", row.cells().get(0)), false, () -> {
                    panel.action("unban", row.id());
                    panel.request("table", "bans|");
                });
            } else if (button.equals("unmute")) {
                panel.confirm(tr("action.unmute"), tr("confirm.unmute", row.cells().get(0)), false, () -> {
                    panel.action(row.cells().get(1).equals("VOICE_MUTE") ? "voice_unmute" : "unmute", row.id());
                    panel.request("table", "mutes|");
                });
            }
        };
        if (!canRevoke) {
            table.buttons = row -> view == View.MUTES && ClientState.has(Perm.STAFF_MUTE)
                ? List.of(new TableView.RowButton("unmute", tr("action.unmute"), Theme.ACCENT)) : List.of();
        }
    }

    private void fill() {
        Dto.Table data = ClientState.table(kind(), "");
        if (data != null && table != null) {
            table.setRows(new ArrayList<>(data.rows()));
        }
    }

    @Override
    void dataChanged(String what) {
        if (what.equals("table:" + kind())) {
            fill();
        }
    }
}
