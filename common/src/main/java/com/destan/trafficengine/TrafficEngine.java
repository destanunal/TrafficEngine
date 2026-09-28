package com.destan.trafficengine;

import com.destan.trafficengine.registry.*;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.destan.trafficengine.client.screen.menu.ModMenuTypes;
import com.destan.trafficengine.data.AgingManager;
import com.destan.trafficengine.init.ClientInitWrapper;
import com.destan.trafficengine.init.ServerInit;
import com.destan.trafficengine.world.ModWorldGen;
import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;

public final class TrafficEngine {
    public static final String MOD_ID = "trafficengine";
    public static final String MOD_NAME = "trafficengine";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static void init() {
        ServerInit.init();
        if (Platform.getEnv() == EnvType.CLIENT) {
            ClientInitWrapper.init();
            AgingManager.init();
        }

        ModBlocks.register();
        ModItems.register();
        ModBlockEntities.register();
        ModMenuTypes.register();
        ModNetworkManager.init();
        ModCreativeModeTab.init();
        ModWorldGen.init();
        ModItemTags.init();
        ModBlockTags.init();
            
        //ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModCommonConfig.SPEC, MOD_ID + "-common.toml");
        CrossPlatform.registerConfig();
    }
}
