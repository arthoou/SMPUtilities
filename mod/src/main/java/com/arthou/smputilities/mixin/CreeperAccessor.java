package com.arthou.smputilities.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The prank creeper explodes with radius 0, which breaks and hurts nothing. */
@Mixin(Creeper.class)
public interface CreeperAccessor {
    @Accessor("explosionRadius")
    void smputilities$setExplosionRadius(int radius);
}
