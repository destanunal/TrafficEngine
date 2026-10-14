package com.destan.trafficengine.client.ber;

import com.destan.trafficengine.block.BoomBarrierBlock;
import com.destan.trafficengine.block.entity.BoomBarrierBlockEntity;
import com.destan.trafficengine.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Four repeated baked beam modules share one model and the existing terrain atlas. */
public class BoomBarrierRenderer implements BlockEntityRenderer<BoomBarrierBlockEntity> {
    private final BlockRenderDispatcher blocks;
    public BoomBarrierRenderer(BlockEntityRendererProvider.Context context) { blocks = context.getBlockRenderDispatcher(); }
    @Override public void render(BoomBarrierBlockEntity barrier, float tick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = ModBlocks.BOOM_BARRIER_PART.get().defaultBlockState();
        var model = blocks.getBlockModel(state);
        var vertices = buffers.getBuffer(RenderType.solid());
        pose.pushPose();
        pose.translate(0.5, BoomBarrierBlock.PIVOT_Y, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - barrier.getBlockState().getValue(BoomBarrierBlock.FACING).toYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(90 * barrier.progress(tick)));
        for (int i = 0; i < BoomBarrierBlock.LENGTH; i++) {
            blocks.getModelRenderer().renderModel(pose.last(), vertices, state, model, 1, 1, 1, light, overlay);
            pose.translate(0, 0, -1);
        }
        pose.popPose();
    }
    // NeoForge moved the culling hook from the entity to the renderer.
    // The erased BlockEntity signature also works when compiled against the vanilla common API.
    public net.minecraft.world.phys.AABB getRenderBoundingBox(net.minecraft.world.level.block.entity.BlockEntity entity) {
        return entity instanceof BoomBarrierBlockEntity barrier ? barrier.getRenderBoundingBox()
            : new net.minecraft.world.phys.AABB(entity.getBlockPos());
    }
    @Override public boolean shouldRenderOffScreen(BoomBarrierBlockEntity barrier) { return true; }
    @Override public int getViewDistance() { return 64; }
}
