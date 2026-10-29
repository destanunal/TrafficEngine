package com.destan.trafficengine.block.entity;

import com.destan.trafficengine.block.PedestrianRailingBlock;
import com.destan.trafficengine.block.data.IColorBlockEntity;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** One persisted dye value, no ticker, renderer, mesh cache or neighbour scans. */
public final class RailingPaintBlockEntity extends SyncedBlockEntity implements IColorBlockEntity {
    private PaintColor color = PaintColor.NONE;
    public RailingPaintBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RAILING_PAINT_BLOCK_ENTITY.get(), pos, state);
    }
    @Override public void setLevel(net.minecraft.world.level.Level world) {
        super.setLevel(world);
        schedulePaintVariant();
    }
    private void schedulePaintVariant() {
        // Defer old-data conversion until after chunk loading; no world mutation in load().
        if (level != null && !level.isClientSide && color != PaintColor.NONE
                && getBlockState().getValue(PedestrianRailingBlock.GRAY))
            level.scheduleTick(worldPosition,getBlockState().getBlock(),1);
    }
    /** A state packet may arrive before its dye packet; keep that short gap neutral. */
    public static int tint(boolean gray, PaintColor color) {
        return gray ? 0xFFFFFFFF : color == PaintColor.NONE ? 0xFF797979 : color.getTextureARGB();
    }
    @Override public PaintColor getColor() { return color; }
    @Override public void setColor(PaintColor value) {
        if (level == null || level.isClientSide || value == null) return;
        BlockState state = getBlockState();
        boolean gray = value == PaintColor.NONE;
        if (color == value && state.getValue(PedestrianRailingBlock.GRAY) == gray) return;
        color = value;
        // Painted variants use a white tint base; unpainted variants retain standard metal.
        if (state.getValue(PedestrianRailingBlock.GRAY) != gray)
            level.setBlockAndUpdate(worldPosition, state.setValue(PedestrianRailingBlock.GRAY, gray));
        notifyUpdate();
    }
    @Override public void load(CompoundTag tag) {
        PaintColor before = color;
        super.load(tag);
        int index = tag.contains(NBT_COLOR) ? tag.getInt(NBT_COLOR) : -1;
        color = index >= -1 && index <= 15 ? PaintColor.getByIndex(index) : PaintColor.NONE;
        schedulePaintVariant();
        if (level != null && level.isClientSide) {
            BlockState state = getBlockState();
            // The packet carries an explicit NONE value on erase. Apply its
            // texture variant together with the tint, independent of packet order.
            if (tag.contains(NBT_COLOR) && state.getValue(PedestrianRailingBlock.GRAY) != (color == PaintColor.NONE))
                level.setBlock(worldPosition, state.setValue(PedestrianRailingBlock.GRAY, color == PaintColor.NONE), 2 | 16);
            else if (before != color) level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(NBT_COLOR, color.getIndex());
    }
}
