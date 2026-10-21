package com.arthou.smputilities.client.staff;

import com.arthou.smputilities.client.ClientState;
import com.arthou.smputilities.client.gui.Heads;
import com.arthou.smputilities.client.gui.SuButton;
import com.arthou.smputilities.client.gui.SuTextField;
import com.arthou.smputilities.client.gui.TableView;
import com.arthou.smputilities.client.gui.Theme;
import com.arthou.smputilities.core.chat.StyledText;
import com.arthou.smputilities.network.Dto;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Compact player list shared by tabs that act on one player. */
final class PlayerPicker {
    private static String search = "";
    private static boolean offline;
    private final StaffPanelScreen panel;
    private final boolean allowOffline;
    private TableView list;

    PlayerPicker(StaffPanelScreen panel, boolean allowOffline) {
        this.panel = panel;
        this.allowOffline = allowOffline;
    }

    boolean offline() {
        return allowOffline && offline;
    }

    /** @return x where the tab's own content starts */
    int build(int left, int top, int bottom, int width, Runnable onChange) {
        int fieldWidth = allowOffline ? width - 44 : width;
        SuTextField field = panel.add(new SuTextField(left, top, fieldWidth, 18, Component.translatable("panel.smputilities.search")));
        field.setValue(search);
        field.setResponder(value -> {
            search = value;
            if (list != null) {
                list.setFilter(value);
            }
        });
        if (allowOffline) {
            panel.add(new SuButton(left + width - 40, top, 40, 18, Component.translatable(offline() ? "panel.smputilities.list.online" : "panel.smputilities.list.offline"), button -> {
                offline = !offline;
                if (offline) {
                    panel.request("table", "known|");
                }
                panel.refreshTab();
            }).kind(offline() ? SuButton.Kind.PRIMARY : SuButton.Kind.GHOST));
        }
        list = panel.add(new TableView(left, top + 24, width, bottom - top - 24, List.of()));
        list.rowHeight = 18;
        list.remember("picker." + offline());
        list.emptyText = Component.translatable("panel.smputilities.list.empty_online");
        list.selectedId = panel.selectedPlayer == null ? "" : panel.selectedPlayer.toString();
        Font font = Minecraft.getInstance().font;
        list.rowRenderer = (graphics, row, x, y, w, h, hovered, selected) -> {
            UUID id = UUID.fromString(row.id());
            Heads.draw(graphics, id, row.cells().get(0), x + 2, y + 3, 12);
            String nick = row.cells().size() > 1 ? row.cells().get(1) : "";
            String text = nick.isEmpty() ? row.cells().get(0) : ClientState.realNames() ? nick + " (" + row.cells().get(0) + ")" : nick;
            graphics.drawString(font, Theme.clip(font, text, w - 20), x + 18, y + 5, selected ? Theme.TEXT : Theme.TEXT_DIM, false);
            return true;
        };
        list.onSelect = row -> {
            panel.selectedPlayer = UUID.fromString(row.id());
            onChange.run();
        };
        refresh();
        list.setFilter(search);
        return left + width + 8;
    }

    void refresh() {
        if (list == null) {
            return;
        }
        List<Dto.Row> rows = new ArrayList<>();
        if (offline()) {
            Dto.Table known = ClientState.table("known", "");
            if (known != null) {
                rows.addAll(known.rows());
            }
        } else if (ClientState.dashboard() != null) {
            for (Dto.PlayerRow player : ClientState.dashboard().players()) {
                rows.add(new Dto.Row(player.id().toString(), List.of(player.name(), StyledText.strip(player.nick()))));
            }
        }
        list.setRows(rows);
    }

    void dataChanged(String what) {
        if (what.equals("dashboard") && !offline() || what.equals("table:known") && offline()) {
            refresh();
        }
    }
}
