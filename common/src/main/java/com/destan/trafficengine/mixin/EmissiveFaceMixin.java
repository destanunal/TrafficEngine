package com.destan.trafficengine.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import com.destan.trafficengine.client.model.EmissiveFace;
@Mixin(BlockElementFace.class)
public class EmissiveFaceMixin implements EmissiveFace {
    @Unique private boolean trafficengine$emissive;
    @Unique private float[] trafficengine$vertices;
    @Unique private boolean trafficengine$smoothShade;
    public boolean trafficengine$smoothShade(){return trafficengine$smoothShade;}
    public void trafficengine$setSmoothShade(boolean value){trafficengine$smoothShade=value;}
    @Unique private boolean trafficengine$matchesStraightShade;
    public boolean trafficengine$matchesStraightShade() { return trafficengine$matchesStraightShade; }
    public void trafficengine$setMatchesStraightShade(boolean value) { trafficengine$matchesStraightShade = value; }
    public float[] trafficengine$getVertices() { return trafficengine$vertices; }
    public void trafficengine$setVertices(float[] vertices) { trafficengine$vertices = vertices; }
    public boolean trafficengine$isEmissive() {return trafficengine$emissive;}
    public void trafficengine$setEmissive(boolean value) {trafficengine$emissive=value;}
}
