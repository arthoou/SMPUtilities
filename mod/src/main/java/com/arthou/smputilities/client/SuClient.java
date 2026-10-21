package com.arthou.smputilities.client;

import com.arthou.smputilities.client.gui.Gfx;
import com.arthou.smputilities.client.bubble.BubbleManager;
import com.arthou.smputilities.client.bubble.TypingWatcher;
import com.arthou.smputilities.client.gui.Theme;
import com.arthou.smputilities.client.modsync.ClientModSync;
import com.arthou.smputilities.client.tts.BridgeLauncher;
import com.arthou.smputilities.core.chat.StyledText;
import com.arthou.smputilities.core.nick.NameAnimation;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.server.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;

/**
 * Loader-independent client hooks: the panel key, the HUD indicator, nametags,
 * speech bubbles and TTS. Each loader's client glue calls these.
 */
public final class SuClient {
    public static final KeyMapping PANEL_KEY = ClientCompat.keyMapping("key.smputilities.panel", "key.categories.smputilities");
    private static int ticks;

    private SuClient() {
    }

    /** End of every client tick. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        while (PANEL_KEY.consumeClick()) {
            if (minecraft.player != null && minecraft.getConnection() != null) {
                ClientCompat.sendCommand("su staff");
            }
        }
        BubbleManager.tick();
        TypingWatcher.tick(minecraft);
        ClientModSync.tick();
        if (++ticks % 20 == 0) {
            VoiceMuteBridge.tick();
        }
    }

    /** Vanish reminder in the top-left corner of the HUD. */
    public static void renderHud(Gfx graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !ClientState.isVanished(minecraft.player.getUUID())) {
            return;
        }
        String text = Texts.translatable("hud.smputilities.vanished").getString();
        int width = minecraft.font.width(text) + 14;
        Theme.roundRect(graphics, 6, 6, 6 + width, 22, Theme.PANEL_BG);
        graphics.fill(10, 12, 14, 16, Theme.ACCENT);
        graphics.drawString(minecraft.font, text, 18, 10, Theme.ACCENT, false);
    }

    /**
     * The name above heads (HiddenNames' nameplate): the RP nickname, in the
     * team's color when the server says so, with its animation; staff with
     * real names on see both. Returns the original when there is nothing to change.
     */
    public static Component nametag(Player player, Component original) {
        String nick = ClientState.nickname(player.getUUID());
        NameAnimation animation = ClientState.animation(player.getUUID());
        boolean animated = animation != NameAnimation.NONE && animation != NameAnimation.HIDDEN && ClientConfig.get().nametags.animations;
        boolean hasNick = nick != null && !nick.isEmpty();
        if (!hasNick && !animated) {
            return original;
        }
        Team team = player.getTeam();
        boolean teamColor = ClientState.teamColorOverride() && team != null && team.getColor() != null && team.getColor().isColor();
        MutableComponent name;
        if (!hasNick) {
            name = original.copy();
        } else if (teamColor) {
            name = Texts.literal(StyledText.strip(nick)).withStyle(team.getColor());
        } else {
            name = Msg.styled(nick);
        }
        Component shown = name;
        Minecraft minecraft = Minecraft.getInstance();
        if (animated && minecraft.level != null) {
            shown = NameAnimations.apply(animation, name, player, minecraft.level.getGameTime() + player.getId() * 21L);
        }
        String real = ClientState.realName(player.getUUID());
        if (hasNick && ClientState.realNames() && real != null) {
            shown = Texts.empty().append(shown).append(Texts.literal(" (" + real + ")").withStyle(ChatFormatting.GRAY));
        }
        return shown;
    }

    /**
     * Whether a nameplate is drawn (HiddenNames' rules): hidden nameplates
     * never, behind blocks not when the server hides them there, your own in
     * third person when you asked for it. Null leaves it to the game.
     */
    public static Boolean nameplateVisible(Entity entity) {
        if (!(entity instanceof Player player)) {
            return null;
        }
        if (ClientState.animation(player.getUUID()) == NameAnimation.HIDDEN) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player local = minecraft.player;
        if (local == null) {
            return null;
        }
        if (player == local) {
            boolean thirdPerson = !minecraft.options.getCameraType().isFirstPerson();
            return ClientConfig.get().nametags.showOwn && thirdPerson && !minecraft.options.hideGui && !player.isSpectator() ? Boolean.TRUE : null;
        }
        if (ClientState.blocksHideNameplates()) {
            Vec3 from = local.getEyePosition();
            Vec3 to = player.getEyePosition();
            HitResult hit = Mc.level(local).clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, local));
            if (hit.getType() != HitResult.Type.MISS) {
                return false;
            }
        }
        return null;
    }

    public static void loggedOut() {
        ClientState.reset();
        BubbleManager.clear();
        TypingWatcher.reset();
        ClientModSync.reset();
        BridgeLauncher.requestShutdownAsync();
    }
}
