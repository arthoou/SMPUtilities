package com.arthou.smputilities.antixray;

import com.arthou.smputilities.core.antixray.XrayRules;
import com.arthou.smputilities.core.config.SuConfig;
import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.mc.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
//#if MC>=1.19.3
import net.minecraft.core.registries.Registries;
//#else
//$$ import net.minecraft.core.Registry;
//#endif

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Turns a dimension's anti-xray settings into {@link XrayRules}, as AntiXray's WorldConfig and controllers do. */
final class XrayRuleBuilder {
    private XrayRuleBuilder() {
    }

    static XrayRules build(ResourceKey<Level> dimension, SuConfig.AntiXray.World world) {
        XrayRules.Mode mode = XrayRules.Mode.byId(world.engineMode);
        if (mode == null) {
            mode = XrayRules.Mode.HIDE;
        }
        Set<Block> hidden = blocks(world.hiddenBlocks);
        Set<Block> replacement = blocks(world.replacementBlocks);
        int size = Block.BLOCK_STATE_REGISTRY.size();
        boolean[] solid = new boolean[size];
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            int id = AntiXray.id(state);
            if (id >= 0 && id < size) {
                solid[id] = solid(state, world.lavaObscures);
            }
        }

        Set<Block> toObfuscate = new LinkedHashSet<>(hidden);
        if (mode != XrayRules.Mode.HIDE) {
            toObfuscate.addAll(replacement);
        }
        boolean[] obfuscate = new boolean[size];
        for (Block block : toObfuscate) {
            // Air is never hidden: it would cause needless block updates and fail in the void.
            if (block.defaultBlockState().isAir()) {
                continue;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                int id = AntiXray.id(state);
                if (id >= 0 && id < size) {
                    obfuscate[id] = true;
                }
            }
        }

        int[] below;
        int[] above;
        if (mode == XrayRules.Mode.HIDE) {
            if (dimension.equals(Level.NETHER)) {
                below = above = ids(Blocks.NETHERRACK);
            } else if (dimension.equals(Level.END)) {
                below = above = ids(Blocks.END_STONE);
            } else {
                below = ids(Blocks.DEEPSLATE);
                above = ids(Blocks.STONE);
            }
        } else {
            Set<Integer> presets = new LinkedHashSet<>();
            for (Block block : hidden) {
                if (!(block instanceof EntityBlock)) {
                    presets.add(AntiXray.id(block.defaultBlockState()));
                }
            }
            below = above = presets.isEmpty() ? ids(Blocks.DIAMOND_ORE) : presets.stream().mapToInt(Integer::intValue).toArray();
        }
        return new XrayRules(mode, world.maxBlockHeight, world.updateRadius, solid, obfuscate, below, above);
    }

    private static int[] ids(Block block) {
        return new int[] {AntiXray.id(block.defaultBlockState())};
    }

    /** AntiXray's test: a full, occluding visual shape, or still lava when lava hides too. */
    private static boolean solid(BlockState state, boolean lavaObscures) {
        if (lavaObscures && state == Blocks.LAVA.defaultBlockState()) {
            return true;
        }
        try {
            return state.canOcclude() && Block.isShapeFullBlock(state.getVisualShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()));
        } catch (RuntimeException e) {
            // Some modded blocks need a real world for their shape.
            return false;
        }
    }

    /** Block ids and #tags in order; "!" in front removes them again. */
    static Set<Block> blocks(List<String> entries) {
        Set<Block> result = new LinkedHashSet<>();
        if (entries == null) {
            return result;
        }
        for (String raw : entries) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String entry = raw.trim();
            boolean remove = entry.startsWith("!");
            if (remove) {
                entry = entry.substring(1).trim();
            }
            boolean tag = entry.startsWith("#");
            ResourceLocation id = Ids.parse(tag ? entry.substring(1) : entry);
            if (id == null) {
                SuLog.warn("Anti-xray: \"" + raw + "\" is not a block id");
                continue;
            }
            Set<Block> matched = new LinkedHashSet<>();
            if (tag) {
                //#if MC>=1.19.3
                TagKey<Block> key = TagKey.create(Registries.BLOCK, id);
                //#else
                //$$ TagKey<Block> key = TagKey.create(Registry.BLOCK_REGISTRY, id);
                //#endif
                for (Block block : BuiltInRegistries.BLOCK) {
                    if (block.defaultBlockState().is(key)) {
                        matched.add(block);
                    }
                }
            } else {
                Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
                if (block == null) {
                    // Blocks of other versions or of mods that are not installed are left out quietly.
                    continue;
                }
                matched.add(block);
            }
            if (remove) {
                result.removeAll(matched);
            } else {
                result.addAll(matched);
            }
        }
        return result;
    }
}
