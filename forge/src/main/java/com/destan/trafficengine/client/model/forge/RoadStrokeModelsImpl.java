package com.destan.trafficengine.client.model.forge;

import com.destan.trafficengine.client.model.RoadStrokeBakedModel;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

public final class RoadStrokeModelsImpl {
    public static BakedModel wrap(BakedModel original,TextureAtlasSprite white) {return new StrokeModel(original,white);}
    public static void refresh(net.minecraft.world.level.block.entity.BlockEntity entity) {
        entity.requestModelDataUpdate();
    }
    private static final class StrokeModel extends RoadStrokeBakedModel {
        private static final ModelProperty<float[]> STROKE=new ModelProperty<>();
        StrokeModel(BakedModel original,TextureAtlasSprite white) {super(original,white);}
        @Override public ModelData getModelData(BlockAndTintGetter level,BlockPos pos,BlockState state,ModelData data) {
            float[] vertices=com.destan.trafficengine.item.RoadMarkingMesh.renderVertices(level,pos,state);
            return original.getModelData(level,pos,state,data).derive().with(STROKE,vertices).build();
        }
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType type) {
            return decorate(original.getQuads(state,side,random,data,type),state,side,data.get(STROKE));
        }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data) {
            return ChunkRenderTypeSet.of(RenderType.cutout());
        }
    }
}
