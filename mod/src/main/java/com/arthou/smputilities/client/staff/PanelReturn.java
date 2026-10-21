package com.arthou.smputilities.client.staff;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.Set;
import java.util.UUID;

/**
 * Brings the staff panel back when a menu it opened (inventory, ender chest,
 * curios, backups, profiles) is closed with Esc, on the same tab and player.
 * The server replaces one menu with the next by closing it first, so a
 * menu counts as closed only after a few ticks without one.
 */
public final class PanelReturn {
    private static final Set<String> MENUS = Set.of("inventory", "enderchest", "curios", "cosmetic", "backups_open", "profiles_open");
    private static final long WAIT_MILLIS = 5000;
    private static final int CLOSED_TICKS = 3;
    private static String tab;
    private static UUID player;
    private static long armedAt;
    private static boolean inMenu;
    private static int closedTicks;

    private PanelReturn() {
    }

    static void sent(String action, String tabId, UUID target) {
        if (MENUS.contains(action)) {
            tab = tabId;
            player = target;
            armedAt = System.currentTimeMillis();
            inMenu = false;
            closedTicks = 0;
        }
    }

    public static void tick(Minecraft minecraft) {
        if (tab == null) {
            return;
        }
        if (minecraft.screen instanceof AbstractContainerScreen<?>) {
            inMenu = true;
            closedTicks = 0;
            return;
        }
        if (!inMenu) {
            // The menu never came (no permission, player offline...).
            if (System.currentTimeMillis() - armedAt > WAIT_MILLIS || !(minecraft.screen instanceof StaffPanelScreen)) {
                reset();
            }
            return;
        }
        if (minecraft.screen != null || minecraft.player == null) {
            reset();
            return;
        }
        if (++closedTicks >= CLOSED_TICKS) {
            String reopenTab = tab;
            UUID reopenPlayer = player;
            reset();
            minecraft.setScreen(StaffPanelScreen.reopen(reopenTab, reopenPlayer));
        }
    }

    public static void reset() {
        tab = null;
        player = null;
        inMenu = false;
        closedTicks = 0;
    }
}
