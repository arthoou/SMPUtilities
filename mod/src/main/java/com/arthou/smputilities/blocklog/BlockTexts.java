package com.arthou.smputilities.blocklog;

import com.arthou.smputilities.mc.Mc;
import com.arthou.smputilities.mc.Nbt;
import com.arthou.smputilities.mc.Stacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Collection;

/**
 * Block states, block entities and items as text for the block log, in a
 * form that reads back the same on every version: "minecraft:chest[facing=north]"
 * for states, base64 of compressed NBT for block entities and items.
 */
final class BlockTexts {
    private BlockTexts() {
    }

    static String state(BlockState state) {
        StringBuilder out = new StringBuilder(Mc.blockId(state.getBlock()));
        Collection<Property<?>> properties = state.getProperties();
        if (!properties.isEmpty()) {
            out.append('[');
            boolean first = true;
            for (Property<?> property : properties) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(property.getName()).append('=').append(valueName(state, property));
            }
            out.append(']');
        }
        return out.toString();
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    /** The state written by {@link #state}, or null when its block does not exist (any more). */
    static BlockState state(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int bracket = text.indexOf('[');
        Block block = Mc.block(bracket < 0 ? text : text.substring(0, bracket));
        if (block == null) {
            return null;
        }
        BlockState state = block.defaultBlockState();
        if (bracket >= 0 && text.endsWith("]")) {
            for (String pair : text.substring(bracket + 1, text.length() - 1).split(",")) {
                int equals = pair.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                Property<?> property = block.getStateDefinition().getProperty(pair.substring(0, equals));
                if (property != null) {
                    state = with(state, property, pair.substring(equals + 1));
                }
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    /** "minecraft:oak_log[axis=y]" -> "oak_log". */
    static String shortName(String state) {
        if (state == null || state.isEmpty()) {
            return "?";
        }
        int bracket = state.indexOf('[');
        String id = bracket < 0 ? state : state.substring(0, bracket);
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    // ------------------------------------------------------- block entities

    static String blockEntity(BlockEntity entity, RegistryAccess registries) {
        if (entity == null) {
            return "";
        }
        try {
            //#if MC>=1.20.5
            CompoundTag tag = entity.saveWithFullMetadata(registries);
            //#else
            //$$ CompoundTag tag = entity.saveWithFullMetadata();
            //#endif
            return encode(tag);
        } catch (RuntimeException | IOException e) {
            return "";
        }
    }

    static BlockEntity blockEntity(String data, BlockPos pos, BlockState state, RegistryAccess registries) {
        CompoundTag tag = decode(data);
        if (tag == null) {
            return null;
        }
        //#if MC>=1.20.5
        return BlockEntity.loadStatic(pos, state, tag, registries);
        //#else
        //$$ return BlockEntity.loadStatic(pos, state, tag);
        //#endif
    }

    // ----------------------------------------------------------------- items

    /** One of the stack (count 1), as the key items are compared by. */
    static String item(ItemStack stack, RegistryAccess registries) {
        ItemStack one = stack.copy();
        one.setCount(1);
        try {
            return encode(Stacks.save(one, registries));
        } catch (IOException e) {
            return "";
        }
    }

    static ItemStack item(String data, int count, RegistryAccess registries) {
        CompoundTag tag = decode(data);
        if (tag == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = Stacks.load(tag, registries);
        if (!stack.isEmpty()) {
            stack.setCount(count);
        }
        return stack;
    }

    static String itemName(String data) {
        CompoundTag tag = decode(data);
        String id = tag == null ? "?" : Nbt.getString(tag, "id", "?");
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static String encode(CompoundTag tag) throws IOException {
        return Base64.getEncoder().encodeToString(Nbt.compressed(tag));
    }

    private static CompoundTag decode(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return Nbt.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(data)));
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }
}
