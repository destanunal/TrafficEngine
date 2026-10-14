package com.destan.trafficengine.fabric;

import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.config.ModCommonConfig;
import fuzs.forgeconfigapiport.impl.config.ForgeConfigRegistryImpl;
import net.minecraftforge.fml.config.ModConfig;

public final class CrossPlatformImpl {
    
    public static void registerConfig() {
        ForgeConfigRegistryImpl.INSTANCE.register(TrafficEngine.MOD_ID, ModConfig.Type.COMMON, ModCommonConfig.SPEC, TrafficEngine.MOD_ID + "-common.toml");
    }
}
