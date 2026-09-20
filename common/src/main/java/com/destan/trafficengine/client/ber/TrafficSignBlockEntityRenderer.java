package com.destan.trafficengine.client.ber;

import org.joml.Vector3f;

import com.mojang.math.Axis;

import de.mrjulsen.mcdragonlib.client.ber.BERGraphics;
import de.mrjulsen.mcdragonlib.client.ber.RotatableBlockEntityRenderer;
import de.mrjulsen.mcdragonlib.client.util.RenderUtils;
import de.mrjulsen.mcdragonlib.util.DLColor;
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
    public void renderBlock(BERGraphics<TrafficSignBlockEntity> graphics, float pPartialTick) {

        if (graphics.blockEntity() == null || graphics.blockEntity().isRemoved()) {
            return;
        }

        BlockState blockstate = graphics.blockEntity().getBlockState();
        if (blockstate == null) {
            return;
        }

        if (blockstate.getBlock() instanceof DoubleSidedTrafficSignBlock) {
            renderDoubleSided(graphics, blockstate);
            return;
        }

        TrafficSignClientTexture tex = graphics.blockEntity().getClientTexture();

        if (tex.isDisposed()) {
            return;
        }

        double p = 1 / 16f;
        boolean wall =
                blockstate.hasProperty(TrafficSignBlock.WALL_MOUNTED)
                        && blockstate.getValue(TrafficSignBlock.WALL_MOUNTED);
        double z;
        if (wall) {
            // Wall sign: the plate is flush against the back edge, so the pattern plane is only one plate
            // thickness (0.5px) away from that edge. The translate below adds 0.5 back, hence the - 0.5d.
            z = 0.5d * p - 0.5d;
        } else {
            z = blockstate.getValue(TrafficSignBlock.SHAPE) == TrafficSignShape.MISC ? 1.0d * p : 1.5d * p;
        }
        graphics.poseStack().pushPose();
        graphics.poseStack().scale(16, 16, 16);
        graphics.poseStack().translate(0.5f, 0.5f, 0.5f);
        graphics.poseStack().translate(-0.5d, -0.5d, z + 0.002d);

        RenderUtils.renderTexture(tex.getTextureLocation(), graphics, new Vector3f(0), 1, 1, 0, 0, 1, 1, blockstate.getValue(TrafficSignBlock.FACING), DLColor.WHITE, graphics.packedLight(), true);

        graphics.poseStack().popPose();

        if (!wall && tex.hasBackground()) {
            z = 9.0d * p - 0.5d;
            graphics.poseStack().pushPose();
            graphics.poseStack().scale(16, 16, 16);
            graphics.poseStack().translate(0.5f, 0.5f, 0.5f);
            graphics.poseStack().mulPose(Axis.YP.rotationDegrees(180));
            graphics.poseStack().translate(-0.5d, -0.5d, -(p * 2) + z - 0.002d);

            RenderUtils.renderTexture(tex.getBackgroundTextureLocation(), graphics, new Vector3f(0), 1, 1, 0, 0, 1, 1, blockstate.getValue(TrafficSignBlock.FACING).getOpposite(), DLColor.WHITE, graphics.packedLight(), true);

            graphics.poseStack().popPose();
        }
    }

    /**
     * Double sided sign = a plate on the FACING side of the center post
     * plus a plate on the opposite side of the post.
     *
     * The front pattern uses exactly the same plane as a single sided sign
     * (z = 0.5 + 1.5px, 1px for MISC). The back pattern is the same plane rotated
     * 180 degrees around the block center, so it lines up with the back plate
     * (see TrafficSignShape#getVoxelShape) and reads correctly from behind.
     * Front and back have their own shape (SHAPE / BACK_SHAPE).
     */
    private void renderDoubleSided(BERGraphics<TrafficSignBlockEntity> graphics, BlockState blockstate) {
        double p = 1 / 16f;
        double zFront = blockstate.getValue(TrafficSignBlock.SHAPE) == TrafficSignShape.MISC ? 1.0d * p : 1.5d * p;
        double zBack = blockstate.getValue(DoubleSidedTrafficSignBlock.BACK_SHAPE) == TrafficSignShape.MISC ? 1.0d * p : 1.5d * p;
        Direction facing = blockstate.getValue(TrafficSignBlock.FACING);

        // FRONT
        TrafficSignClientTexture front = graphics.blockEntity().getClientTexture();
        if (!front.isDisposed()) {
            graphics.poseStack().pushPose();
            graphics.poseStack().scale(16, 16, 16);
            graphics.poseStack().translate(0.5f, 0.5f, 0.5f);
            graphics.poseStack().translate(-0.5d, -0.5d, zFront + 0.002d);

            RenderUtils.renderTexture(front.getTextureLocation(), graphics, new Vector3f(0), 1, 1, 0, 0, 1, 1, facing, DLColor.WHITE, graphics.packedLight(), true);

            graphics.poseStack().popPose();
        }

        // BACK
        TrafficSignClientTexture back = graphics.blockEntity().getBackClientTexture();
        if (!back.isDisposed()) {
            graphics.poseStack().pushPose();
            graphics.poseStack().scale(16, 16, 16);
            graphics.poseStack().translate(0.5f, 0.5f, 0.5f);
            graphics.poseStack().mulPose(Axis.YP.rotationDegrees(180));
            graphics.poseStack().translate(-0.5d, -0.5d, zBack + 0.002d);

            RenderUtils.renderTexture(back.getTextureLocation(), graphics, new Vector3f(0), 1, 1, 0, 0, 1, 1, facing.getOpposite(), DLColor.WHITE, graphics.packedLight(), true);

            graphics.poseStack().popPose();
        }
    }
}
