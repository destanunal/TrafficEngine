package com.destan.trafficengine.client.model.fabric;

import com.destan.trafficengine.client.model.RoadStrokeBakedModel;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class RoadStrokeModelsImpl {
    public static BakedModel wrap(BakedModel original,TextureAtlasSprite white) {return new StrokeModel(original,white);}
    public static void refresh(net.minecraft.world.level.block.entity.BlockEntity entity) {}
    private static final class StrokeModel extends RoadStrokeBakedModel implements FabricBakedModel {
        private RenderMaterial markingMaterial;
        StrokeModel(BakedModel original,TextureAtlasSprite white) {super(original,white);}
        @Override public boolean isVanillaAdapter() {return false;}
        @Override public void emitBlockQuads(BlockAndTintGetter level,BlockState state,BlockPos pos,Supplier<RandomSource> random,RenderContext context) {
            float[] vertices=com.destan.trafficengine.item.RoadMarkingMesh.renderVertices(level,pos,state);
            context.pushTransform(quad->vertices.length==0 || quad.colorIndex()!=1);
            context.bakedModelConsumer().accept(original);
            context.popTransform();
            if (vertices.length==0) return;
            if (markingMaterial==null) {
                var renderer=RendererAccess.INSTANCE.getRenderer();
                if (renderer==null) return;
                markingMaterial=renderer.materialFinder().find();
            }
            for(var quad:decorate(java.util.List.of(),state,null,vertices))
                context.getEmitter().fromVanilla(quad,markingMaterial,null).emit();
        }
    }
}
