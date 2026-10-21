package com.arthou.smputilities.mc;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
//#if MC>=1.20.5
import net.minecraft.nbt.NbtOps;
//#endif

/** Potion effects to and from the player save format of the running version. */
public final class Effects {
    /** Key vanilla saves effects under ("ActiveEffects" before 1.20.2). */
    //#if MC>=1.20.2
    public static final String SAVE_KEY = "active_effects";
    //#else
    //$$ public static final String SAVE_KEY = "ActiveEffects";
    //#endif

    private Effects() {
    }

    public static ListTag save(ServerPlayer player, RegistryAccess registries) {
        ListTag list = new ListTag();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            //#if MC>=1.20.5
            MobEffectInstance.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), effect).result().ifPresent(list::add);
            //#else
            //$$ list.add(effect.save(new CompoundTag()));
            //#endif
        }
        return list;
    }

    public static boolean hasSaved(CompoundTag tag) {
        return Nbt.hasList(tag, "active_effects") || Nbt.hasList(tag, "ActiveEffects");
    }

    /** The saved effect list under either key. */
    public static ListTag savedList(CompoundTag tag) {
        return Nbt.hasList(tag, "active_effects") ? Nbt.getList(tag, "active_effects") : Nbt.getList(tag, "ActiveEffects");
    }

    /** Replaces the player's effects with the saved ones. */
    public static void apply(ServerPlayer player, CompoundTag tag, RegistryAccess registries) {
        player.removeAllEffects();
        for (CompoundTag entry : Nbt.compounds(savedList(tag))) {
            MobEffectInstance effect = load(entry, registries);
            if (effect != null) {
                player.addEffect(effect);
            }
        }
    }

    private static MobEffectInstance load(CompoundTag tag, RegistryAccess registries) {
        //#if MC>=1.20.5
        return MobEffectInstance.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result().orElse(null);
        //#else
        //$$ return MobEffectInstance.load(tag);
        //#endif
    }
}
