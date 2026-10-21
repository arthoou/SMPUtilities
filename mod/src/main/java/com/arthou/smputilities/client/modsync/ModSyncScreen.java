package com.arthou.smputilities.client.modsync;

import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.client.ClientConfig;
import com.arthou.smputilities.client.gui.SuButton;
import com.arthou.smputilities.client.gui.SuScreen;
import com.arthou.smputilities.client.gui.Theme;
import com.arthou.smputilities.core.modsync.ModDownloader;
import com.arthou.smputilities.core.modsync.ModEntry;
import com.arthou.smputilities.core.modsync.ModManifest;
import com.arthou.smputilities.core.modsync.SyncPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Lists what the server wants changed in the mods folder and applies it on request. */
public final class ModSyncScreen extends SuScreen {
    private final ModManifest manifest;
    private final SyncPlan plan;
    private final Set<String> allowedHosts;
    private final long maxBytes;
    private ModDownloader downloader;
    private volatile String currentFile = "";
    private volatile long done;
    private volatile long total;
    private volatile int fileIndex;
    private volatile int fileCount;
    private volatile ModDownloader.Result result;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private float scroll;

    ModSyncScreen(ModManifest manifest, SyncPlan plan, Set<String> allowedHosts, long maxBytes) {
        super(Texts.translatable("modsync.smputilities.title"));
        this.manifest = manifest;
        this.plan = plan;
        this.allowedHosts = allowedHosts;
        this.maxBytes = maxBytes;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(420, width - 20);
        panelHeight = Math.min(260, height - 20);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        int y = top + panelHeight - 28;
        int buttonWidth = (panelWidth - 32) / 3;
        if (result != null) {
            addRenderableWidget(new SuButton(left + panelWidth - 8 - buttonWidth, y, buttonWidth, 20,
                Texts.translatable("modsync.smputilities.close"), button -> onClose()).kind(SuButton.Kind.PRIMARY));
            return;
        }
        if (downloader != null) {
            addRenderableWidget(new SuButton(left + panelWidth - 8 - buttonWidth, y, buttonWidth, 20,
                Texts.translatable("modsync.smputilities.cancel_download"), button -> downloader.cancel()).kind(SuButton.Kind.DANGER));
            return;
        }
        addRenderableWidget(new SuButton(left + 8, y, buttonWidth, 20, Texts.translatable("modsync.smputilities.never"), button -> {
            ClientConfig.get().modSync.prompt = false;
            ClientConfig.save();
            onClose();
        }).kind(SuButton.Kind.SUBTLE).tooltip(Texts.translatable("modsync.smputilities.never_tip")));
        addRenderableWidget(new SuButton(left + 16 + buttonWidth, y, buttonWidth, 20, Texts.translatable("modsync.smputilities.later"),
            button -> onClose()));
        addRenderableWidget(new SuButton(left + panelWidth - 8 - buttonWidth, y, buttonWidth, 20, Texts.translatable("modsync.smputilities.update"),
            button -> start()).kind(SuButton.Kind.PRIMARY).enabled(!plan.upToDate()));
    }

