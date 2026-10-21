package com.arthou.smputilities.client.staff;

import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.client.ClientState;
import com.arthou.smputilities.client.gui.Dialog;
import com.arthou.smputilities.client.gui.SuButton;
import com.arthou.smputilities.client.gui.SuScreen;
import com.arthou.smputilities.client.gui.Theme;
import com.arthou.smputilities.core.perm.Perm;
import com.arthou.smputilities.client.ClientNetwork;
import com.arthou.smputilities.network.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The staff panel opened with /su staff. A sidebar switches between
 * categories; each category is a {@link PanelTab}. All data comes from the
 * server on request, and every button sends an action the server re-checks.
 */
public final class StaffPanelScreen extends SuScreen implements ClientState.Listener {
    record TabInfo(String id, String icon, Supplier<PanelTab> factory, Perm perm) {
    }

    private static final List<TabInfo> TABS = List.of(
        new TabInfo("dashboard", "◈", DashboardTab::new, Perm.STAFF_PANEL),
        new TabInfo("players", "☺", PlayersTab::new, Perm.STAFF_PANEL),
        new TabInfo("chat", "✉", ChatTab::new, Perm.CHAT_MANAGE),
        new TabInfo("inventories", "▤", InventoriesTab::new, Perm.STAFF_INVENTORY),
        new TabInfo("punishments", "⚖", PunishmentsTab::new, Perm.STAFF_HISTORY),
        new TabInfo("world", "◎", WorldTab::new, Perm.STAFF_PANEL),
        new TabInfo("utilities", "✦", UtilitiesTab::new, Perm.STAFF_PANEL),
        new TabInfo("settings", "⚙", SettingsTab::new, Perm.ADMIN_SETTINGS));

    private static final int SIDEBAR = 116;
    private static final long REFRESH_MILLIS = 2000;

    int panelLeft;
    int panelTop;
    int panelWidth;
    int panelHeight;
    private String tabId;
    private PanelTab tab;
    /** Player shared by the Players, Inventories and Utilities tabs. */
    UUID selectedPlayer;
    private long lastRefresh;
    private long tabSwitchedAt;
    private float indicatorY = -1;
    private long lastFrame = Util.getMillis();

    public StaffPanelScreen(String tab, String target) {
        super(Texts.translatable("panel.smputilities.title"));
        this.tabId = tab == null || tab.isEmpty() ? "dashboard" : tab;
        if (target != null && !target.isEmpty()) {
            try {
                selectedPlayer = UUID.fromString(target);
                this.tabId = "players";
            } catch (IllegalArgumentException ignored) {
                // keep the requested tab
            }
        }
    }

    // ------------------------------------------------------------ network

    void request(String kind, String argument) {
        ClientNetwork.send(new Payloads.Request(kind, argument == null ? "" : argument));
    }

    void action(String action, UUID target, String... arguments) {
        action(action, target == null ? "" : target.toString(), arguments);
    }

    void action(String action, String target, String... arguments) {
        ClientNetwork.send(new Payloads.Action(action, target == null ? "" : target, Arrays.asList(arguments)));
    }

    // ------------------------------------------------------------ dialogs

    void confirm(Component title, Component message, boolean danger, Runnable onConfirm) {
        openDialog(new Dialog.Confirm(title, message, Texts.translatable(danger ? "gui.smputilities.confirm_danger" : "gui.smputilities.confirm"), danger, onConfirm));
    }

    void form(Component title, List<Dialog.Form.Field> fields, Component submit, boolean danger, Component note, Consumer<List<String>> onSubmit) {
        openDialog(new Dialog.Form(title, fields, submit, danger, note, onSubmit));
    }

    void choice(Component title, List<Dialog.Choice.Option> options, Consumer<String> onChoose) {
        openDialog(new Dialog.Choice(title, options, onChoose));
    }

    // ------------------------------------------------------------- layout

    int contentLeft() {
        return panelLeft + SIDEBAR + 12;
    }

    int contentRight() {
        return panelLeft + panelWidth - 12;
    }

    int contentTop() {
        return panelTop + 42;
    }

    int contentBottom() {
        return panelTop + panelHeight - 10;
    }

    <T extends AbstractWidget> T add(T widget) {
        return addRenderableWidget(widget);
    }

