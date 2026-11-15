package com.destan.trafficengine.client.model.neoforge;
import com.destan.trafficengine.client.model.RoadSignJoinedBakedModel;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
public final class RoadSignModelsImpl {
    public static BakedModel wrap(BakedModel original,BlockState state) {return new JoinedModel(original,state);}
    private static final class JoinedModel extends RoadSignJoinedBakedModel {
        private static final ModelProperty<Integer> JOINS=new ModelProperty<>();
        private static final ModelProperty<Integer> LIGHT=new ModelProperty<>();
        JoinedModel(BakedModel original,BlockState state) {super(original,state);}
        @Override public ModelData getModelData(BlockAndTintGetter level,BlockPos pos,BlockState state,ModelData data) {
            return original.getModelData(level,pos,state,data).derive().with(JOINS,connections(level,pos)).with(LIGHT,surfaceLight(level,pos,state)).build();
        }
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType type) {
            Integer joins=data.get(JOINS);
            Integer light=data.get(LIGHT);
            return light==null?original.getQuads(state,side,random,data,type):lit(side,joins==null?0:joins,light);
        }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data) {
            return original.getRenderTypes(state,random,data);
        }
    }
}
