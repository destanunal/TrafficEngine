package com.destan.trafficengine.client.ber;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
public abstract class BaseBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    protected BaseBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public final void render(T entity,float tick,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if (!entity.hasLevel() || entity.getBlockState().isAir()) return;
        renderSafe(new RenderContext<>(entity,pose,buffers,light,overlay,tick),tick);
    }
    protected abstract void renderSafe(RenderContext<T> context,float tick);
}