    void switchTab(String id) {
        if (!id.equals(tabId)) {
            tabId = id;
            tabSwitchedAt = Util.getMillis();
            tab = null;
            rebuildWidgets();
        }
    }

    void refreshTab() {
        rebuildWidgets();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 16, 580);
        panelHeight = Math.min(height - 16, 330);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;

        TabInfo current = null;
        for (TabInfo info : TABS) {
            if (info.id().equals(tabId) && ClientState.has(info.perm())) {
                current = info;
            }
        }
        if (current == null) {
            current = TABS.get(0);
            tabId = current.id();
        }

        // sidebar
        int available = panelHeight - 44 - 52;
        int itemHeight = Math.max(15, Math.min(22, available / TABS.size()));
        int y = panelTop + 44;
        for (TabInfo info : TABS) {
            boolean allowed = ClientState.has(info.perm());
            NavItem item = add(new NavItem(panelLeft + 6, y, SIDEBAR - 12, itemHeight - 2, info, this));
            item.active = allowed;
            if (!allowed) {
                item.setTooltip(Tooltip.create(Texts.translatable("panel.smputilities.no_permission")));
            }
            if (info.id().equals(tabId) && indicatorY < 0) {
                indicatorY = y;
            }
            y += itemHeight;
        }
        int bottom = panelTop + panelHeight - 8;
        if (ClientState.has(Perm.STAFF_VANISH)) {
            boolean vanished = minecraft != null && minecraft.player != null && ClientState.isVanished(minecraft.player.getUUID());
            add(new SuButton(panelLeft + 6, bottom - 40, SIDEBAR - 12, 18,
                Texts.translatable(vanished ? "panel.smputilities.vanish_off" : "panel.smputilities.vanish_on"),
                button -> action("vanish_self", "")).kind(vanished ? SuButton.Kind.PRIMARY : SuButton.Kind.GHOST).icon("◐"));
        }
        if (ClientState.has(Perm.STAFF_REALNAMES)) {
            add(new SuButton(panelLeft + 6, bottom - 20, SIDEBAR - 12, 18,
                Texts.translatable(ClientState.realNames() ? "panel.smputilities.realnames_off" : "panel.smputilities.realnames_on"),
                button -> action("realnames", "", String.valueOf(!ClientState.realNames()))).kind(ClientState.realNames() ? SuButton.Kind.PRIMARY : SuButton.Kind.GHOST).icon("⚑"));
        }

