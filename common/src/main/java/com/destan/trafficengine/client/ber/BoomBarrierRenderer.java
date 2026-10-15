package com.destan.trafficengine.client.ber;

import com.destan.trafficengine.block.BoomBarrierBlock;
import com.destan.trafficengine.block.BoomBarrierPartBlock;
import com.destan.trafficengine.block.entity.BoomBarrierBlockEntity;
import com.destan.trafficengine.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** All arm lengths repeat one baked module on the existing atlas; there are no per-section renderers. */
public class BoomBarrierRenderer implements BlockEntityRenderer<BoomBarrierBlockEntity> {
    // Endpoint rotations are shared read-only; only an arm currently moving needs a new rotation.
    private static final Quaternionf[] FACING_ROTATIONS = new Quaternionf[4];
    private static final Quaternionf OPEN_ROTATION = Axis.XP.rotationDegrees(90);
    static {
        for (Direction facing : Direction.Plane.HORIZONTAL)
            FACING_ROTATIONS[facing.get2DDataValue()] = Axis.YP.rotationDegrees(180 - facing.toYRot());
    }
    private final BlockRenderDispatcher blocks;
    public BoomBarrierRenderer(BlockEntityRendererProvider.Context context) { blocks = context.getBlockRenderDispatcher(); }
    @Override public void render(BoomBarrierBlockEntity barrier, float tick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = ModBlocks.BOOM_BARRIER_PART.get().defaultBlockState();
        // Invisible proxy states select two atlas-baked meshes without extra blocks or entities.
        var hingeState = state.setValue(BoomBarrierPartBlock.VERTICAL, true);
        var hingeModel = blocks.getBlockModel(hingeState);
        var vertices = buffers.getBuffer(RenderType.solid());
        var renderer = blocks.getModelRenderer();
        var motorState = barrier.getBlockState();
        int length = BoomBarrierBlock.length(motorState);
        float progress = barrier.progress(tick);
        pose.pushPose();
        pose.translate(0.5, BoomBarrierBlock.PIVOT_Y, 0.5);
        pose.mulPose(FACING_ROTATIONS[motorState.getValue(BoomBarrierBlock.FACING).get2DDataValue()]);
        pose.translate(BoomBarrierBlock.PIVOT_SIDE-0.5,0,0);
        if (progress >= 1) pose.mulPose(OPEN_ROTATION);
        else if (progress > 0) pose.mulPose(Axis.XP.rotationDegrees(90 * progress));
        renderer.renderModel(pose.last(), vertices, hingeState, hingeModel, 1, 1, 1, light, overlay);
        if (length > 0) {
            var model = blocks.getBlockModel(state);
            pose.translate(0, 0, -BoomBarrierBlock.ARM_OFFSET);
            for (int i = 0; i < length; i++) {
                renderer.renderModel(pose.last(), vertices, state, model, 1, 1, 1, light, overlay);
                pose.translate(0, 0, -1);
            }
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
