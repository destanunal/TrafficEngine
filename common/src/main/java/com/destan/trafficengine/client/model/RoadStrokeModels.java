package com.destan.trafficengine.client.model;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public final class RoadStrokeModels {
    private RoadStrokeModels() {}
    @ExpectPlatform public static void refresh(net.minecraft.world.level.block.entity.BlockEntity entity) {throw new AssertionError();}
    @ExpectPlatform public static BakedModel wrap(BakedModel original,TextureAtlasSprite white) {throw new AssertionError();}
}
