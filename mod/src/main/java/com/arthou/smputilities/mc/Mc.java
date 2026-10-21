package com.arthou.smputilities.mc;

import com.mojang.authlib.GameProfile;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
//#if MC>=1.19.3
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
//#else
//$$ import net.minecraft.core.Registry;
//$$ import net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket;
//#endif
//#if MC<1.19
//$$ import net.minecraft.Util;
//$$ import net.minecraft.network.chat.ChatType;
//#endif
//#if MC>=1.19.4
import net.minecraft.world.entity.Display;
//#else
//$$ import net.minecraft.world.entity.AreaEffectCloud;
//#endif
//#if MC>=1.21
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
//#endif
import net.minecraft.world.item.enchantment.EnchantmentHelper;
//#if MC>=1.21.2
import net.minecraft.world.entity.EntitySpawnReason;
//#endif
//#if MC>=1.21.9
import net.minecraft.server.players.NameAndId;
//#endif
//#if MC>=1.21.11
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.PermissionSet;
//#endif
import net.minecraft.network.protocol.game.ClientboundSoundPacket;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side game API calls that differ between Minecraft versions. Shared
 * code calls these instead of the game directly.
 */
public final class Mc {
    private Mc() {
    }

    // ---------------------------------------------------------------- players

    public static ServerLevel level(ServerPlayer player) {
        //#if MC>=1.21.6
        return player.level();
        //#elif MC>=1.20
        //$$ return player.serverLevel();
        //#else
        //$$ return player.getLevel();
        //#endif
    }

    public static Level level(Entity entity) {
        //#if MC>=1.20
        return entity.level();
        //#else
        //$$ return entity.getLevel();
        //#endif
    }

    public static MinecraftServer server(ServerPlayer player) {
        return level(player).getServer();
    }

    public static String name(GameProfile profile) {
        //#if MC>=1.21.9
        return profile.name();
        //#else
        //$$ return profile.getName();
        //#endif
    }

    public static UUID id(GameProfile profile) {
        //#if MC>=1.21.9
        return profile.id();
        //#else
        //$$ return profile.getId();
        //#endif
    }

    /** The account name. */
    public static String name(net.minecraft.world.entity.player.Player player) {
        return name(player.getGameProfile());
    }

    /** Damage type id ("minecraft:fall"), used as the reason of death backups. */
    public static String damageCause(net.minecraft.world.damagesource.DamageSource source) {
        //#if MC>=1.19.4
        return source.typeHolder().unwrapKey().map(Ids::key).orElse("death");
        //#else
        //$$ return source.getMsgId();
        //#endif
    }

    public static boolean onGround(Entity entity) {
        //#if MC>=1.20
        return entity.onGround();
        //#else
        //$$ return entity.isOnGround();
        //#endif
    }

    /** The player's Netty channel; null for fake players without one. */
    public static io.netty.channel.Channel channel(ServerPlayer player) {
        net.minecraft.network.Connection connection = ((com.arthou.smputilities.mixin.PacketListenerAccessor) player.connection).smputilities$connection();
        return connection == null ? null : ((com.arthou.smputilities.mixin.ConnectionAccessor) connection).smputilities$channel();
    }

    public static int latency(ServerPlayer player) {
        //#if MC>=1.20.2
        return player.connection.latency();
        //#else
        //$$ return player.latency;
        //#endif
    }

    /** The language the player's client uses, e.g. "pt_br". */
    public static String language(ServerPlayer player) {
        //#if MC>=1.20.2
        return player.clientInformation().language();
        //#else
        //$$ return ((com.arthou.smputilities.mixin.ServerPlayerAccessor) player).smputilities$language();
        //#endif
    }

