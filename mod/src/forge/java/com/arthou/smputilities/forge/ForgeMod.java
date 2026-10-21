package com.arthou.smputilities.forge;

import com.arthou.smputilities.SmpUtilities;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//#if MC<1.20.2
//$$ import net.minecraftforge.fml.IExtensionPoint;
//$$ import net.minecraftforge.fml.ModLoadingContext;
//$$ import net.minecraftforge.network.NetworkConstants;
//#endif

/** Forge entry point. */
@Mod(SmpUtilities.MOD_ID)
public final class ForgeMod {
    @SuppressWarnings("removal")
    public ForgeMod() {
        //#if MC>=1.21.6
        ForgeBus.init(FMLJavaModLoadingContext.get().getModBusGroup());
        //#else
        //$$ ForgeBus.init(FMLJavaModLoadingContext.get().getModEventBus());
        //#endif
        //#if MC<1.20.2
        //$$ // Players without SMPutilities may join; newer Forge reads this from mods.toml.
        //$$ ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
        //$$     () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));
        //#endif
        SmpUtilities.init(new ForgePlatform());
        ForgeNetwork.register();
        ForgeEvents.register();
        if (ForgePlatform.physicalClient()) {
            ForgeClient.init();
        }
    }
}
