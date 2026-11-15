package com.destan.trafficengine.client.model;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;
public final class RoadSignModels {
    private RoadSignModels() {}
    @ExpectPlatform public static BakedModel wrap(BakedModel model,BlockState state) {throw new AssertionError();}
}