    public static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        //#if MC>=1.21.2
        player.teleportTo(level, x, y, z, Set.of(), yaw, pitch, true);
        //#else
        //$$ player.teleportTo(level, x, y, z, yaw, pitch);
        //#endif
    }

    public static void moveTo(Entity entity, double x, double y, double z) {
        //#if MC>=1.21.5
        entity.snapTo(x, y, z);
        //#else
        //$$ entity.moveTo(x, y, z);
        //#endif
    }

    // --------------------------------------------------------------- messages

    public static void send(ServerPlayer player, Component text) {
        //#if MC>=1.19
        player.sendSystemMessage(text);
        //#else
        //$$ player.sendMessage(text, Util.NIL_UUID);
        //#endif
    }

    public static void actionBar(ServerPlayer player, Component text) {
        player.displayClientMessage(text, true);
    }

    /** Writes to the server console. */
    public static void console(MinecraftServer server, Component text) {
        //#if MC>=1.19
        server.sendSystemMessage(text);
        //#else
        //$$ server.sendMessage(text, Util.NIL_UUID);
        //#endif
    }

    /** Sends a system line to every player and the console. */
    public static void broadcast(MinecraftServer server, Component text) {
        //#if MC>=1.19
        server.getPlayerList().broadcastSystemMessage(text, false);
        //#else
        //$$ server.getPlayerList().broadcastMessage(text, ChatType.SYSTEM, Util.NIL_UUID);
        //#endif
    }

    /** The player behind a command source, or null for the console and command blocks. */
    public static ServerPlayer player(CommandSourceStack source) {
        //#if MC>=1.19
        return source.getPlayer();
        //#else
        //$$ return source.getEntity() instanceof ServerPlayer player ? player : null;
        //#endif
    }

    /** Command feedback that is not broadcast to other operators. */
    public static void reply(CommandSourceStack source, Component text) {
        //#if MC>=1.20
        source.sendSuccess(() -> text, false);
        //#else
        //$$ source.sendSuccess(text, false);
        //#endif
    }

    // ------------------------------------------------------------ permissions

    /** Whether the source has at least the given vanilla op level (0-4). */
    public static boolean hasOpLevel(CommandSourceStack source, int level) {
        //#if MC>=1.21.11
        return levelOf(source.permissions()) >= level;
        //#else
        //$$ return source.hasPermission(level);
        //#endif
    }

    /** The vanilla op level of an online player (0-4). */
    public static int opLevel(ServerPlayer player) {
        //#if MC>=1.21.11
        return levelOf(player.permissions());
        //#else
        //$$ return opLevel(server(player), player.getUUID(), name(player));
        //#endif
    }

    /** The op level the server gives an account, online or not (0-4). */
    public static int opLevel(MinecraftServer server, UUID id, String name) {
        //#if MC>=1.21.11
        return levelOf(server.getProfilePermissions(new NameAndId(id, name)));
        //#elif MC>=1.21.9
        //$$ return server.getProfilePermissions(new NameAndId(id, name));
        //#else
        //$$ return server.getProfilePermissions(new GameProfile(id, name));
        //#endif
    }

    //#if MC>=1.21.11
    private static int levelOf(PermissionSet permissions) {
        if (Commands.LEVEL_OWNERS.check(permissions)) {
            return 4;
        }
        if (Commands.LEVEL_ADMINS.check(permissions)) {
            return 3;
        }
        if (Commands.LEVEL_GAMEMASTERS.check(permissions)) {
            return 2;
        }
        return Commands.LEVEL_MODERATORS.check(permissions) ? 1 : 0;
    }
    //#endif

    // ------------------------------------------------------------- profiles

    /** A known account. */
    public record Account(UUID id, String name) {
    }

    /** Looks up an account in the server's name cache (players who joined before). */
    public static Optional<Account> accountByName(MinecraftServer server, String name) {
        //#if MC>=1.21.9
        return server.services().nameToIdCache().get(name).map(found -> new Account(found.id(), found.name()));
        //#else
        //$$ net.minecraft.server.players.GameProfileCache cache = server.getProfileCache();
        //$$ return cache == null ? Optional.empty() : cache.get(name).map(found -> new Account(id(found), name(found)));
        //#endif
    }

    public static Optional<Account> accountById(MinecraftServer server, UUID id) {
        //#if MC>=1.21.9
        return server.services().nameToIdCache().get(id).map(found -> new Account(found.id(), found.name()));
        //#else
        //$$ net.minecraft.server.players.GameProfileCache cache = server.getProfileCache();
        //$$ return cache == null ? Optional.empty() : cache.get(id).map(found -> new Account(id(found), name(found)));
        //#endif
    }

    // ----------------------------------------------------------------- bans

    public static void ban(MinecraftServer server, UUID id, String name, Date created, String source, Date expires, String reason) {
        //#if MC>=1.21.9
        server.getPlayerList().getBans().add(new UserBanListEntry(new NameAndId(id, name), created, source, expires, reason));
        //#else
        //$$ server.getPlayerList().getBans().add(new UserBanListEntry(new GameProfile(id, name), created, source, expires, reason));
        //#endif
    }

    public static boolean isBanned(MinecraftServer server, UUID id, String name) {
        //#if MC>=1.21.9
        return server.getPlayerList().getBans().isBanned(new NameAndId(id, name));
        //#else
        //$$ return server.getPlayerList().getBans().isBanned(new GameProfile(id, name));
        //#endif
    }

    /** Removes the vanilla ban. @return whether there was one */
    public static boolean unban(MinecraftServer server, UUID id, String name) {
        //#if MC>=1.21.9
        NameAndId key = new NameAndId(id, name);
        //#else
        //$$ GameProfile key = new GameProfile(id, name);
        //#endif
        if (!server.getPlayerList().getBans().isBanned(key)) {
            return false;
        }
        server.getPlayerList().getBans().remove(key);
        return true;
    }

    // ----------------------------------------------------------------- world

    public static int minY(Level level) {
        //#if MC>=1.21.2
        return level.getMinY();
        //#else
        //$$ return level.getMinBuildHeight();
        //#endif
    }

    public static boolean isSolid(BlockState state) {
        //#if MC>=1.20
        return state.isSolid();
        //#else
        //$$ return state.getMaterial().isSolid();
        //#endif
    }

    /** Average milliseconds per tick over the last 100 ticks. */
    public static double millisPerTick(MinecraftServer server) {
        //#if MC>=1.20.3
        return server.getAverageTickTimeNanos() / 1_000_000.0;
        //#else
        //$$ return server.getAverageTickTime();
        //#endif
    }

    /** The tick rate the server aims for (/tick rate from 1.20.3, otherwise 20). */
    public static float targetTickRate(MinecraftServer server) {
        //#if MC>=1.20.3
        return server.tickRateManager().tickrate();
        //#else
        //$$ return 20.0F;
        //#endif
    }

    /** Puts the player on a vehicle even when it would not normally accept riders. */
    public static boolean ride(Entity rider, Entity vehicle) {
        //#if MC>=1.21.9
        return rider.startRiding(vehicle, true, true);
        //#else
        //$$ return rider.startRiding(vehicle, true);
        //#endif
    }

    public static boolean hasChunk(ServerLevel level, int chunkX, int chunkZ) {
        return level.getChunkSource().hasChunk(chunkX, chunkZ);
    }

    /** World spawn: block position and facing. */
    public record Spawn(String dimension, BlockPos pos, float yaw) {
    }

    public static Spawn worldSpawn(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //#if MC>=1.21.9
        net.minecraft.world.level.storage.LevelData.RespawnData data = overworld.getRespawnData();
        return new Spawn(Ids.key(data.dimension()), data.pos(), data.yaw());
        //#else
        //$$ return new Spawn(Ids.dimensionOf(overworld), overworld.getSharedSpawnPos(), overworld.getSharedSpawnAngle());
        //#endif
    }

    public static String blockId(Block block) {
        //#if MC>=1.19.3
        return BuiltInRegistries.BLOCK.getKey(block).toString();
        //#else
        //$$ return Registry.BLOCK.getKey(block).toString();
        //#endif
    }

    /** The block with this id, or null. */
    public static Block block(String id) {
        ResourceLocation location = Ids.parse(id);
        if (location == null) {
            return null;
        }
        //#if MC>=1.19.3
        return BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
        //#else
        //$$ return Registry.BLOCK.getOptional(location).orElse(null);
        //#endif
    }

    /** Every registered sound id, for command suggestions. */
    public static Set<ResourceLocation> soundIds() {
        //#if MC>=1.19.3
        return BuiltInRegistries.SOUND_EVENT.keySet();
        //#else
        //$$ return Registry.SOUND_EVENT.keySet();
        //#endif
    }

    // ---------------------------------------------------------------- effects

    private static SoundEvent sound(ResourceLocation id) {
        //#if MC>=1.19.3
        return BuiltInRegistries.SOUND_EVENT.getOptional(id).orElseGet(() -> SoundEvent.createVariableRangeEvent(id));
        //#else
        //$$ return Registry.SOUND_EVENT.getOptional(id).orElseGet(() -> new SoundEvent(id));
        //#endif
    }

    /**
     * Plays a sound to one player only, at a position. Unknown ids still play
     * when the client has the sound (for example from SMPutilities' resources).
     */
    public static void playSound(ServerPlayer player, String soundId, SoundSource source, double x, double y, double z, float volume, float pitch) {
        ResourceLocation id = Ids.parse(soundId);
        if (id == null) {
            return;
        }
        SoundEvent sound = sound(id);
        long seed = player.getRandom().nextLong();
        //#if MC>=1.19.3
        player.connection.send(new ClientboundSoundPacket(Holder.direct(sound), source, x, y, z, volume, pitch, seed));
        //#elif MC>=1.19
        //$$ player.connection.send(new ClientboundSoundPacket(sound, source, x, y, z, volume, pitch, seed));
        //#else
        //$$ player.connection.send(new ClientboundSoundPacket(sound, source, x, y, z, volume, pitch));
        //#endif
    }

    /** Plays a sound to one player at their own position. */
    public static void playSound(ServerPlayer player, String soundId, SoundSource source, float volume, float pitch) {
        playSound(player, soundId, source, player.getX(), player.getY(), player.getZ(), volume, pitch);
    }

    /** A potion effect by id ("minecraft:darkness"); null when the effect does not exist on this version. */
    public static MobEffectInstance effect(String effectId, int ticks, int amplifier) {
        ResourceLocation id = Ids.parse(effectId);
        if (id == null) {
            return null;
        }
        //#if MC>=1.21.2
        Optional<Holder.Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.get(id);
        //#elif MC>=1.20.5
        //$$ Optional<Holder.Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.getHolder(id);
        //#elif MC>=1.19.3
        //$$ Optional<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getOptional(id);
        //#else
        //$$ Optional<MobEffect> effect = Registry.MOB_EFFECT.getOptional(id);
        //#endif
        return effect.map(value -> new MobEffectInstance(value, ticks, amplifier, false, false, true)).orElse(null);
    }

    /** Particles only this player sees. */
    public static void particle(ServerPlayer viewer, ParticleOptions particle, double x, double y, double z) {
        //#if MC>=1.21.4
        level(viewer).sendParticles(viewer, particle, true, true, x, y, z, 1, 0, 0, 0, 0);
        //#else
        //$$ level(viewer).sendParticles(viewer, particle, true, x, y, z, 1, 0, 0, 0, 0);
        //#endif
    }

    /** Lightning that only shows and sounds; it sets nothing on fire and hurts no one. */
    public static void visualLightning(ServerLevel level, double x, double y, double z) {
        //#if MC>=1.21.2
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        //#else
        //$$ LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        //#endif
        if (bolt != null) {
            bolt.setVisualOnly(true);
            moveTo(bolt, x, y, z);
            level.addFreshEntity(bolt);
        }
    }

    public static boolean hasBindingCurse(ItemStack stack) {
        //#if MC>=1.21
        return EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
        //#else
        //$$ return EnchantmentHelper.hasBindingCurse(stack);
        //#endif
    }

    /** An invisible entity players can sit on (/sit). */
    public static Entity createSeat(ServerLevel level, double x, double y, double z) {
        //#if MC>=1.19.4
        Display.TextDisplay seat = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        seat.setPos(x, y - 0.35, z);
        //#else
        //$$ AreaEffectCloud seat = new AreaEffectCloud(level, x, y - 0.15, z);
        //$$ seat.setRadius(0);
        //$$ seat.setDuration(Integer.MAX_VALUE);
        //$$ seat.setWaitTime(0);
        //#endif
        seat.setInvulnerable(true);
        return seat;
    }

    // ---------------------------------------------------------------- tab list

    /** Sends the target's tab list entry to the viewer (needed before the client spawns a player). */
    public static void showInTabList(ServerPlayer viewer, ServerPlayer target) {
        //#if MC>=1.19.3
        viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(target)));
        //#else
        //$$ viewer.connection.send(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER, target));
        //#endif
    }

    public static void hideFromTabList(ServerPlayer viewer, ServerPlayer target) {
        //#if MC>=1.19.3
        viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(target.getUUID())));
        //#else
        //$$ viewer.connection.send(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER, target));
        //#endif
    }

    /** Re-sends the player's tab list name to everyone. */
    public static void broadcastTabListName(ServerPlayer player) {
        //#if MC>=1.19.3
        server(player).getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
            EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of(player)));
        //#else
        //$$ server(player).getPlayerList().broadcastAll(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME, player));
        //#endif
    }

    // ------------------------------------------------------------ data versions

    public static int dataVersion() {
        //#if MC>=1.21.6
        return SharedConstants.getCurrentVersion().dataVersion().version();
        //#elif MC>=1.19
        //$$ return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        //#else
        //$$ return SharedConstants.getCurrentVersion().getWorldVersion();
        //#endif
    }

    /** Upgrades a saved player tag written by an older game version. */
    public static CompoundTag upgradePlayer(MinecraftServer server, CompoundTag tag, int fromVersion) {
        //#if MC>=1.19.4
        return DataFixTypes.PLAYER.updateToCurrentVersion(server.getFixerUpper(), tag, fromVersion);
        //#else
        //$$ return net.minecraft.nbt.NbtUtils.update(server.getFixerUpper(), DataFixTypes.PLAYER, tag, fromVersion);
        //#endif
    }
}
