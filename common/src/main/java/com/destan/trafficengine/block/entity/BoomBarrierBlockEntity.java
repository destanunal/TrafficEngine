package com.destan.trafficengine.block.entity;

import com.destan.trafficengine.block.BoomBarrierBlock;
import com.destan.trafficengine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Animation is derived from block updates and world time; no ticker or animation packets. */
public class BoomBarrierBlockEntity extends BlockEntity {
    private float from, target;
    private long changedAt;
    private AABB renderBounds;
    public BoomBarrierBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOOM_BARRIER_BLOCK_ENTITY.get(), pos, state);
        renderBounds = BoomBarrierBlock.renderBounds(pos,state);
        from = target = state.getValue(BoomBarrierBlock.OPEN) ? 1 : 0;
    }
    public float progress(float partialTick) {
        if (level == null || from == target) return target;
        float elapsed = Mth.clamp((level.getGameTime() - changedAt + partialTick) / 12F, 0, 1);
        // Settle once: idle barriers take the constant-time early return above on later frames.
        if (elapsed >= 1) { from = target; return target; }
        return Mth.lerp(elapsed * elapsed * (3 - 2 * elapsed), from, target);
    }
    /** Forge/NeoForge culling hook; Fabric uses the renderer off-screen flag. */
    public AABB getRenderBoundingBox() { return renderBounds; }
    @Override public void setBlockState(BlockState state) {
        if (renderBounds != null && state.hasProperty(BoomBarrierBlock.FACING)
                && (getBlockState().getValue(BoomBarrierBlock.FACING)!=state.getValue(BoomBarrierBlock.FACING)
                    || BoomBarrierBlock.length(getBlockState())!=BoomBarrierBlock.length(state)))
            renderBounds=BoomBarrierBlock.renderBounds(worldPosition,state);
        if (state.hasProperty(BoomBarrierBlock.OPEN)) {
            float next = state.getValue(BoomBarrierBlock.OPEN) ? 1 : 0;
            if (level==null) from=target=next;
            else if (next != target) { from = progress(0); target = next; changedAt = level.getGameTime(); }
        }
        super.setBlockState(state);
    }
}