    private void start() {
        Set<String> managed = new LinkedHashSet<>(ClientConfig.get().modSync.managed);
        downloader = new ModDownloader();
        downloader.start(manifest, plan, ClientModSync.modsDir(), ClientModSync.stateDir(), ClientConfig.get().modSync.credentials,
            managed, maxBytes, new ModDownloader.Listener() {
                @Override
                public void progress(String fileName, long bytes, long size, int index, int count) {
                    currentFile = fileName;
                    done = bytes;
                    total = size;
                    fileIndex = index;
                    fileCount = count;
                }

                @Override
                public void finished(ModDownloader.Result value) {
                    Minecraft.getInstance().execute(() -> {
                        result = value;
                        ClientConfig.get().modSync.managed = new ArrayList<>(managed);
                        ClientConfig.save();
                        rebuildWidgets();
                    });
                }
            });
        rebuildWidgets();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        Theme.shadow(graphics, left, top, left + panelWidth, top + panelHeight);
        Theme.roundBox(graphics, left, top, panelWidth, panelHeight, Theme.PANEL_BG, Theme.BORDER);
        graphics.fillGradient(left + 1, top + 1, left + panelWidth - 1, top + 40, 0x1A6AF06B, 0x006AF06B);
        graphics.drawString(font, title, left + 10, top + 10, Theme.TEXT, false);
        graphics.drawString(font, Texts.translatable("modsync.smputilities.subtitle"), left + 10, top + 22, Theme.TEXT_DIM, false);

        int listTop = top + 44;
        int listBottom = top + panelHeight - 36;
        if (result != null) {
            renderResult(graphics, listTop);
            return;
        }
        if (downloader != null) {
            graphics.drawString(font, Texts.translatable("modsync.smputilities.downloading", fileIndex, fileCount, currentFile), left + 10, listTop + 4, Theme.TEXT, false);
            int barWidth = panelWidth - 20;
            Theme.roundRect(graphics, left + 10, listTop + 20, left + 10 + barWidth, listTop + 30, Theme.CARD);
            float progress = total > 0 ? Math.min(1, done / (float) total) : 0;
            Theme.roundRect(graphics, left + 10, listTop + 20, left + 10 + Math.max(2, Math.round(barWidth * progress)), listTop + 30, Theme.ACCENT);
            return;
        }
        List<String> lines = new ArrayList<>();
        for (ModEntry entry : plan.download()) {
            lines.add("+ " + entry.fileName() + "  (" + host(entry) + ", " + size(entry.size()) + ")");
        }
        for (String name : plan.remove()) {
            lines.add("- " + name);
        }
        for (String rejected : plan.rejected()) {
            lines.add("! " + rejected);
        }
        if (lines.isEmpty()) {
            graphics.drawString(font, Texts.translatable("modsync.smputilities.up_to_date"), left + 10, listTop + 4, Theme.ACCENT, false);
            return;
        }
        graphics.enableScissor(left + 6, listTop, left + panelWidth - 6, listBottom);
        int y = listTop + 2 - Math.round(scroll);
        for (String line : lines) {
            int color = line.startsWith("+") ? Theme.ACCENT : line.startsWith("-") ? Theme.WARN : Theme.DANGER;
            graphics.drawString(font, Theme.clip(font, line, panelWidth - 24), left + 10, y, color, false);
            y += 11;
        }
        graphics.disableScissor();
        graphics.drawString(font, Texts.translatable("modsync.smputilities.restart_note"), left + 10, listBottom + 1, Theme.TEXT_OFF, false);
    }

    private void renderResult(GuiGraphics graphics, int y) {
        ModDownloader.Result value = result;
        int line = y + 4;
        if (value.cancelled()) {
            graphics.drawString(font, Texts.translatable("modsync.smputilities.cancelled"), left + 10, line, Theme.WARN, false);
            line += 12;
        }
        graphics.drawString(font, Texts.translatable("modsync.smputilities.installed", value.installed().size(), value.removed().size()), left + 10, line, Theme.ACCENT, false);
        line += 12;
        for (String failure : value.failed()) {
            graphics.drawString(font, Theme.clip(font, "✘ " + failure, panelWidth - 24), left + 10, line, Theme.DANGER, false);
            line += 11;
        }
        for (String manual : value.manual()) {
            graphics.drawString(font, Theme.clip(font, "⚠ " + manual, panelWidth - 24), left + 10, line, Theme.WARN, false);
            line += 11;
        }
        if (value.restartRequired()) {
            graphics.drawString(font, Texts.translatable("modsync.smputilities.restart_required"), left + 10, line + 6, Theme.WARN, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (float) scrollY * 22);
        return true;
    }

    private static String host(ModEntry entry) {
        if (entry.hosted()) {
            return Texts.translatable("modsync.smputilities.server_host").getString();
        }
        try {
            return URI.create(entry.url()).getHost();
        } catch (IllegalArgumentException e) {
            return "?";
        }
    }

    private static String size(long bytes) {
        if (bytes <= 0) {
            return "?";
        }
        return bytes < 1024 * 1024 ? (bytes / 1024) + " KB" : String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1048576.0);
    }
}
