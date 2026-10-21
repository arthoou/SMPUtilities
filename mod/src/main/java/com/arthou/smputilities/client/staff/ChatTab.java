package com.arthou.smputilities.client.staff;

import com.arthou.smputilities.client.ClientState;
import com.arthou.smputilities.client.gui.SuButton;
import com.arthou.smputilities.client.gui.SuTextField;
import com.arthou.smputilities.client.gui.TableView;
import com.arthou.smputilities.client.gui.Theme;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.network.Dto;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Chat settings, moderation and announcements. */
final class ChatTab extends PanelTab {
    private TableView mutes;
    private Map<String, String> lastFlags = Map.of();

    @Override
    void opened() {
        panel.request("table", "mutes|");
        if (ClientState.has(Perm.ADMIN_SETTINGS)) {
            panel.request("settings", "");
        }
    }

    @Override
    void refresh() {
        panel.request("table", "mutes|");
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
        Dto.Dashboard dashboard = ClientState.dashboard();
        lastFlags = dashboard == null ? Map.of() : dashboard.flags();
        int x = left();
        int half = (width() - 6) / 2;
        int y = top() + 12;
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
        if (what.equals("table:mutes")) {
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
    void renderBack(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        section(graphics, "section.chat_options", left(), top(), width());
        int y = top() + 12 + 5 * 21 + 4 + 21 + 2 + 26;
        section(graphics, "section.muted", left(), y, width());
    }
}
