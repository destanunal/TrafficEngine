package com.destan.trafficengine.neoforge;

import com.destan.trafficengine.TrafficEngine;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(TrafficEngine.MOD_ID)
public class TrafficEngineNeoForge {
    public static ModContainer container;

    public TrafficEngineNeoForge(IEventBus modBus, ModContainer modContainer) {
        container = modContainer;
        TrafficEngine.init();
    }
}
