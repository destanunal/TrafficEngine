package com.destan.trafficengine.client.ber;

import org.joml.Vector3f;

import com.mojang.math.Axis;

import com.destan.trafficengine.client.ber.RenderContext;
import com.destan.trafficengine.client.ber.RotatableBlockEntityRenderer;
import com.destan.trafficengine.client.ber.SignRenderUtils;
import com.destan.trafficengine.util.ColorValue;
import com.destan.trafficengine.block.DoubleSidedTrafficSignBlock;
import com.destan.trafficengine.block.TrafficSignBlock;
import com.destan.trafficengine.block.data.TrafficSignShape;
import com.destan.trafficengine.block.entity.TrafficSignBlockEntity;
import com.destan.trafficengine.data.TrafficSignClientTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class TrafficSignBlockEntityRenderer extends RotatableBlockEntityRenderer<TrafficSignBlockEntity> {

    public TrafficSignBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void renderBlock(RenderContext<TrafficSignBlockEntity> graphics, float pPartialTick) {
        BlockState state = graphics.blockEntity() == null ? null : graphics.blockEntity().getBlockState();
        boolean diagonal = state != null
            && state.hasProperty(TrafficSignBlock.DIAGONAL)
            && state.getValue(TrafficSignBlock.DIAGONAL);
        boolean postMounted = state != null
            && state.hasProperty(TrafficSignBlock.POST_MOUNTED)
            && state.getValue(TrafficSignBlock.POST_MOUNTED);

        boolean direct = state != null && state.getValue(TrafficSignBlock.DIRECT_MOUNTED) > 0;
        if (direct) { graphics.poseStack().pushPose(); graphics.poseStack().translate(0, 0, -TrafficSignBlock.directDepth(state.getValue(TrafficSignBlock.DIRECT_MOUNTED))); }
        else if (diagonal) {
            graphics.poseStack().pushPose();
            graphics.poseStack().translate(8.0f, 8.0f, 8.0f);
            graphics.poseStack().mulPose(Axis.YP.rotationDegrees(-45.0f));
            graphics.poseStack().translate(-8.0f, -8.0f, -8.0f);
            if (TrafficSignBlock.hasSpacedDiagonalPlate(state)) {
                graphics.poseStack().translate(0, 0, TrafficSignBlock.DIAGONAL_PLATE_OFFSET);
            }
        } else if (postMounted) {
            graphics.poseStack().pushPose();
            // RotatableBlockEntityRenderer has already rotated the pose to the
            // block's FACING. Work in that local NORTH-facing coordinate system.
            graphics.poseStack().translate(0, 0, -12.0f);
            graphics.poseStack().translate(8.0f, 8.0f, 8.0f);
            graphics.poseStack().mulPose(Axis.XP.rotationDegrees(-22.5f));
            graphics.poseStack().translate(-8.0f, -8.0f, -8.0f);
        }

        try {
            renderOriented(graphics);
        } finally {
            if (direct || diagonal || postMounted) {
                graphics.poseStack().popPose();
            }
        }
    }

    private static final Vector3f ORIGIN = new Vector3f(0);
    private static final float FREESTANDING_FRONT = 9.5f / 16;
    private static final float WALL_FRONT = 0.5f / 16;
    private static final float PLATE_DEPTH = 0.5f / 16;
    private static final float FACE_OFFSET = 0.002f;

    private void renderOriented(RenderContext<TrafficSignBlockEntity> graphics) {
        var entity = graphics.blockEntity();
        if (entity == null || entity.isRemoved()) return;

        BlockState state = entity.getBlockState();
        Direction facing = state.getValue(TrafficSignBlock.FACING);
        boolean wall = state.getValue(TrafficSignBlock.WALL_MOUNTED);
        var pose = graphics.poseStack();
        pose.pushPose();
        pose.scale(16, 16, 16);
        renderFace(graphics, entity.getClientTexture(), state.getValue(TrafficSignBlock.SHAPE), facing, wall);
        if (state.getBlock() instanceof DoubleSidedTrafficSignBlock) {
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.translate(-0.5, 0, -0.5);
            renderFace(graphics, entity.getBackClientTexture(), state.getValue(DoubleSidedTrafficSignBlock.BACK_SHAPE),
                facing.getOpposite(), false);
        }
        pose.popPose();
    }

    private static void renderFace(RenderContext<?> graphics, TrafficSignClientTexture texture,
            TrafficSignShape shape, Direction facing, boolean wall) {
        if (texture.isDisposed() || !texture.isFullyLoaded()) return;
        float front = wall ? WALL_FRONT : FREESTANDING_FRONT;
        var pose = graphics.poseStack();

        if (shape == TrafficSignShape.MISC && texture.hasBackground()) {
            float rear = front - PLATE_DEPTH;
            SignRenderUtils.renderPlateEdges(texture, graphics, rear, front);
            if (!wall) {
                pose.pushPose();
                pose.translate(1, 0, rear - FACE_OFFSET);
                pose.mulPose(Axis.YP.rotationDegrees(180));
                SignRenderUtils.renderTexture(texture.getBackgroundTextureLocation(), graphics, ORIGIN,
                    1, 1, 0, 0, 1, 1, facing.getOpposite(), ColorValue.WHITE, graphics.packedLight(), true);
                pose.popPose();
            }
        }

        pose.pushPose();
        pose.translate(0, 0, front + FACE_OFFSET);
        SignRenderUtils.renderTexture(texture.getTextureLocation(), graphics, ORIGIN,
            1, 1, 0, 0, 1, 1, facing, ColorValue.WHITE, graphics.packedLight(), true);
        pose.popPose();
    }
}
