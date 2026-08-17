package com.destan.trafficengine.init;

import com.destan.trafficengine.TrafficEngine;
import dev.architectury.event.events.common.LifecycleEvent;

public class ServerInit {

    public static void init() {            
        LifecycleEvent.SETUP.register(() -> {
            TrafficEngine.LOGGER.info("Welcome to the TRAFFİCENGİNE mod by MRJULSEN.");
        });
    }
    
}
