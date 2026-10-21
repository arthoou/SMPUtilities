package com.arthou.smputilities.fabric;

import net.fabricmc.api.ClientModInitializer;

/** Fabric client entry point; only loaded on a physical client. */
public final class FabricClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricClient.init();
    }
}
