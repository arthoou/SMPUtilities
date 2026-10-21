package com.arthou.smputilities;

import com.arthou.smputilities.core.util.SuLog;
import com.arthou.smputilities.mc.Ids;
import com.arthou.smputilities.platform.Platform;
import com.arthou.smputilities.server.SuServer;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * SMPutilities: one mod for SMP and roleplay servers. This class only wires
 * things up; each system lives in its own package, and each loader's entry
 * point calls {@link #init} with its {@link Platform}.
 */
public final class SmpUtilities {
    public static final String MOD_ID = "smputilities";
    public static final Logger LOGGER = LogUtils.getLogger();

    private SmpUtilities() {
    }

    public static void init(Platform platform) {
        Platform.install(platform);
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
        LOGGER.info("SMPutilities loaded on {}", platform.loader());
    }

    public static ResourceLocation id(String path) {
        return Ids.mod(path);
    }
}
