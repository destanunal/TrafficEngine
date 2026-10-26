package com.destan.trafficengine.client;
import com.destan.trafficengine.block.PedestrianRailingBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
public final class PedestrianRailingParticles {
    private static final ResourceLocation GRAY=new ResourceLocation("trafficengine","block/sign");
    private static final ResourceLocation CHARCOAL=new ResourceLocation("trafficengine","block/pedestrian_railing_charcoal");
    public static boolean supports(BlockState state){return state.getBlock() instanceof PedestrianRailingBlock||state.getBlock() instanceof com.destan.trafficengine.block.PedestrianRailingGateBlock||state.getBlock() instanceof com.destan.trafficengine.block.SlopedPedestrianRailingBlock;}
    private PedestrianRailingParticles(){}
    /** Reuse the normal straight railing's state and cached outline, including its current colour/sink. */
    public static BlockState straightParticleState(BlockState normal,BlockState sloped){
        return normal            .setValue(PedestrianRailingBlock.SINK,sloped.getValue(PedestrianRailingBlock.SINK))
            .setValue(PedestrianRailingBlock.GRAY,sloped.getValue(PedestrianRailingBlock.GRAY));
    }
    public static ResourceLocation texture(BlockState state){return state.getValue(PedestrianRailingBlock.GRAY)?GRAY:CHARCOAL;}
}
