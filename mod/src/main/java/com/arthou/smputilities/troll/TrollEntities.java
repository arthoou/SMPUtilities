package com.arthou.smputilities.troll;

import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mixin.CreeperAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
//#if MC>=1.21.2
import net.minecraft.world.entity.EntitySpawnReason;
//#endif

/** Mobs the pranks summon: they cannot be hurt, drop nothing and are removed after a while. */
final class TrollEntities {
    enum Kind { COW, SQUID }

    /** Receives a summoned mob and how many ticks it may stay. */
    interface Sink {
        void add(Entity entity, int ticks);
    }

    private TrollEntities() {
    }

    static void summon(ServerLevel level, Kind kind, double x, double y, double z, int ticks, Sink sink) {
        EntityType<?> type = kind == Kind.COW ? EntityType.COW : EntityType.SQUID;
        Entity entity = create(type, level);
        if (entity == null) {
            return;
        }
        Mc.moveTo(entity, x, y, z);
        entity.setInvulnerable(true);
        if (level.addFreshEntity(entity)) {
            sink.add(entity, ticks);
        }
    }

    /** A creeper that hisses and blows up right away, with an explosion that breaks and hurts nothing. */
    static void harmlessCreeper(ServerLevel level, Vec3 at) {
        Creeper creeper = create(EntityType.CREEPER, level);
        if (creeper == null) {
            return;
        }
        Mc.moveTo(creeper, at.x, at.y, at.z);
        ((CreeperAccessor) creeper).smputilities$setExplosionRadius(0);
        creeper.setInvulnerable(true);
        if (level.addFreshEntity(creeper)) {
            creeper.ignite();
        }
    }

    private static <T extends Entity> T create(EntityType<T> type, ServerLevel level) {
        //#if MC>=1.21.2
        return type.create(level, EntitySpawnReason.TRIGGERED);
        //#else
        //$$ return type.create(level);
        //#endif
    }
}
