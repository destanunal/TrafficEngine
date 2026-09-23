package com.destan.trafficengine;

import dev.architectury.injectables.annotations.ExpectPlatform;

public final class CrossPlatform {
        
    @ExpectPlatform
    public static void registerConfig() {
        throw new AssertionError();
    }
}
