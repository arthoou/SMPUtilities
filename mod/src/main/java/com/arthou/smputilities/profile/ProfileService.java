package com.arthou.smputilities.profile;

import com.arthou.smputilities.mc.Nbt;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.backup.BackupService;
import com.arthou.smputilities.backup.CompatInventorySupport;
import com.arthou.smputilities.backup.SnapshotType;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.inventory.PlayerState;
import com.arthou.smputilities.nick.NicknameService;
import com.arthou.smputilities.platform.PlayerData;
import com.arthou.smputilities.server.Msg;
import com.arthou.smputilities.server.Perms;
import com.arthou.smputilities.server.SuServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Inventory profiles (Inv Profile, integrated and extended): the original
 * one-slot Quick Change plus named profiles for RP characters.
 *
 * <p>Switching is crash-safe: the outgoing state is written to disk before the
 * incoming one is applied, and the player's own save file remembers which
 * profile it holds, so a crash between the two steps is repaired on the next
 * login instead of duplicating or losing items.
 */
public final class ProfileService {
    /** Stored with the vanilla player data (NeoForge persistent data). */
    private static final String PERSISTENT_KEY = "smputilities:profile";
    private static ProfileStore store;

    private ProfileService() {
    }

    public static void onServerStart(SuServer context) {
        store = new ProfileStore(context.dataDir().resolve("profiles"));
    }

    public static void onServerStop() {
        store = null;
    }

    public static ProfileStore store() {
        return store;
    }

    public static Set<PlayerState.Part> parts() {
        SuConfig.Profiles config = SuServer.config().profiles;
        Set<PlayerState.Part> parts = EnumSet.noneOf(PlayerState.Part.class);
        if (config.storeInventory) {
            parts.add(PlayerState.Part.INVENTORY);
        }
        if (config.storeEnderChest) {
            parts.add(PlayerState.Part.ENDER_CHEST);
        }
        if (config.storeExperience) {
            parts.add(PlayerState.Part.EXPERIENCE);
        }
        if (config.storeHealth) {
            parts.add(PlayerState.Part.HEALTH);
        }
        if (config.storeHunger) {
            parts.add(PlayerState.Part.HUNGER);
        }
        if (config.storeEffects) {
            parts.add(PlayerState.Part.EFFECTS);
        }
        if (config.storeLocation) {
            parts.add(PlayerState.Part.LOCATION);
        }
        return parts;
    }

    /** A fresh character: empty inventory, no XP, full health and hunger. */
    static CompoundTag emptyState() {
        CompoundTag tag = new CompoundTag();
        tag.put("Inventory", new ListTag());
        tag.put("EnderItems", new ListTag());
        tag.putInt("XpLevel", 0);
        tag.putFloat("XpP", 0);
        tag.putInt("XpTotal", 0);
        tag.putFloat("Health", 20.0F);
        tag.putInt("foodLevel", 20);
        tag.putFloat("foodSaturationLevel", 5.0F);
        tag.putFloat("foodExhaustionLevel", 0);
        tag.putInt("foodTickTimer", 0);
        tag.put("active_effects", new ListTag());
        return tag;
    }

    private static ProfileStore.PlayerProfiles profiles(UUID player) {
        return store.get(player);
    }

    // ------------------------------------------------------------- queries

    public static ProfileStore.PlayerProfiles of(UUID player) {
        if (store == null) {
            return new ProfileStore.PlayerProfiles();
        }
        try {
            return profiles(player);
        } catch (IllegalStateException e) {
            return new ProfileStore.PlayerProfiles();
        }
    }

    // ------------------------------------------------------------ commands

