package com.destan.trafficengine.client.ber;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
public abstract class RotatableBlockEntityRenderer<T extends BlockEntity> extends BaseBlockEntityRenderer<T> {
    protected final Font font;
    protected RotatableBlockEntityRenderer(BlockEntityRendererProvider.Context context) {super(context);font=context.getFont();}
    @Override protected final void renderSafe(RenderContext<T> context,float tick) {
        var pose=context.poseStack();
        Direction direction=context.blockEntity().getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        pose.pushPose();
        pose.translate(0.5,0,0.5);
        pose.mulPose(Axis.YP.rotationDegrees(direction==Direction.EAST || direction==Direction.WEST ? direction.getOpposite().toYRot() : direction.toYRot()));
        pose.translate(-0.5,1,-0.5);
        pose.scale(1F/16F,-1F/16F,1F/16F);
        renderBlock(context,tick);
        pose.popPose();
    }
    protected abstract void renderBlock(RenderContext<T> context,float tick);
}
