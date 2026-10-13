package com.destan.trafficengine.fabric;

import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.config.ModCommonConfig;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import net.neoforged.fml.config.ModConfig;

public final class CrossPlatformImpl {
    
    public static void registerConfig() {
        NeoForgeConfigRegistry.INSTANCE.register(TrafficEngine.MOD_ID, ModConfig.Type.COMMON, ModCommonConfig.SPEC, TrafficEngine.MOD_ID + "-common.toml");
    }
}