    /** @return null on success, otherwise a translation key */
    public static String create(ServerPlayer owner, String rawName, ServerPlayer actor) {
        String name = ProfileStore.normalize(rawName);
        if (!ProfileStore.isValidName(name)) {
            return "profile.invalid_name";
        }
        try {
            ProfileStore.PlayerProfiles profiles = profiles(owner.getUUID());
            if (profiles.exists(name)) {
                return "profile.exists";
            }
            int limit = Perms.profileLimit(owner);
            if (profiles.names().size() >= limit) {
                return "profile.limit";
            }
            ProfileStore.Profile profile = new ProfileStore.Profile(name);
            profile.created = System.currentTimeMillis();
            profile.updated = profile.created;
            profile.data = emptyState();
            profiles.profiles.put(name, profile);
            if (!store.save(owner.getUUID())) {
                profiles.profiles.remove(name);
                return "profile.save_failed";
            }
            SuServer.get().log(actor, "profile.create", owner.getUUID(), owner.getGameProfile().getName(), name);
            return null;
        } catch (IllegalStateException e) {
            return "profile.unreadable";
        }
    }

    /** @return null on success, otherwise a translation key */
    public static String switchTo(ServerPlayer player, String rawName, ServerPlayer actor, boolean bypassCooldown) {
        String name = ProfileStore.normalize(rawName);
        if (!player.isAlive()) {
            return "profile.dead";
        }
        if (!player.containerMenu.getCarried().isEmpty()) {
            return "profile.cursor_not_empty";
        }
        ProfileStore.PlayerProfiles profiles;
        try {
            profiles = profiles(player.getUUID());
        } catch (IllegalStateException e) {
            return "profile.unreadable";
        }
        if (!profiles.exists(name)) {
            return "profile.not_found";
        }
        if (profiles.active.equals(name)) {
            return "profile.already_active";
        }
        long now = System.currentTimeMillis();
        long cooldown = SuServer.config().profiles.switchCooldownSeconds * 1000L;
        if (!bypassCooldown && now - profiles.lastSwitch < cooldown) {
            return "profile.cooldown";
        }

        // Close any open container and return the 2x2 crafting grid to the
        // inventory, exactly like closing the inventory screen, so those items
        // are saved with the outgoing profile.
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        player.inventoryMenu.removed(player);
        player.inventoryMenu.broadcastChanges();

        Set<PlayerState.Part> parts = parts();
        String previousName = profiles.active;
        CompoundTag outgoing = PlayerState.capture(player, parts);
        CompoundTag outgoingCompat = SuServer.config().profiles.storeCompatSlots ? CompatInventorySupport.capture(player) : new CompoundTag();
        ProfileStore.Profile previous = profiles.profiles.computeIfAbsent(previousName, ProfileStore.Profile::new);
        if (previous.created == 0) {
            previous.created = now;
        }
        ProfileStore.Profile incoming = profiles.profiles.get(name);
        if (incoming == null) {
            incoming = new ProfileStore.Profile(name);
            incoming.created = now;
            incoming.data = emptyState();
            profiles.profiles.put(name, incoming);
        }

        BackupService.snapshot(player, SnapshotType.PROFILE_SWITCH, previousName + " -> " + name,
            actor == null ? "" : actor.getGameProfile().getName());

        // 1. Persist the outgoing state and the new active name.
        CompoundTag previousData = previous.data;
        CompoundTag previousCompat = previous.compat;
        previous.data = outgoing;
        previous.compat = outgoingCompat;
        previous.updated = now;
        profiles.active = name;
        profiles.lastSwitch = now;
        if (!store.save(player.getUUID())) {
            previous.data = previousData;
            previous.compat = previousCompat;
            profiles.active = previousName;
            return "profile.save_failed";
        }

        // 2. Apply the incoming state; undo everything if that fails.
        try {
            PlayerState.apply(player, incoming.data.copy(), parts);
            if (SuServer.config().profiles.storeCompatSlots && !incoming.compat.isEmpty()) {
                CompatInventorySupport.apply(player, incoming.compat);
            }
            PlayerData.of(player).putString(PERSISTENT_KEY, name);
        } catch (RuntimeException e) {
            SuLog.error("Profile switch of " + player.getGameProfile().getName() + " failed, rolling back", e);
            PlayerState.apply(player, outgoing, parts);
            if (!outgoingCompat.isEmpty()) {
                CompatInventorySupport.apply(player, outgoingCompat);
            }
            profiles.active = previousName;
            store.save(player.getUUID());
            return "profile.switch_failed";
        }

        if (SuServer.config().profiles.switchNickname && !incoming.nick.isEmpty()) {
            NicknameService.set(Mc.server(player), player.getUUID(), incoming.nick, true, actor);
        }
        SuServer.get().log(actor == null ? player : actor, "profile.switch", player.getUUID(), player.getGameProfile().getName(), previousName + " -> " + name);
        return null;
    }

