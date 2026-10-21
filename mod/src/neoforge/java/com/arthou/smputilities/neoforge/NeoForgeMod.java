package com.arthou.smputilities.neoforge;

import com.arthou.smputilities.SmpUtilities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** NeoForge entry point. */
@Mod(SmpUtilities.MOD_ID)
public final class NeoForgeMod {
    public NeoForgeMod(IEventBus modBus, ModContainer container) {
        SmpUtilities.init(new NeoForgePlatform());
        modBus.addListener(NeoForgeNetwork::register);
        NeoForgeEvents.register();
        if (NeoForgePlatform.physicalClient()) {
            NeoForgeClient.init(modBus);
        }
    }
}
