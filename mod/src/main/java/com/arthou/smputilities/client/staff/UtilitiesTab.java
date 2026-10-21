package com.arthou.smputilities.client.staff;

import com.arthou.smputilities.client.gui.Gfx;
import com.arthou.smputilities.client.ClientState;
import com.arthou.smputilities.client.gui.SuButton;
import com.arthou.smputilities.client.gui.TableView;
import com.arthou.smputilities.client.gui.Theme;
import com.arthou.smputilities.client.modsync.ClientModSync;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.network.Dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Staff pranks, announcements, warps and mod sync. */
final class UtilitiesTab extends PanelTab {
    private PlayerPicker picker;
    private TableView warps;
    private int contentX;
    private int trollBottom;

    @Override
    void opened() {
        panel.request("table", "warps|");
    }

    @Override
    void init() {
        if (picker == null) {
            picker = new PlayerPicker(panel, false);
        }
        int pickerWidth = Math.max(110, (int) (width() * 0.28));
        contentX = picker.build(left(), top(), bottom(), pickerWidth, panel::refreshTab);
        int width = right() - contentX;
        UUID target = panel.selectedPlayer;
        boolean troll = ClientState.has(Perm.STAFF_TROLL);
        Pranks.chips(panel, contentX, top() + 12, width);
        List<SuButton> buttons = Pranks.buttons(id -> {
            SuButton button = button(0, 0, 10, "troll." + id, b -> Pranks.run(panel, target, id));
            button.active = troll && target != null;
            return button;
        });
        trollBottom = grid(contentX, top() + 32, width, width > 300 ? 4 : 3, 20, buttons);

        int y = trollBottom + 14;
        List<SuButton> announce = new ArrayList<>();
        announce.add(button(0, 0, 10, "button.say", Perm.STAFF_ANNOUNCE, b -> panel.form(tr("button.say"), List.of(fieldDef("message", "")), tr("button.send"),
            false, null, values -> panel.action("say", "", values.get(0)))).icon("✉"));
        announce.add(button(0, 0, 10, "button.title_action", Perm.STAFF_ANNOUNCE, b -> panel.form(tr("button.title_action"), List.of(fieldDef("subtitle", "")),
            tr("button.send"), false, tr("title_action.note"), values -> panel.action("announce_title", "", "title_action", values.get(0)))).icon("⚡"));
        announce.add(button(0, 0, 10, "button.title_death", Perm.STAFF_ANNOUNCE, b -> panel.form(tr("button.title_death"),
            List.of(fieldDef("name", ""), fieldDef("color", "red")), tr("button.send"), false, null,
            values -> panel.action("announce_title", "", "title_death", values.get(0).trim() + " " + values.get(1).trim()))).icon("☠"));
        y = grid(contentX, y, width, 3, 20, announce) + 14;

        List<SuButton> sync = new ArrayList<>();
        sync.add(button(0, 0, 10, "button.modsync_rescan", Perm.MODSYNC_MANAGE, b -> panel.action("modsync_rescan", "")).icon("↻"));
        SuButton open = button(0, 0, 10, "button.modsync_open", b -> ClientModSync.openScreen());
        open.active = ClientModSync.hasUpdates();
        sync.add(open.icon("⬇"));
        y = grid(contentX, y, width, 2, 20, sync) + 14;

        warps = table(contentX, y, width, bottom() - y, List.of(column("name", 1), column("location", 2)));
        warps.emptyText = tr("warps.empty");
        fillWarps();
    }

    private void fillWarps() {
        Dto.Table table = ClientState.table("warps", "");
        if (table != null && warps != null) {
            warps.setRows(table.rows());
        }
    }

    @Override
    void dataChanged(String what) {
        if (picker != null) {
            picker.dataChanged(what);
        }
        if (what.equals("table:warps")) {
            fillWarps();
        }
    }

    @Override
    void renderBack(Gfx graphics, int mouseX, int mouseY, float partialTick) {
        int width = right() - contentX;
        section(graphics, "section.troll", contentX, top(), width);
        if (panel.selectedPlayer == null) {
            graphics.drawString(font, tr("troll.pick"), contentX + width - font.width(tr("troll.pick")), top(), Theme.TEXT_OFF, false);
        }
        int y = trollBottom + 2;
        section(graphics, "section.announcements", contentX, y, width);
        section(graphics, "section.modsync", contentX, y + 20 + 14, width);
        section(graphics, "section.warps", contentX, y + 2 * (20 + 14), width);
    }
}
