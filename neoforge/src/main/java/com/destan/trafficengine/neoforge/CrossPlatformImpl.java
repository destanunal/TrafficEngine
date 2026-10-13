package com.destan.trafficengine.neoforge;

import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.config.ModCommonConfig;
import net.neoforged.fml.config.ModConfig;

public final class CrossPlatformImpl {

    public static void registerConfig() {
        TrafficEngineNeoForge.container.registerConfig(ModConfig.Type.COMMON, ModCommonConfig.SPEC, TrafficEngine.MOD_ID + "-common.toml");
    }
}
