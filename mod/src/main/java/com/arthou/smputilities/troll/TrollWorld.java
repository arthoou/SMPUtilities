package com.arthou.smputilities.troll;

import com.arthou.smputilities.mc.Mc;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
//#if MC<1.19.4
//$$ import net.minecraft.world.level.Explosion;
//#endif

/** World and damage helpers for the pranks, where the game's API differs between versions. */
final class TrollWorld {
    private TrollWorld() {
    }

    /** The height a player can stand on at x, z. */
    static int surface(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    static int maxY(Level level) {
        return Mc.minY(level) + level.getHeight() - 1;
    }

    static boolean inside(Level level, BlockPos pos) {
        return pos.getY() >= Mc.minY(level) && pos.getY() <= maxY(level);
    }

    /** The nearest point just inside the world border. */
    static Vec3 nearestEdge(WorldBorder border, double x, double z) {
        double toWest = x - border.getMinX();
        double toEast = border.getMaxX() - x;
        double toNorth = z - border.getMinZ();
        double toSouth = border.getMaxZ() - z;
        double nearest = Math.min(Math.min(toWest, toEast), Math.min(toNorth, toSouth));
        double insideX = Math.max(border.getMinX() + 1.5, Math.min(border.getMaxX() - 1.5, x));
        double insideZ = Math.max(border.getMinZ() + 1.5, Math.min(border.getMaxZ() - 1.5, z));
        if (nearest == toWest) {
            return new Vec3(border.getMinX() + 1.5, 0, insideZ);
        }
        if (nearest == toEast) {
            return new Vec3(border.getMaxX() - 1.5, 0, insideZ);
        }
        if (nearest == toNorth) {
            return new Vec3(insideX, 0, border.getMinZ() + 1.5);
        }
        return new Vec3(insideX, 0, border.getMaxZ() - 1.5);
    }

    static void ignite(ServerPlayer player, int seconds) {
        //#if MC>=1.20.6
        player.igniteForSeconds(seconds);
        //#else
        //$$ player.setSecondsOnFire(seconds);
        //#endif
    }

    /** An explosion that hurts but breaks no blocks. */
    static void explode(ServerLevel level, double x, double y, double z, float power) {
        //#if MC>=1.19.4
        level.explode(null, x, y, z, power, Level.ExplosionInteraction.NONE);
        //#else
        //$$ level.explode(null, x, y, z, power, Explosion.BlockInteraction.NONE);
        //#endif
    }

    static void kill(ServerPlayer player) {
        //#if MC>=1.21.2
        player.kill(Mc.level(player));
        //#else
        //$$ player.kill();
        //#endif
    }

    /** An anvil that falls from {@code at} (which must be air) and hurts what it lands on. */
    static FallingBlockEntity fallingAnvil(ServerLevel level, BlockPos at) {
        FallingBlockEntity anvil = FallingBlockEntity.fall(level, at, Blocks.ANVIL.defaultBlockState());
        anvil.setHurtsEntities(2.0F, 40);
        anvil.dropItem = false;
        return anvil;
    }

    /** Takes away the anvil block a prank anvil became when it landed. */
    static void removeLandedAnvil(ServerLevel level, FallingBlockEntity anvil) {
        if (anvil.isAlive()) {
            anvil.discard();
            return;
        }
        BlockPos pos = anvil.blockPosition();
        for (BlockPos check : new BlockPos[] {pos, pos.below(), pos.above()}) {
            if (level.getBlockState(check).getBlock() instanceof AnvilBlock) {
                level.setBlock(check, Blocks.AIR.defaultBlockState(), 3);
                return;
            }
        }
    }
}
