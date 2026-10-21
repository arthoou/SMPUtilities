package com.arthou.smputilities.troll;

import com.arthou.smputilities.mc.Ids;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Texts;
import com.arthou.smputilities.SmpUtilities;
import com.arthou.smputilities.chat.ChatService;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
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

    private static final SoundEvent THIS_ACTION_SOUND = SoundEvent.createVariableRangeEvent(SmpUtilities.id("action.thisaction"));
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
                level.sendParticles(target, ParticleTypes.EXPLOSION_EMITTER, true, behind.x, behind.y + 1, behind.z, 1, 0, 0, 0, 0);
                sound(target, SoundEvents.GENERIC_EXPLODE.value(), behind, 4.0F, 1.0F);
            }
            case LIGHTNING -> {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.setVisualOnly(true);
                    bolt.moveTo(behind.x, behind.y, behind.z);
                    level.addFreshEntity(bolt);
                }
            }
            case SOUND -> {
                SoundEvent sound = SoundEvents.CREEPER_PRIMED;
                if (argument != null && !argument.isBlank()) {
                    ResourceLocation id = Ids.parse(argument.trim());
                    if (id != null) {
                        SoundEvent found = BuiltInRegistries.SOUND_EVENT.get(id);
                        sound = found != null ? found : SoundEvent.createVariableRangeEvent(id);
                    }
                }
                sound(target, sound, behind, 1.0F, 1.0F);
            }
            case JUMPSCARE -> {
                level.sendParticles(target, ParticleTypes.ELDER_GUARDIAN, true, target.getX(), target.getY(), target.getZ(), 1, 0, 0, 0, 0);
                sound(target, SoundEvents.ELDER_GUARDIAN_CURSE, target.position(), 1.0F, 1.0F);
                effect(target, MobEffects.DARKNESS, 3, 0);
            }
            case NAUSEA -> effect(target, MobEffects.CONFUSION, Math.min(seconds, 10), 0);
            case DARKNESS -> effect(target, MobEffects.DARKNESS, Math.min(seconds, 8), 0);
            case LEVITATE -> {
                effect(target, MobEffects.LEVITATION, Math.min(seconds, 3), 0);
                effect(target, MobEffects.SLOW_FALLING, Math.min(seconds, 3) + 8, 0);
            }
            case SLOW -> effect(target, MobEffects.MOVEMENT_SLOWDOWN, seconds, 2);
            case GLOW -> effect(target, MobEffects.GLOWING, seconds, 0);
            case TINY_JUMP -> effect(target, MobEffects.JUMP, seconds, 4);
            case SPIN -> {
                synchronized (SPINS) {
                    SPINS.add(new Spin(target.getUUID(), new int[] {16}));
                }
            }
            case LAUNCH -> {
                effect(target, MobEffects.SLOW_FALLING, 12, 0);
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
                title(target, Texts.literal(name + " -1").withStyle(color), Texts.empty(), SoundEvents.WITHER_SPAWN);
            }
        }
    }

    /** Same title to everyone (DUSMP /thisaction and /death). */
    public static void broadcastTitle(MinecraftServer server, Action action, String argument) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            apply(action, player, argument);
        }
    }

    private static void title(ServerPlayer player, Component title, Component subtitle, SoundEvent sound) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 12 * 20, 20));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.playNotifySound(sound, SoundSource.MASTER, 1.0F, 1.0F);
    }

    private static void effect(ServerPlayer player, Holder<MobEffect> effect, int seconds, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, seconds * 20, amplifier, false, false, true));
    }

    private static void sound(ServerPlayer player, SoundEvent sound, Vec3 at, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.HOSTILE,
            at.x, at.y, at.z, volume, pitch, player.getRandom().nextLong()));
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
