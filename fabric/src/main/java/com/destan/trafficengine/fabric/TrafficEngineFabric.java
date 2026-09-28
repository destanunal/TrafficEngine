package com.destan.trafficengine.fabric;

import net.fabricmc.api.ModInitializer;

import com.destan.trafficengine.TrafficEngine;

public final class TrafficEngineFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        TrafficEngine.init();
    }
}
