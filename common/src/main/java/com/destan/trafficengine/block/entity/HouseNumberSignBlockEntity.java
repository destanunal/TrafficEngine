package com.destan.trafficengine.block.entity;

import com.destan.trafficengine.block.HouseNumberSignBlock;
import com.destan.trafficengine.data.SignTextConfig;
import com.destan.trafficengine.data.SignTextConfig.ConfiguredLineData;
import com.destan.trafficengine.data.SignTextConfig.WritableSignConfig;
import com.destan.trafficengine.util.ColorValue;
import com.destan.trafficengine.block.data.IColorBlockEntity;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;

public class HouseNumberSignBlockEntity extends WritableTrafficSignBlockEntity implements IColorBlockEntity {

    private static final WritableSignConfig CONFIG = new WritableSignConfig(new ConfiguredLineData[]{
        new ConfiguredLineData(0, .5F / 16, new Vec2(1, 1), new Vec2(3, 3), 8F / 16, 3, 0)
    }, false, 0, 120, WritableSignConfig.DEFAULT_SCALE, 0, 0, 0, -.45F, SignTextConfig::facingRotation, 0);
    private PaintColor color = PaintColor.NONE;
    private WritableSignConfig renderConfig;

    protected HouseNumberSignBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public HouseNumberSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HOUSE_NUMBER_SIGN_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public WritableSignConfig getRenderConfig() {
        if (renderConfig == null) {
            ColorValue face = color == PaintColor.NONE ? HouseNumberSignBlock.DEFAULT_PANEL_COLOR : color.getTextureColor();
            int textColor = ColorValue.pickBasedOnBrightness(face, ColorValue.WHITE, ColorValue.BLACK, .5F).getAsARGB();
            renderConfig = CONFIG.withColor(textColor);
        }
        return renderConfig;
    }

    @Override
    public void load(CompoundTag compound) {
        PaintColor previousColor = this.color;
        super.load(compound);
        this.color = PaintColor.getByIndex(compound.getInt(NBT_COLOR));
        if (previousColor != this.color) renderConfig = null;
        // The block-state packet can rebuild Embeddium's mesh before the paint
        // data arrives. Invalidate it after reading the new colour, once per change.
        if (previousColor != this.color && level != null && level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        tag.putInt(NBT_COLOR, color.getIndex());
        super.saveAdditional(tag);
    }

    @Override
    public void setColor(PaintColor color) {
        if (this.color == color) return;
        this.color = color;
        renderConfig = null;
        notifyUpdate();
        if (level != null && level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public PaintColor getColor() {
        return this.color;
    }
}
