package com.arthou.smputilities.mc;

import com.arthou.smputilities.SmpUtilities;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
//#if MC>=1.19.3
import net.minecraft.core.registries.Registries;
//#else
//$$ import net.minecraft.core.Registry;
//#endif

/** Resource ids and dimension keys across versions (ResourceLocation is Identifier from 1.21.11). */
public final class Ids {
    private Ids() {
    }

    public static ResourceLocation of(String namespace, String path) {
        //#if MC>=1.21
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
        //#else
        //$$ return new ResourceLocation(namespace, path);
        //#endif
    }

    public static ResourceLocation mod(String path) {
        return of(SmpUtilities.MOD_ID, path);
    }

    /** @return the id, or null when the text is not a valid id */
    public static ResourceLocation parse(String text) {
        return text == null ? null : ResourceLocation.tryParse(text.trim());
    }

    /** The id of a registry key, e.g. "minecraft:the_nether". */
    public static String key(ResourceKey<?> key) {
        //#if MC>=1.21.11
        return key.identifier().toString();
        //#else
        //$$ return key.location().toString();
        //#endif
    }

    public static ResourceLocation location(ResourceKey<?> key) {
        //#if MC>=1.21.11
        return key.identifier();
        //#else
        //$$ return key.location();
        //#endif
    }

    /** Dimension key for "minecraft:overworld" style ids; null when invalid. */
    public static ResourceKey<Level> dimension(String id) {
        ResourceLocation location = parse(id);
        if (location == null) {
            return null;
        }
        //#if MC>=1.19.3
        return ResourceKey.create(Registries.DIMENSION, location);
        //#else
        //$$ return ResourceKey.create(Registry.DIMENSION_REGISTRY, location);
        //#endif
    }

    public static String dimensionOf(Level level) {
        return key(level.dimension());
    }
}
