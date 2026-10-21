package com.arthou.smputilities.fabric;

import com.arthou.smputilities.SmpUtilities;
import net.fabricmc.api.ModInitializer;

/** Fabric entry point (both sides). */
public final class FabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        SmpUtilities.init(new FabricPlatform());
        FabricNetwork.register();
        FabricEvents.register();
    }
}
