package com.destan.trafficengine.data;

import com.destan.trafficengine.block.WritableTrafficSign;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;

public final class SignTextConfig {
    public static final int DEFAULT_LINE_HEIGHT = 10;

    public static float facingRotation(BlockState state) {
        Direction facing = state.getValue(WritableTrafficSign.FACING);
        return (facing.getAxis() == Direction.Axis.X ? facing.getOpposite() : facing).toYRot();
    }

    public record ConfiguredLineData(float xOffset, float yOffset, Vec2 minScale, Vec2 maxScale, float maxLineWidth, float lineHeightScale, int color) {}

    public record WritableSignConfig(ConfiguredLineData[] lineData, boolean renderBack, float xCenterOffset, float y, float scale,
            float yRot, float berX, float berY, float berZ, ToDoubleFunction<BlockState> blockEntityRendererRotation, int berColor) {
        public static final int DEFAULT_SCALE = 96;

        public WritableSignConfig withColor(int color) {
            return new WritableSignConfig(lineData, renderBack, xCenterOffset, y, scale, yRot, berX, berY, berZ, blockEntityRendererRotation, color);
        }

        public float scaleX(int index, int width) {
            var line = lineData[index];
            return Math.min(line.maxScale.x, line.maxLineWidth * scale / Math.max(1, width));
        }

        public float scaleY(int index, int width) {
            var line = lineData[index];
            return Math.max(line.minScale.y, Math.min(line.maxScale.y, line.maxLineWidth * scale / Math.max(1, width)));
        }

        public float baseline(int index, float sizeY) {
            float center = lineData[index].yOffset * scale - DEFAULT_LINE_HEIGHT * lineData[0].lineHeightScale * .5F;
            for (int i = 0; i < index; i++) center += lineData[i].lineHeightScale * DEFAULT_LINE_HEIGHT;
            return center + DEFAULT_LINE_HEIGHT * (lineData[index].lineHeightScale - sizeY) * .5F;
        }
    }
}
