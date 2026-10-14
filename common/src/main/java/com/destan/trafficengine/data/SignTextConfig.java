package com.destan.trafficengine.data;
import java.util.function.Function;
import org.joml.Vector3f;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
public final class SignTextConfig {
    public static final int DEFAULT_LINE_HEIGHT=10;
    public record ConfiguredLineData(float xOffset,float yOffset,Vec2 minScale,Vec2 maxScale,float maxLineWidth,float lineHeightScale,int color) {}
    public record WritableSignConfig(ConfiguredLineData[] lineData,boolean renderBack,float xCenterOffset,float y,float scale,float yRot,float berX,float berY,float berZ,Function<BlockState,Float> blockEntityRendererRotation,int berColor) {
        public static final int DEFAULT_SCALE=96;
        public int getLineHeightsUntil(int index) {int height=0; for(int i=0;i<index;i++) height+=(int)(lineData[i].lineHeightScale()*DEFAULT_LINE_HEIGHT); return height;}
        public int getLineOffset(int index,float currentScaleY) {return (int)(lineData[index].lineHeightScale()*DEFAULT_LINE_HEIGHT*0.5F-DEFAULT_LINE_HEIGHT*0.5F*currentScaleY);}
        public Vector3f berTextScale(String text,int fontWidth,float scale,ConfiguredLineData data) {
            float width=fontWidth*scale;
            return new Vector3f(clampScale(width,data.maxLineWidth(),data.minScale().x,data.maxScale().x),clampScale(width,data.maxLineWidth(),data.minScale().y,data.maxScale().y),1);
        }
        private static float clampScale(float width,float available,float min,float max) {return Math.max(min,Math.min(max,available/width));}
    }
}
