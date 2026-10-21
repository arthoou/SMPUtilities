package com.arthou.smputilities.inventory;

import com.arthou.smputilities.core.teleport.Location;
import com.arthou.smputilities.mc.Effects;
import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Nbt;
import com.arthou.smputilities.teleport.Teleports;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Captures and applies parts of a player's state as NBT. Applying goes
 * through the normal setters, which keep the client in sync, instead of
 * {@code Entity#load} (that would also move the player without telling the
 * client).
 *
 * <p>Keys follow the vanilla player save format of the running version, so a
 * captured tag can be upgraded by the game's data fixers after a Minecraft
 * update, and full vanilla player tags (old Inventory Rollback snapshots)
 * apply the same way.
 */
public final class PlayerState {
    public enum Part { INVENTORY, ENDER_CHEST, EXPERIENCE, HEALTH, HUNGER, EFFECTS, LOCATION }

    public static final Set<Part> ALL_BUT_LOCATION = EnumSet.complementOf(EnumSet.of(Part.LOCATION));
    public static final Set<Part> ALL = EnumSet.allOf(Part.class);

    private static final List<String> FOOD_KEYS = List.of("foodLevel", "foodSaturationLevel", "foodExhaustionLevel", "foodTickTimer");

    private PlayerState() {
    }

    public static CompoundTag capture(ServerPlayer player, Set<Part> parts) {
        RegistryAccess registries = Mc.server(player).registryAccess();
        CompoundTag tag = new CompoundTag();
        if (parts.contains(Part.INVENTORY)) {
            PlayerNbt.writeInventory(tag, PlayerNbt.contents(player), registries);
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
            tag.putInt("foodLevel", player.getFoodData().getFoodLevel());
            tag.putFloat("foodSaturationLevel", player.getFoodData().getSaturationLevel());
        }
        if (parts.contains(Part.EFFECTS)) {
            tag.put(Effects.SAVE_KEY, Effects.save(player, registries));
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
        Nbt.addCurrentDataVersion(tag);
        return tag;
    }

    /** Applies the requested parts that the tag contains. Parts missing from the tag are left alone. */
    public static void apply(ServerPlayer player, CompoundTag tag, Set<Part> parts) {
        RegistryAccess registries = Mc.server(player).registryAccess();
        if (parts.contains(Part.INVENTORY) && hasInventory(tag)) {
            PlayerNbt.setContents(player, PlayerNbt.readInventory(tag, registries));
        }
        if (parts.contains(Part.ENDER_CHEST) && Nbt.hasList(tag, "EnderItems")) {
            ItemStack[] ender = PlayerNbt.readEnder(tag, registries);
            for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) {
                player.getEnderChestInventory().setItem(i, i < ender.length ? ender[i] : ItemStack.EMPTY);
            }
        }
        if (parts.contains(Part.EXPERIENCE) && Nbt.has(tag, "XpLevel")) {
            player.experienceProgress = Nbt.getFloat(tag, "XpP", 0);
            player.totalExperience = Nbt.getInt(tag, "XpTotal", 0);
            // setExperienceLevels also forces the next experience packet.
            player.setExperienceLevels(Nbt.getInt(tag, "XpLevel", 0));
        }
        if (parts.contains(Part.HEALTH) && Nbt.has(tag, "Health")) {
            player.setHealth(Math.max(1.0F, Math.min(player.getMaxHealth(), Nbt.getFloat(tag, "Health", 20))));
        }
        if (parts.contains(Part.HUNGER) && Nbt.has(tag, "foodLevel")) {
            player.getFoodData().setFoodLevel(Nbt.getInt(tag, "foodLevel", 20));
            player.getFoodData().setSaturation(Nbt.getFloat(tag, "foodSaturationLevel", 5));
        }
        if (parts.contains(Part.EFFECTS) && Effects.hasSaved(tag)) {
            Effects.apply(player, tag, registries);
        }
        if (parts.contains(Part.LOCATION) && Nbt.hasCompound(tag, "SuLocation")) {
            CompoundTag loc = Nbt.getCompound(tag, "SuLocation");
            Teleports.teleport(player, new Location(Nbt.getString(loc, "dimension"), Nbt.getDouble(loc, "x", 0), Nbt.getDouble(loc, "y", 0),
                Nbt.getDouble(loc, "z", 0), Nbt.getFloat(loc, "yaw", 0), Nbt.getFloat(loc, "pitch", 0)), false);
        }
        sync(player);
    }

    private static boolean hasInventory(CompoundTag tag) {
        return Nbt.hasList(tag, "Inventory") || Nbt.hasCompound(tag, "equipment");
    }

    /** Applies the same parts to an offline player's saved data, written in the running game's layout. */
    public static void applyOffline(CompoundTag saved, CompoundTag state, Set<Part> parts, RegistryAccess registries) {
        if (parts.contains(Part.INVENTORY) && hasInventory(state)) {
            PlayerNbt.writeInventory(saved, PlayerNbt.readInventory(state, registries), registries);
        }
        if (parts.contains(Part.ENDER_CHEST) && Nbt.hasList(state, "EnderItems")) {
            saved.put("EnderItems", Nbt.getList(state, "EnderItems").copy());
        }
        if (parts.contains(Part.EXPERIENCE)) {
            copy(state, saved, List.of("XpLevel", "XpP", "XpTotal"));
        }
        if (parts.contains(Part.HEALTH)) {
            copy(state, saved, List.of("Health"));
        }
        if (parts.contains(Part.HUNGER)) {
            copy(state, saved, FOOD_KEYS);
        }
        if (parts.contains(Part.EFFECTS) && Effects.hasSaved(state)) {
            saved.remove("ActiveEffects");
            saved.remove("active_effects");
            saved.put(Effects.SAVE_KEY, Effects.savedList(state).copy());
        }
        if (parts.contains(Part.LOCATION) && Nbt.hasCompound(state, "SuLocation")) {
            CompoundTag loc = Nbt.getCompound(state, "SuLocation");
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(Nbt.getDouble(loc, "x", 0)));
            pos.add(DoubleTag.valueOf(Nbt.getDouble(loc, "y", 0)));
            pos.add(DoubleTag.valueOf(Nbt.getDouble(loc, "z", 0)));
            saved.put("Pos", pos);
            saved.putString("Dimension", Nbt.getString(loc, "dimension"));
        }
    }

    private static void copy(CompoundTag from, CompoundTag to, List<String> keys) {
        for (String key : keys) {
            Tag value = from.get(key);
            if (value != null) {
                to.put(key, value.copy());
            }
        }
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