    /** @return null on success, otherwise a translation key */
    public static String delete(UUID owner, String ownerName, String rawName, ServerPlayer actor) {
        String name = ProfileStore.normalize(rawName);
        try {
            ProfileStore.PlayerProfiles profiles = profiles(owner);
            if (name.equals(ProfileStore.DEFAULT) || !profiles.profiles.containsKey(name)) {
                return name.equals(ProfileStore.DEFAULT) ? "profile.cannot_delete_default" : "profile.not_found";
            }
            if (profiles.active.equals(name)) {
                return "profile.cannot_delete_active";
            }
            ProfileStore.Profile removed = profiles.profiles.remove(name);
            if (!store.save(owner)) {
                profiles.profiles.put(name, removed);
                return "profile.save_failed";
            }
            SuServer.get().log(actor, "profile.delete", owner, ownerName, name);
            return null;
        } catch (IllegalStateException e) {
            return "profile.unreadable";
        }
    }

    public static String setNick(UUID owner, String rawName, String nick) {
        String name = ProfileStore.normalize(rawName);
        ProfileStore.PlayerProfiles profiles = of(owner);
        if (!profiles.exists(name)) {
            return "profile.not_found";
        }
        ProfileStore.Profile profile = profiles.profiles.computeIfAbsent(name, ProfileStore.Profile::new);
        profile.nick = nick;
        return store.save(owner) ? null : "profile.save_failed";
    }

    /**
     * Inv Profile's Quick Change, unchanged in behaviour: swaps main inventory,
     * armor and offhand with the single stored slot.
     */
    public static String quickChange(ServerPlayer player, net.minecraft.world.item.ItemStack carried) {
        if (!carried.isEmpty()) {
            return "invprofile.cursor_not_empty";
        }
        ProfileStore.PlayerProfiles profiles;
        try {
            profiles = profiles(player.getUUID());
        } catch (IllegalStateException e) {
            return "profile.unreadable";
        }
        Set<PlayerState.Part> inventory = EnumSet.of(PlayerState.Part.INVENTORY);
        CompoundTag current = PlayerState.capture(player, inventory);
        CompoundTag saved = profiles.quick.contains("Inventory") ? profiles.quick.copy() : emptyState();
        CompoundTag previousQuick = profiles.quick;
        profiles.quick = current;
        if (!store.save(player.getUUID())) {
            profiles.quick = previousQuick;
            return "profile.save_failed";
        }
        try {
            PlayerState.apply(player, saved, inventory);
        } catch (RuntimeException e) {
            SuLog.error("Quick Change of " + player.getGameProfile().getName() + " failed, rolling back", e);
            PlayerState.apply(player, current, inventory);
            profiles.quick = previousQuick;
            store.save(player.getUUID());
            return "profile.switch_failed";
        }
        return null;
    }

    /** Repairs the stored active name if the server stopped between saving a switch and saving the player. */
    public static void onLogin(ServerPlayer player) {
        if (store == null) {
            return;
        }
        try {
            ProfileStore.PlayerProfiles profiles = profiles(player.getUUID());
            CompoundTag persistent = PlayerData.of(player);
            if (!persistent.contains(PERSISTENT_KEY)) {
                persistent.putString(PERSISTENT_KEY, profiles.active);
                return;
            }
            String held = Nbt.getString(persistent, PERSISTENT_KEY);
            if (!held.equals(profiles.active) && profiles.exists(held)) {
                SuLog.warn("Profile of " + player.getGameProfile().getName() + " was '" + profiles.active + "' on disk but the player holds '"
                    + held + "'; using the player's data");
                profiles.active = held;
                store.save(player.getUUID());
                Msg.info(player, "profile.repaired", held);
            }
        } catch (IllegalStateException e) {
            Msg.error(player, "profile.unreadable");
        }
    }
}
