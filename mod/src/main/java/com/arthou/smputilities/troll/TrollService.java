package com.arthou.smputilities.troll;

import com.arthou.smputilities.mc.Ids;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.chat.ChatService;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Harmless staff pranks. Nothing here breaks blocks, deals damage or touches
 * inventories: explosions and lightning are visual only, and launches come
 * with slow falling.
 */
public final class TrollService {
    public enum Action {
        EXPLOSION, LIGHTNING, SOUND, JUMPSCARE, NAUSEA, DARKNESS, LEVITATE, SLOW, GLOW, TINY_JUMP, SPIN, LAUNCH, FAKE_MESSAGE, FAKE_JOIN,
        FAKE_QUIT, TITLE_ACTION, TITLE_DEATH;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Action parse(String value) {
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private static final String THIS_ACTION_SOUND = SmpUtilities.MOD_ID + ":action.thisaction";
    private static final List<Spin> SPINS = new ArrayList<>();

    private record Spin(UUID player, int[] stepsLeft) {
    }

    private TrollService() {
    }

    /** @return false when this kind of prank is disabled in the config */
    public static boolean allowed(Action action) {
        SuConfig config = SuServer.config();
        if (!config.features.troll) {
            return false;
        }
        SuConfig.Troll troll = config.troll;
        return switch (action) {
            case EXPLOSION, LIGHTNING -> troll.fakeExplosions;
            case SOUND -> troll.fakeSounds;
            case JUMPSCARE, TITLE_ACTION, TITLE_DEATH -> troll.jumpscares;
            case NAUSEA, DARKNESS, GLOW -> troll.visualEffects;
            case LEVITATE, SLOW, TINY_JUMP, SPIN, LAUNCH -> troll.funnyEffects;
            case FAKE_MESSAGE, FAKE_JOIN, FAKE_QUIT -> troll.fakeMessages;
        };
    }

    public static void apply(Action action, ServerPlayer target, String argument) {
        int seconds = Math.max(1, SuServer.config().troll.maxEffectSeconds);
        ServerLevel level = target.serverLevel();
        Vec3 behind = target.position().subtract(target.getLookAngle().multiply(2.5, 0, 2.5));
        switch (action) {
            case EXPLOSION -> {
                Mc.particle(target, ParticleTypes.EXPLOSION_EMITTER, behind.x, behind.y + 1, behind.z);
                sound(target, "minecraft:entity.generic.explode", behind, 4.0F, 1.0F);
            }
            case LIGHTNING -> {
                Mc.visualLightning(level, behind.x, behind.y, behind.z);
            }
            case SOUND -> {
                String sound = argument != null && Ids.parse(argument.trim()) != null ? argument.trim() : "minecraft:entity.creeper.primed";
                sound(target, sound, behind, 1.0F, 1.0F);
            }
            case JUMPSCARE -> {
                Mc.particle(target, ParticleTypes.ELDER_GUARDIAN, target.getX(), target.getY(), target.getZ());
                sound(target, "minecraft:entity.elder_guardian.curse", target.position(), 1.0F, 1.0F);
                effect(target, "minecraft:darkness", 3, 0);
            }
            case NAUSEA -> effect(target, "minecraft:nausea", Math.min(seconds, 10), 0);
            case DARKNESS -> effect(target, "minecraft:darkness", Math.min(seconds, 8), 0);
            case LEVITATE -> {
                effect(target, "minecraft:levitation", Math.min(seconds, 3), 0);
                effect(target, "minecraft:slow_falling", Math.min(seconds, 3) + 8, 0);
            }
            case SLOW -> effect(target, "minecraft:slowness", seconds, 2);
            case GLOW -> effect(target, "minecraft:glowing", seconds, 0);
            case TINY_JUMP -> effect(target, "minecraft:jump_boost", seconds, 4);
            case SPIN -> {
                synchronized (SPINS) {
                    SPINS.add(new Spin(target.getUUID(), new int[] {16}));
                }
            }
            case LAUNCH -> {
                effect(target, "minecraft:slow_falling", 12, 0);
                target.setDeltaMovement(target.getDeltaMovement().add(0, 1.1, 0));
                target.hurtMarked = true;
            }
            case FAKE_MESSAGE -> {
                String[] parts = argument == null ? new String[0] : argument.split(" ", 2);
                if (parts.length == 2) {
                    Mc.send(target, Texts.literal("<" + parts[0] + "> " + parts[1]));
                }
            }
            case FAKE_JOIN, FAKE_QUIT -> {
                String name = argument == null || argument.isBlank() ? "Herobrine" : argument.trim();
                Component line = Texts.empty().append(Texts.translatable(
                    action == Action.FAKE_JOIN ? "multiplayer.player.joined" : "multiplayer.player.left", name).withStyle(ChatFormatting.YELLOW));
                Mc.send(target, line);
            }
            case TITLE_ACTION -> title(target, Texts.literal("This action will have consequences...").withStyle(ChatFormatting.RED),
                argument == null || argument.isBlank() ? Texts.empty() : Texts.literal(argument), THIS_ACTION_SOUND);
            case TITLE_DEATH -> {
                String[] parts = argument == null ? new String[0] : argument.trim().split(" ");
                String name = parts.length > 0 && !parts[0].isEmpty() ? parts[0] : target.getGameProfile().getName();
                ChatFormatting color = parts.length > 1 ? ChatFormatting.getByName(parts[1]) : ChatFormatting.RED;
                if (color == null || !color.isColor()) {
                    color = ChatFormatting.RED;
                }
                title(target, Texts.literal(name + " -1").withStyle(color), Texts.empty(), "minecraft:entity.wither.spawn");
            }
        }
    }

    /** Same title to everyone (DUSMP /thisaction and /death). */
    public static void broadcastTitle(MinecraftServer server, Action action, String argument) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            apply(action, player, argument);
        }
    }

    private static void title(ServerPlayer player, Component title, Component subtitle, String soundId) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 12 * 20, 20));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        Mc.playSound(player, soundId, SoundSource.MASTER, 1.0F, 1.0F);
    }

    /** Effects by id, so renamed fields (JUMP became JUMP_BOOST) and missing ones (darkness before 1.19) need no code. */
    private static void effect(ServerPlayer player, String effectId, int seconds, int amplifier) {
        MobEffectInstance effect = Mc.effect(effectId, seconds * 20, amplifier);
        if (effect != null) {
            player.addEffect(effect);
        }
    }

    private static void sound(ServerPlayer player, String soundId, Vec3 at, float volume, float pitch) {
        Mc.playSound(player, soundId, SoundSource.HOSTILE, at.x, at.y, at.z, volume, pitch);
    }

    /** Spins 22.5° per tick until done. */
    public static void tick(MinecraftServer server) {
        synchronized (SPINS) {
            Iterator<Spin> iterator = SPINS.iterator();
            while (iterator.hasNext()) {
                Spin spin = iterator.next();
                ServerPlayer player = server.getPlayerList().getPlayer(spin.player());
                if (player == null || spin.stepsLeft()[0]-- <= 0) {
                    iterator.remove();
                    continue;
                }
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot() + 22.5F, player.getXRot());
            }
        }
    }

    /** Helper for chat-based fake presence lines broadcast to everyone. */
    public static void fakePresenceForAll(ServerPlayer reference, boolean join) {
        ChatService.broadcastPresence(reference, join, true);
    }
}
