package com.destan.trafficengine.client.model.fabric;
import com.destan.trafficengine.client.model.RoadSignJoinedBakedModel;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
public final class RoadSignModelsImpl {
    public static BakedModel wrap(BakedModel original,BlockState state) {return new JoinedModel(original,state);}
    private static final class JoinedModel extends RoadSignJoinedBakedModel implements FabricBakedModel {
        private RenderMaterial material;
        JoinedModel(BakedModel original,BlockState state) {super(original,state);}
        @Override public boolean isVanillaAdapter() {return false;}
        @Override public void emitBlockQuads(BlockAndTintGetter level,BlockState state,BlockPos pos,Supplier<RandomSource> random,RenderContext context) {
            int joins=connections(level,pos);
            if(material==null) {
                var renderer=RendererAccess.INSTANCE.getRenderer();
                if(renderer==null)return;
                material=renderer.materialFinder().ambientOcclusion(net.fabricmc.fabric.api.util.TriState.FALSE).find();
            }
            var emitter=context.getEmitter();
            int light=surfaceLight(level,pos,state);
            for(Direction side:SIDES)for(var quad:joined(side,joins)) {
                emitter.fromVanilla(quad,material,side);
                if(quad.getTintIndex()==110)for(int i=0;i<4;i++)emitter.lightmap(i,light);
                emitter.emit();
            }
        }
    }
}