        if (tab == null || !tab.id().equals(tabId)) {
            tab = current.factory().get();
            tab.attach(this, current.id());
            tab.opened();
        }
        tab.init();
    }

    @Override
    public void added() {
        super.added();
        ClientState.listen(this);
        request("state", "");
        request("dashboard", "");
        lastRefresh = Util.getMillis();
    }

    @Override
    public void removed() {
        ClientState.unlisten(this);
        super.removed();
    }

    @Override
    public void changed(String what) {
        if (minecraft != null) {
            minecraft.execute(() -> {
                if (what.equals("state")) {
                    rebuildWidgets();
                } else if (tab != null) {
                    tab.dataChanged(what);
                }
            });
        }
    }

    @Override
    public void tick() {
        super.tick();
        long now = Util.getMillis();
        if (now - lastRefresh >= REFRESH_MILLIS) {
            lastRefresh = now;
            request("dashboard", "");
            if (tab != null) {
                tab.refresh();
            }
        }
        if (tab != null) {
            tab.tick();
        }
    }

    // ------------------------------------------------------------ drawing

    /** 0 right after a tab switch, 1 when the fade-in is done. */
    float fadeIn() {
        return Math.min(1, (Util.getMillis() - tabSwitchedAt) / 160.0F);
    }

    float targetIndicator() {
        int available = panelHeight - 44 - 52;
        int itemHeight = Math.max(15, Math.min(22, available / TABS.size()));
        for (int i = 0; i < TABS.size(); i++) {
            if (TABS.get(i).id().equals(tabId)) {
                return panelTop + 44 + i * itemHeight;
            }
        }
        return panelTop + 44;
    }

    boolean isCurrent(TabInfo info) {
        return info.id().equals(tabId);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        long now = Util.getMillis();
        indicatorY = Theme.approach(indicatorY < 0 ? targetIndicator() : indicatorY, targetIndicator(), now - lastFrame, 70);
        lastFrame = now;

        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;
        Theme.shadow(graphics, panelLeft, panelTop, right, bottom);
        Theme.roundRect(graphics, panelLeft - 1, panelTop - 1, right + 1, bottom + 1, Theme.BORDER);
        Theme.roundRect(graphics, panelLeft, panelTop, right, bottom, Theme.PANEL_BG);
        graphics.fill(panelLeft + 1, panelTop, panelLeft + SIDEBAR, panelTop + 1, Theme.SIDEBAR_BG);
        graphics.fill(panelLeft, panelTop + 1, panelLeft + SIDEBAR, bottom - 1, Theme.SIDEBAR_BG);
        graphics.fill(panelLeft + 1, bottom - 1, panelLeft + SIDEBAR, bottom, Theme.SIDEBAR_BG);
        graphics.fill(panelLeft + SIDEBAR, panelTop, panelLeft + SIDEBAR + 1, bottom, Theme.BORDER);
        graphics.fillGradient(panelLeft + SIDEBAR + 1, panelTop, right - 1, panelTop + 40, 0x1A6AF06B, 0x006AF06B);

        // logo
        int logoX = panelLeft + 10;
        int logoY = panelTop + 12;
        Theme.roundRect(graphics, logoX, logoY, logoX + 20, logoY + 20, Theme.ACCENT);
        graphics.drawString(font, "SU", logoX + 4, logoY + 6, Theme.ACCENT_TEXT, false);
        graphics.drawString(font, Texts.literal("SMPutilities").withStyle(ChatFormatting.BOLD), logoX + 26, logoY + 2, Theme.TEXT, false);
        graphics.drawString(font, Texts.translatable("panel.smputilities.subtitle"), logoX + 26, logoY + 12, Theme.TEXT_DIM, false);

        // selection indicator
        int available = panelHeight - 44 - 52;
        int itemHeight = Math.max(15, Math.min(22, available / TABS.size()));
        Theme.roundRect(graphics, panelLeft + 6, Math.round(indicatorY), panelLeft + SIDEBAR - 6, Math.round(indicatorY) + itemHeight - 2, Theme.CARD);
        graphics.fill(panelLeft + 6, Math.round(indicatorY) + 3, panelLeft + 8, Math.round(indicatorY) + itemHeight - 5, Theme.ACCENT);

        // header of the current tab
        Component header = Texts.translatable("panel.smputilities.tab." + tabId);
        graphics.drawString(font, header.copy().withStyle(ChatFormatting.BOLD), contentLeft(), panelTop + 12, Theme.TEXT, false);
        graphics.drawString(font, Texts.translatable("panel.smputilities.tab." + tabId + ".hint"), contentLeft(), panelTop + 24, Theme.TEXT_DIM, false);
        renderStatus(graphics);
        graphics.fill(contentLeft(), panelTop + 37, contentRight(), panelTop + 38, Theme.BORDER);

        if (tab != null) {
            tab.renderBack(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderStatus(GuiGraphics graphics) {
        var dashboard = ClientState.dashboard();
        if (dashboard == null) {
            return;
        }
        String text = dashboard.online() + "/" + dashboard.max() + " · " + String.format(java.util.Locale.ROOT, "%.1f TPS", dashboard.tps());
        int color = dashboard.tps() >= 18 ? Theme.ACCENT : dashboard.tps() >= 14 ? Theme.WARN : Theme.DANGER;
        int width = font.width(text) + 16;
        Theme.pill(graphics, font, contentRight() - width, panelTop + 10, text, color);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (tab != null && !hasDialog()) {
            tab.renderFront(graphics, mouseX, mouseY, partialTick);
        }
        float fade = fadeIn();
        if (fade < 1 && !hasDialog()) {
            // Short fade between categories.
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.fill(contentLeft() - 4, panelTop + 39, contentRight() + 4, contentBottom() + 4, Theme.withAlpha(0xFF14181F, 1 - fade));
            graphics.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!hasDialog() && tab != null && tab.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    static List<TabInfo> tabs() {
        return TABS;
    }
}
