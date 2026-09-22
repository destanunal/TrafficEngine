package com.destan.trafficengine.block.entity;

import de.mrjulsen.mcdragonlib.block.DLSyncedBlockEntity;
import com.destan.trafficengine.block.data.IColorBlockEntity;
import com.destan.trafficengine.block.data.RoadBlock;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ColoredBlockEntity extends DLSyncedBlockEntity implements IColorBlockEntity {

    // Properties
    protected PaintColor color = PaintColor.NONE;
    // Road blocks use a second tint for their painted marking. Keeping this
    // separate lets a white/blue marking sit on an already painted road.
    private PaintColor markingColor = PaintColor.NONE;
    private static final String NBT_MARKING_COLOR = "marking_color";

    protected ColoredBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ColoredBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLORED_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void load(CompoundTag compound) {
        super.load(compound);
        this.color = PaintColor.getByIndex(compound.getInt(NBT_COLOR));
        if (compound.contains(NBT_MARKING_COLOR)) {
            this.markingColor = PaintColor.getByIndex(compound.getInt(NBT_MARKING_COLOR));
        } else if (getBlockState().getBlock() instanceof RoadBlock) {
            // Older worlds stored a road marking's colour in the single
            // "color" field. Migrate it instead of silently turning it white.
            this.markingColor = this.color;
            this.color = PaintColor.NONE;
        } else {
            this.markingColor = PaintColor.NONE;
        }
    }    

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(NBT_COLOR, color.getIndex());
        tag.putInt(NBT_MARKING_COLOR, markingColor.getIndex());
    }

    /* GETTERS AND SETTERS */
    @Override
    public void setColor(PaintColor color) {
        this.color = color;
        notifyUpdate();
        getLevel().sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 512);
    }

    @Override
    public PaintColor getColor() {
        return this.color;
    }

    public PaintColor getMarkingColor() {
        return markingColor;
    }

    public void setRoadColors(PaintColor baseColor, PaintColor newMarkingColor) {
        this.color = baseColor;
        this.markingColor = newMarkingColor;
        notifyUpdate();
        getLevel().sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 512);
    }
}
