package com.destan.trafficengine.client.ber;

import com.destan.trafficengine.block.LedDeviceBlock;
import com.destan.trafficengine.block.data.LedDeviceType;
import com.destan.trafficengine.block.entity.LedDeviceBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

public class LedDeviceBlockEntityRenderer implements BlockEntityRenderer<LedDeviceBlockEntity> {
    private final Font font;
    private final Matrix4f textMatrix = new Matrix4f();
    public LedDeviceBlockEntityRenderer(BlockEntityRendererProvider.Context context) { this.font = context.getFont(); }

    @Override
    public void render(LedDeviceBlockEntity blockEntity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = blockEntity.getBlockState().getValue(LedDeviceBlock.FACING);
        LedDeviceType type = ((LedDeviceBlock)blockEntity.getBlockState().getBlock()).getDeviceType();
        boolean hanging = blockEntity.getBlockState().getValue(LedDeviceBlock.POST_ABOVE);
        boolean postMounted = blockEntity.getBlockState().getValue(LedDeviceBlock.POST_MOUNTED);
        boolean sideMounted = blockEntity.getBlockState().getValue(LedDeviceBlock.POST_SIDE);
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
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        boolean wallMounted = blockEntity.getBlockState().getValue(LedDeviceBlock.WALL_MOUNTED);
        // The black display face is baked into the block model. Only the LED
        // text is dynamic and sits one thousandth of a block in front of it,
        // which removes both the side gap and distant z-fighting.
        pose.translate(0, 0, type.isTrafficDisplay()
            ? (wallMounted ? -0.40525 : 0.15725)
            : (wallMounted ? -0.34275 : 0.15725));
        if (!blockEntity.isLit()) {
            pose.popPose();
            return;
        }
        pose.scale(0.01F, -0.01F, 0.01F);
        Matrix4f matrix = pose.last().pose();
        int color = 0xFF000000 | (blockEntity.getLedColor() & 0xFFFFFF);
        int fullBright = 0xF000F0;
        if (type.isTrafficDisplay()) {
            float textArea = type.getDisplaySize() * 100.0F - 20.0F;
            String[] lines = blockEntity.getMessageLines();
            int lineCount = Math.min(type.getMessageSlots(), lines.length);
            float lineStep = textArea / Math.max(1, lineCount);
            for (int i = 0; i < lineCount; i++) {
                float textScale = Math.min(lineStep / (font.lineHeight + 1.0F), textArea / Math.max(1, font.width(lines[i])));
                float lineCenter = -textArea / 2.0F + lineStep * (i + 0.5F);
                float lineTop = lineCenter - font.lineHeight * textScale / 2.0F;
                drawCentered(matrix, buffers, lines[i], 0, lineTop, color, fullBright, textScale);
            }
        } else {
            long pixels = blockEntity.getPixels();
            pose.scale(0.82F, 0.82F, 0.82F);
            matrix = pose.last().pose();
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                if ((pixels & (1L << (y * 8 + x))) != 0)
                    font.drawInBatch("■", x * 10 - 39, y * 10 - 39, blockEntity.getPixelColor(y * 8 + x), false, matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, fullBright);
            }
        }
        pose.popPose();
    }

    private void drawCentered(Matrix4f matrix, MultiBufferSource buffers, String text, float x, float y, int color, int light, float scale) {
        textMatrix.set(matrix).scale(scale);
        font.drawInBatch(text, x / scale - font.width(text) / 2.0F, y / scale, color, false, textMatrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
    }
}
