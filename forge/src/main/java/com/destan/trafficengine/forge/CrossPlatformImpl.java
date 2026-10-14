package com.destan.trafficengine.forge;

import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.config.ModCommonConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class CrossPlatformImpl {
    
    public static void registerConfig() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModCommonConfig.SPEC, TrafficEngine.MOD_ID + "-common.toml");
    }
}
