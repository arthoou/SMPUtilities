package com.arthou.smputilities;

import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.network.SuNetwork;
import com.arthou.smputilities.platform.NeoForgeEvents;
import com.arthou.smputilities.server.SuServer;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * SMPutilities: one mod for SMP and roleplay servers. This class only wires
 * things up; each system lives in its own package.
 */
@Mod(SmpUtilities.MOD_ID)
public final class SmpUtilities {
    public static final String MOD_ID = "smputilities";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SmpUtilities(IEventBus modBus, ModContainer container) {
        SuLog.install(new SuLog.Sink() {
            @Override
            public void info(String message) {
                LOGGER.info(message);
            }

            @Override
            public void warn(String message, Throwable error) {
                LOGGER.warn(message, error);
            }

            @Override
            public void error(String message, Throwable error) {
                LOGGER.error(message, error);
            }
        });
        // Loads config/smputilities/server.json (creating it with defaults) before anything reads it.
        SuServer.config();
        modBus.addListener(SuNetwork::register);
        NeoForgeEvents.register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.arthou.smputilities.client.SuClient.init(modBus, container);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
