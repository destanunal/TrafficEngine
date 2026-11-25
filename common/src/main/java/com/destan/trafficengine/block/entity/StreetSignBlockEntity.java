package com.destan.trafficengine.block.entity;

import com.destan.trafficengine.block.StreetSignBlock;
import com.destan.trafficengine.data.SignTextConfig;
import com.destan.trafficengine.data.SignTextConfig.ConfiguredLineData;
import com.destan.trafficengine.data.SignTextConfig.WritableSignConfig;
import com.destan.trafficengine.util.ColorValue;
import com.destan.trafficengine.block.data.IColorBlockEntity;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;

public class StreetSignBlockEntity extends WritableTrafficSignBlockEntity implements IColorBlockEntity {

    private static final WritableSignConfig POST_CONFIG = new WritableSignConfig(new ConfiguredLineData[]{
        new ConfiguredLineData(0, -4F / 16, new Vec2(1, 1), new Vec2(2.5F, 2.5F), 22F / 16, 1, 0)
    }, true, StreetSignBlock.POST_TEXT_CENTER, 120, WritableSignConfig.DEFAULT_SCALE, 90, StreetSignBlock.POST_TEXT_CENTER, 0, .034F,
        state -> 90 + SignTextConfig.facingRotation(state), 0);
    private static final WritableSignConfig WALL_CONFIG = new WritableSignConfig(new ConfiguredLineData[]{
        new ConfiguredLineData(0, -4F / 16, new Vec2(1, 1), new Vec2(2.5F, 2.5F), 18F / 16, 1, 0)
    }, false, 0, 120, WritableSignConfig.DEFAULT_SCALE, 0, 0, 0, -6.75F / 16, SignTextConfig::facingRotation, 0);

    private PaintColor color = PaintColor.NONE;
    private WritableSignConfig renderConfig;
    private boolean renderedOnWall;

    protected StreetSignBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public StreetSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STREET_SIGN_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public WritableSignConfig getRenderConfig() {
        boolean onWall = getBlockState().getValue(StreetSignBlock.WALL_MOUNTED);
        if (renderConfig == null || renderedOnWall != onWall) {
            renderedOnWall = onWall;
            renderConfig = (onWall ? WALL_CONFIG : POST_CONFIG).withColor(textColor(color));
        }
        return renderConfig;
    }

    public static int textColor(PaintColor color) {
        ColorValue face = color == PaintColor.NONE ? StreetSignBlock.DEFAULT_PANEL_COLOR : color.getTextureColor();
        return ColorValue.pickBasedOnBrightness(face, ColorValue.WHITE, ColorValue.BLACK, .5F).getAsARGB();
    }

    @Override
    public void loadAdditional(CompoundTag compound, HolderLookup.Provider provider)
    {
        PaintColor previousColor = this.color;
        super.loadAdditional(compound, provider);
        this.color = compound.contains(NBT_COLOR, Tag.TAG_ANY_NUMERIC) ? PaintColor.getByIndex(compound.getInt(NBT_COLOR)) : PaintColor.NONE;
        if (previousColor != this.color) renderConfig = null;
        // The block-state packet can rebuild Embeddium's mesh before the paint
        // data arrives. Invalidate it after reading the new colour, once per change.
        if (previousColor != this.color && level != null && level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt(NBT_COLOR, color.getIndex());
    }

    @Override
    public void setColor(PaintColor color) {
        setSignData(null, color);
    }

    public void setSignData(String[] messages, PaintColor paint) {
        boolean textChanged = messages != null && updateTexts(messages);
        boolean paintChanged = paint != null && color != paint;
        if (paintChanged) {
            color = paint;
            renderConfig = null;
            if (level != null && level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
        if (textChanged || paintChanged) notifyUpdate();
    }

    @Override
    public PaintColor getColor() {
        return this.color;
    }
}
