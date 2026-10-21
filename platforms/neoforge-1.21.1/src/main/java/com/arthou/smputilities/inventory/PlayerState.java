package com.arthou.smputilities.inventory;

import com.arthou.smputilities.core.teleport.Location;
import com.arthou.smputilities.teleport.Teleports;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Captures and applies parts of a player's state as NBT. Applying goes
 * through the normal setters, which keep the client in sync, instead of
 * {@code Entity#load} (that would also move the player without telling the
 * client).
 *
 * <p>Keys match the vanilla player save format, so a full
 * {@code saveWithoutId} tag (old Inventory Rollback snapshots) can be applied
 * the same way as a tag captured here.
 */
public final class PlayerState {
    public enum Part { INVENTORY, ENDER_CHEST, EXPERIENCE, HEALTH, HUNGER, EFFECTS, LOCATION }

    public static final Set<Part> ALL_BUT_LOCATION = EnumSet.complementOf(EnumSet.of(Part.LOCATION));

    private PlayerState() {
    }

    public static CompoundTag capture(ServerPlayer player, Set<Part> parts) {
        HolderLookup.Provider registries = player.server.registryAccess();
        CompoundTag tag = new CompoundTag();
        if (parts.contains(Part.INVENTORY)) {
            tag.put("Inventory", PlayerNbt.writeInventory(contents(player), registries));
        }
        if (parts.contains(Part.ENDER_CHEST)) {
            ItemStack[] ender = new ItemStack[PlayerNbt.ENDER];
            for (int i = 0; i < ender.length; i++) {
                ender[i] = player.getEnderChestInventory().getItem(i).copy();
            }
            tag.put("EnderItems", PlayerNbt.writeEnder(ender, registries));
        }
        if (parts.contains(Part.EXPERIENCE)) {
            tag.putInt("XpLevel", player.experienceLevel);
            tag.putFloat("XpP", player.experienceProgress);
            tag.putInt("XpTotal", player.totalExperience);
        }
        if (parts.contains(Part.HEALTH)) {
            tag.putFloat("Health", player.getHealth());
        }
        if (parts.contains(Part.HUNGER)) {
            player.getFoodData().addAdditionalSaveData(tag);
        }
        if (parts.contains(Part.EFFECTS)) {
            ListTag effects = new ListTag();
            for (MobEffectInstance effect : player.getActiveEffects()) {
                effects.add(effect.save());
            }
            tag.put("active_effects", effects);
        }
        if (parts.contains(Part.LOCATION)) {
            Location location = Teleports.locationOf(player);
            CompoundTag loc = new CompoundTag();
            loc.putString("dimension", location.dimension());
            loc.putDouble("x", location.x());
            loc.putDouble("y", location.y());
            loc.putDouble("z", location.z());
            loc.putFloat("yaw", location.yaw());
            loc.putFloat("pitch", location.pitch());
            tag.put("SuLocation", loc);
        }
        return tag;
    }

    public static PlayerNbt.Contents contents(ServerPlayer player) {
        ItemStack[] main = new ItemStack[PlayerNbt.MAIN];
        for (int i = 0; i < main.length; i++) {
            main[i] = player.getInventory().items.get(i).copy();
        }
        ItemStack[] armor = new ItemStack[PlayerNbt.ARMOR];
        for (int i = 0; i < armor.length; i++) {
            armor[i] = player.getInventory().armor.get(i).copy();
        }
        return new PlayerNbt.Contents(main, armor, player.getInventory().offhand.get(0).copy());
    }

