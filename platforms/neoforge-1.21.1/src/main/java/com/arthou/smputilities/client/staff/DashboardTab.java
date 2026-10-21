package com.arthou.smputilities.client.staff;

import com.arthou.smputilities.client.ClientState;
import com.arthou.smputilities.client.gui.SuButton;
import com.arthou.smputilities.client.gui.Theme;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.core.util.Durations;
import com.arthou.smputilities.network.Dto;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Server overview: players, performance, moderation counters and quick chat controls. */
final class DashboardTab extends PanelTab {
    private int cardsBottom;
    private java.util.Map<String, String> lastFlags = java.util.Map.of();

    @Override
    void init() {
        Dto.Dashboard data = ClientState.dashboard();
        lastFlags = data == null ? java.util.Map.of() : data.flags();
        int x = left();
        int width = width();
        cardsBottom = top() + 2 * 40 + 6;
        int y = cardsBottom + 18;
        boolean chat = ClientState.has(Perm.CHAT_MANAGE);
        int toggleWidth = (width - 4) / 2;
        String[][] toggles = {
            {"chat.globalChatEnabled", "toggle.global_chat"},
            {"chat.customFormat", "toggle.custom_format"},
            {"chat.joinMessages", "toggle.join_messages"},
            {"chat.quitMessages", "toggle.quit_messages"},
            {"chat.privateMessagesEnabled", "toggle.private_messages"}
        };
        for (int i = 0; i < toggles.length; i++) {
            String path = toggles[i][0];
            boolean value = data != null && Boolean.parseBoolean(data.flags().getOrDefault(path, "false"));
            var toggle = toggle(x + (i % 2) * (toggleWidth + 4), y + (i / 2) * 22, toggleWidth, toggles[i][1], value, on -> {
                if (path.equals("chat.globalChatEnabled")) {
                    panel.action("chat_lock", "", String.valueOf(!on));
                } else {
                    panel.action("chat_setting", "", path, String.valueOf(on));
                }
            });
            toggle.active = chat;
            toggle.tooltip(tr("toggle." + toggles[i][1].substring("toggle.".length()) + ".tip"));
        }
        int buttonsY = y + 3 * 22 + 4;
        List<SuButton> buttons = new ArrayList<>();
        buttons.add(button(0, 0, 10, "button.clear_chat", Perm.CHAT_MANAGE, b -> panel.confirm(tr("button.clear_chat"), tr("confirm.clear_chat"), true,
            () -> panel.action("chat_clear", ""))).icon("✎"));
        buttons.add(button(0, 0, 10, "button.say", Perm.STAFF_ANNOUNCE, b -> panel.form(tr("button.say"),
            List.of(fieldDef("message", "")), tr("button.send"), false, null, values -> {
                if (!values.get(0).isBlank()) {
                    panel.action("say", "", values.get(0));
                }
            })).icon("✉"));
        buttons.add(button(0, 0, 10, "button.reload", Perm.ADMIN_RELOAD, b -> panel.confirm(tr("button.reload"), tr("confirm.reload"), false,
            () -> panel.action("reload", ""))).icon("↻"));
        grid(x, buttonsY, width, 3, 22, buttons);
    }

    @Override
    void dataChanged(String what) {
        Dto.Dashboard data = ClientState.dashboard();
        // The cards read live data while drawing; widgets only change with the chat flags.
        if (what.equals("dashboard") && data != null && !data.flags().equals(lastFlags)) {
            panel.refreshTab();
        }
    }

    @Override
    void renderBack(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Dto.Dashboard data = ClientState.dashboard();
        int x = left();
        int width = width();
        int cardWidth = (width - 3 * 6) / 4;
        if (data == null) {
            graphics.drawString(font, tr("loading"), x, top() + 4, Theme.TEXT_DIM, false);
            return;
        }
        double memoryPercent = data.memoryMax() > 0 ? data.memoryUsed() * 100.0 / data.memoryMax() : 0;
        card(graphics, x, top(), cardWidth, "card.players", data.online() + " / " + data.max(), Theme.INFO);
        card(graphics, x + (cardWidth + 6), top(), cardWidth, "card.tps", String.format(Locale.ROOT, "%.1f", data.tps()), tpsColor(data.tps()));
        card(graphics, x + 2 * (cardWidth + 6), top(), cardWidth, "card.mspt", String.format(Locale.ROOT, "%.1f ms", data.mspt()), data.mspt() < 40 ? Theme.ACCENT : data.mspt() < 50 ? Theme.WARN : Theme.DANGER);
        card(graphics, x + 3 * (cardWidth + 6), top(), cardWidth, "card.memory",
            String.format(Locale.ROOT, "%d%% · %d MB", Math.round(memoryPercent), data.memoryUsed() / 1048576), memoryPercent < 80 ? Theme.ACCENT : Theme.WARN);
        int y2 = top() + 46;
        card(graphics, x, y2, cardWidth, "card.uptime", Durations.format(data.uptimeMillis()), Theme.TEXT_DIM);
        card(graphics, x + (cardWidth + 6), y2, cardWidth, "card.bans", String.valueOf(data.activeBans()), data.activeBans() > 0 ? Theme.DANGER : Theme.TEXT_DIM);
        card(graphics, x + 2 * (cardWidth + 6), y2, cardWidth, "card.mutes", String.valueOf(data.activeMutes()), data.activeMutes() > 0 ? Theme.WARN : Theme.TEXT_DIM);
        card(graphics, x + 3 * (cardWidth + 6), y2, cardWidth, "card.version", data.version(), Theme.TEXT_DIM);
        section(graphics, "section.quick", x, cardsBottom + 4, width);

        // online staff and vanished counters under the buttons
        int staff = 0;
        int vanished = 0;
        for (Dto.PlayerRow row : data.players()) {
            if (row.staff()) {
                staff++;
            }
            if (row.vanished()) {
                vanished++;
            }
        }
        Component footer = tr("dashboard.footer", staff, vanished, data.flags().getOrDefault("voice", "false").equals("true")
            ? tr("dashboard.voice_on") : tr("dashboard.voice_off"));
        graphics.drawString(font, footer, x, bottom() - 9, Theme.TEXT_OFF, false);
    }

    private void card(GuiGraphics graphics, int x, int y, int width, String key, String value, int color) {
        Theme.roundBox(graphics, x, y, width, 40, Theme.CARD, Theme.BORDER);
        graphics.fill(x + 1, y + 4, x + 3, y + 36, color);
        graphics.drawString(font, Theme.clip(font, tr(key), width - 14), x + 9, y + 7, Theme.TEXT_DIM, false);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 9, y + 20, 0);
        float scale = font.width(value) * 1.5F > width - 14 ? 1.0F : 1.5F;
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, Component.literal(Theme.clip(font, value, (int) ((width - 14) / scale))).withStyle(ChatFormatting.BOLD), 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static int tpsColor(double tps) {
        return tps >= 18 ? Theme.ACCENT : tps >= 14 ? Theme.WARN : Theme.DANGER;
    }
}
