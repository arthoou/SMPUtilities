package com.arthou.smputilities.client.staff;

import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.client.ClientState;
import com.arthou.smputilities.client.gui.SuButton;
import com.arthou.smputilities.client.gui.SuTextField;
import com.arthou.smputilities.client.gui.TableView;
import com.arthou.smputilities.client.gui.Theme;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.network.Dto;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;

/** Every value of config/smputilities/server.json, editable live. */
final class SettingsTab extends PanelTab {
    private static String filter = "";
    private TableView table;

    @Override
    void opened() {
        panel.request("settings", "");
    }

    @Override
    void init() {
        SuTextField search = field(left(), top(), width() - 98, "search_settings");
        search.setValue(filter);
        search.setResponder(value -> {
            filter = value;
            if (table != null) {
                table.setFilter(value);
            }
        });
        panel.add(new SuButton(right() - 92, top(), 92, 18, tr("button.reload"), b -> panel.confirm(tr("button.reload"), tr("confirm.reload"), false,
            () -> panel.action("reload", ""))).enabled(ClientState.has(Perm.ADMIN_RELOAD)).icon("↻"));
        table = table(left(), top() + 24, width(), bottom() - top() - 24, List.of(column("setting", 2.2F), column("value", 1.4F)));
        table.remember("settings");
        table.emptyText = tr("loading");
        table.buttons = row -> List.of(row.cells().get(2).equals("BOOLEAN")
            ? new TableView.RowButton("toggle", tr("button.toggle"), Theme.ACCENT)
            : new TableView.RowButton("edit", tr("button.edit"), Theme.INFO));
        table.onButton = (row, button) -> {
            String path = row.id();
            if (button.equals("toggle")) {
                panel.action("setting", "", path, String.valueOf(!Boolean.parseBoolean(row.cells().get(1))));
            } else {
                panel.form(tr("settings.edit"), List.of(new com.arthou.smputilities.client.gui.Dialog.Form.Field(
                        Texts.literal(path), row.cells().get(1), tr("field.value.hint"))),
                    tr("button.save"), false, describe(path), values -> panel.action("setting", "", path, values.get(0)));
            }
        };
        fill();
        table.setFilter(filter);
    }

    private static net.minecraft.network.chat.Component describe(String path) {
        String key = "setting.smputilities." + path;
        return I18n.exists(key) ? Texts.translatable(key) : null;
    }

    private void fill() {
        if (table == null) {
            return;
        }
        List<Dto.Row> rows = new ArrayList<>();
        for (Dto.Setting setting : ClientState.settings()) {
            String label = setting.path();
            String key = "setting.smputilities." + setting.path();
            if (I18n.exists(key)) {
                label = setting.path() + " · " + I18n.get(key);
            }
            rows.add(new Dto.Row(setting.path(), List.of(label, setting.value(), setting.kind())));
        }
        table.setRows(rows);
    }

    @Override
    void dataChanged(String what) {
        if (what.equals("settings")) {
            fill();
        }
    }
}