    /** Applies the requested parts that the tag contains. Parts missing from the tag are left alone. */
    public static void apply(ServerPlayer player, CompoundTag tag, Set<Part> parts) {
        HolderLookup.Provider registries = player.server.registryAccess();
        if (parts.contains(Part.INVENTORY) && tag.contains("Inventory", Tag.TAG_LIST)) {
            setContents(player, PlayerNbt.readInventory(tag, registries));
        }
        if (parts.contains(Part.ENDER_CHEST) && tag.contains("EnderItems", Tag.TAG_LIST)) {
            ItemStack[] ender = PlayerNbt.readEnder(tag, registries);
            for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) {
                player.getEnderChestInventory().setItem(i, i < ender.length ? ender[i] : ItemStack.EMPTY);
            }
        }
        if (parts.contains(Part.EXPERIENCE) && tag.contains("XpLevel")) {
            player.experienceProgress = tag.getFloat("XpP");
            player.totalExperience = tag.getInt("XpTotal");
            // setExperienceLevels also forces the next experience packet.
            player.setExperienceLevels(tag.getInt("XpLevel"));
        }
        if (parts.contains(Part.HEALTH) && tag.contains("Health")) {
            player.setHealth(Math.max(1.0F, Math.min(player.getMaxHealth(), tag.getFloat("Health"))));
        }
        if (parts.contains(Part.HUNGER) && tag.contains("foodLevel")) {
            player.getFoodData().readAdditionalSaveData(tag);
        }
        if (parts.contains(Part.EFFECTS) && tag.contains("active_effects", Tag.TAG_LIST)) {
            player.removeAllEffects();
            ListTag effects = tag.getList("active_effects", Tag.TAG_COMPOUND);
            for (int i = 0; i < effects.size(); i++) {
                MobEffectInstance effect = MobEffectInstance.load(effects.getCompound(i));
                if (effect != null) {
                    player.addEffect(effect);
                }
            }
        }
        if (parts.contains(Part.LOCATION) && tag.contains("SuLocation", Tag.TAG_COMPOUND)) {
            CompoundTag loc = tag.getCompound("SuLocation");
            Teleports.teleport(player, new Location(loc.getString("dimension"), loc.getDouble("x"), loc.getDouble("y"),
                loc.getDouble("z"), loc.getFloat("yaw"), loc.getFloat("pitch")), false);
        }
        sync(player);
    }

    /** Applies the same parts to an offline player's saved data. */
    public static void applyOffline(CompoundTag saved, CompoundTag state, Set<Part> parts) {
        List<String> keys = new ArrayList<>();
        if (parts.contains(Part.INVENTORY)) {
            keys.add("Inventory");
        }
        if (parts.contains(Part.ENDER_CHEST)) {
            keys.add("EnderItems");
        }
        if (parts.contains(Part.EXPERIENCE)) {
            keys.addAll(List.of("XpLevel", "XpP", "XpTotal"));
        }
        if (parts.contains(Part.HEALTH)) {
            keys.add("Health");
        }
        if (parts.contains(Part.HUNGER)) {
            keys.addAll(List.of("foodLevel", "foodSaturationLevel", "foodExhaustionLevel", "foodTickTimer"));
        }
        if (parts.contains(Part.EFFECTS)) {
            keys.add("active_effects");
        }
        for (String key : keys) {
            Tag value = state.get(key);
            if (value != null) {
                saved.put(key, value.copy());
            }
        }
        if (parts.contains(Part.LOCATION) && state.contains("SuLocation", Tag.TAG_COMPOUND)) {
            CompoundTag loc = state.getCompound("SuLocation");
            ListTag pos = new ListTag();
            pos.add(net.minecraft.nbt.DoubleTag.valueOf(loc.getDouble("x")));
            pos.add(net.minecraft.nbt.DoubleTag.valueOf(loc.getDouble("y")));
            pos.add(net.minecraft.nbt.DoubleTag.valueOf(loc.getDouble("z")));
            saved.put("Pos", pos);
            saved.putString("Dimension", loc.getString("dimension"));
        }
    }

    public static void setContents(ServerPlayer player, PlayerNbt.Contents contents) {
        for (int i = 0; i < PlayerNbt.MAIN; i++) {
            player.getInventory().items.set(i, contents.main()[i].copy());
        }
        for (int i = 0; i < PlayerNbt.ARMOR; i++) {
            player.getInventory().armor.set(i, contents.armor()[i].copy());
        }
        player.getInventory().offhand.set(0, contents.offhand().copy());
    }

    public static boolean isEmpty(PlayerNbt.Contents contents) {
        for (int i = 0; i <= 40; i++) {
            if (!contents.get(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Pushes inventory and equipment to the client and to players watching. */
    public static void sync(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }
}
