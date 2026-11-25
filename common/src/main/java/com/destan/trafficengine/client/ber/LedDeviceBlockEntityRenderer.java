package com.destan.trafficengine.client.ber;

import com.destan.trafficengine.block.LedDeviceBlock;
import com.destan.trafficengine.block.data.LedDeviceType;
import com.destan.trafficengine.block.entity.LedDeviceBlockEntity;
import com.destan.trafficengine.client.ScrollingText;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class LedDeviceBlockEntityRenderer implements BlockEntityRenderer<LedDeviceBlockEntity> {
    private static final Quaternionf[] FACING_ROTATIONS = new Quaternionf[4];
    static {
        for (Direction direction : Direction.Plane.HORIZONTAL)
            FACING_ROTATIONS[direction.get2DDataValue()] = Axis.YP.rotationDegrees(-direction.toYRot());
    }
    private final Font font;
    private final Matrix4f textMatrix = new Matrix4f();
    public LedDeviceBlockEntityRenderer(BlockEntityRendererProvider.Context context) { this.font = context.getFont(); }

    @Override
    public void render(LedDeviceBlockEntity blockEntity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = blockEntity.getBlockState();
        LedDeviceType type = ((LedDeviceBlock)state.getBlock()).getDeviceType();
        String[] lines = blockEntity.getMessageLines();
        int lineCount = Math.min(type.getMessageSlots(), lines.length);
        long pixels = blockEntity.getPixels();
        if (type.isTrafficDisplay()) {
            int first = 0;
            while (first < lineCount && lines[first].isEmpty()) first++;
            if (first == lineCount) return;
        } else if (pixels == 0) {
            return;
        }
        if (!blockEntity.isLit()) return;
        Direction facing = state.getValue(LedDeviceBlock.FACING);
        boolean hanging = state.getValue(LedDeviceBlock.POST_ABOVE);
        boolean postMounted = state.getValue(LedDeviceBlock.POST_MOUNTED);
        boolean sideMounted = state.getValue(LedDeviceBlock.POST_SIDE);
        boolean compactLedSupport = type == LedDeviceType.LED_LIGHT && postMounted && !sideMounted;
        pose.pushPose();
        double centerY = type == LedDeviceType.LARGE_TRAFFIC_DISPLAY ? 0.5
            : type == LedDeviceType.TRAFFIC_DISPLAY ? 1.0 : 0.5;
        if (hanging) centerY = type == LedDeviceType.LARGE_TRAFFIC_DISPLAY ? 0.5
            : type == LedDeviceType.TRAFFIC_DISPLAY ? 0.0 : 0.4375;
        else if (compactLedSupport) centerY = 0.4375;
        pose.translate(0.5, centerY, 0.5);
        if (type == LedDeviceType.TRAFFIC_DISPLAY && !postMounted) {
            if (facing.getAxis() == Direction.Axis.Z) pose.translate(0.5, 0, 0);
            else pose.translate(0, 0, 0.5);
        }
        // Use the same orientation as Minecraft's sign text: the text plane
        // faces local +Z, then rotates toward the block's visible front.
        pose.mulPose(FACING_ROTATIONS[facing.get2DDataValue()]);
        boolean wallMounted = state.getValue(LedDeviceBlock.WALL_MOUNTED);
        // The black display face is baked into the block model. Only the LED
        // text is dynamic and sits one thousandth of a block in front of it,
        // which removes both the side gap and distant z-fighting.
        pose.translate(0, 0, type.isTrafficDisplay()
            ? (wallMounted ? -0.40525 : 0.15725)
            : (wallMounted ? -0.34275 : 0.15725));
        pose.scale(0.01F, -0.01F, 0.01F);
        Matrix4f matrix = pose.last().pose();
        int color = 0xFF000000 | (blockEntity.getLedColor() & 0xFFFFFF);
        int fullBright = 0xF000F0;
        if (type.isTrafficDisplay()) {
            float textArea = type.getDisplaySize() * 100.0F - 20.0F;
            float lineStep = textArea / lineCount;
            float maxScale = lineStep / (font.lineHeight + 1.0F);
            boolean scrolling = blockEntity.isScrolling();
            var level = scrolling ? blockEntity.getLevel() : null;
            double time = level == null ? 0 : level.getGameTime() + partialTick;
            for (int i = 0; i < lineCount; i++) {
                String line = lines[i];
                if (line.isEmpty()) continue;
                int textWidth = scrolling ? 0 : font.width(line);
                float textScale = scrolling ? maxScale : Math.min(maxScale, textArea / Math.max(1, textWidth));
                float lineCenter = -textArea / 2.0F + lineStep * (i + 0.5F);
                float lineTop = lineCenter - font.lineHeight * textScale / 2.0F;
                if (scrolling) {
                    var visible = ScrollingText.window(line, font, textArea / textScale, time, blockEntity.getScrollSpeed());
                    if (visible.text().isEmpty()) continue;
                    textMatrix.set(matrix).scale(textScale);
                    font.drawInBatch(visible.text(),-textArea/textScale/2+visible.x(),lineTop/textScale,color,false,textMatrix,buffers,Font.DisplayMode.POLYGON_OFFSET,0,fullBright);
                } else {
                    textMatrix.set(matrix).scale(textScale);
                    font.drawInBatch(line, -textWidth / 2.0F, lineTop / textScale, color, false,
                        textMatrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, fullBright);
                }
            }
        } else {
            pose.scale(0.82F, 0.82F, 0.82F);
            matrix = pose.last().pose();
            while (pixels != 0) {
                int pixel = Long.numberOfTrailingZeros(pixels);
                font.drawInBatch("■", (pixel & 7) * 10 - 39, (pixel >>> 3) * 10 - 39,
                    blockEntity.getPixelColor(pixel), false, matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, fullBright);
                pixels &= pixels - 1;
            }
        }
        pose.popPose();
    }

}
