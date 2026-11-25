package com.destan.trafficengine.block.entity;

import com.destan.trafficengine.data.SignTextConfig;
import com.destan.trafficengine.data.WritableSignText;
import com.destan.trafficengine.data.SignTextConfig.ConfiguredLineData;
import com.destan.trafficengine.data.SignTextConfig.WritableSignConfig;
import com.destan.trafficengine.block.TownSignBlock;
import com.destan.trafficengine.block.TownSignBlock.ETownSignSide;
import com.destan.trafficengine.block.data.TownSignVariant;
import com.destan.trafficengine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;

public class TownSignBlockEntity extends WritableTrafficSignBlockEntity {
    
    private final String[] linesBack = new String[WritableSignText.BACK_LINES];

    protected TownSignBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        java.util.Arrays.fill(linesBack, "");
    }

    public TownSignBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.TOWN_SIGN_BLOCK_ENTITY.get(), pos, state);
    }

    private static final WritableSignConfig FRONT_CONFIG = new WritableSignConfig(new ConfiguredLineData[] {
        new ConfiguredLineData(0, -6F / 16, new Vec2(1, 1), new Vec2(1, 1), 13F / 16, 1, 0),
        new ConfiguredLineData(0, -6F / 16, new Vec2(1, 1), new Vec2(2, 2), 13F / 16, 3, 0),
        new ConfiguredLineData(0, -6F / 16, new Vec2(1, 1), new Vec2(1, 1), 13F / 16, 1, 0),
        new ConfiguredLineData(0, -6F / 16, new Vec2(1, 1), new Vec2(1, 1), 13F / 16, 1, 0)
    }, false, 0, 120, WritableSignConfig.DEFAULT_SCALE, 0, 0, 0, .1F, SignTextConfig::facingRotation, 0);
    private static final WritableSignConfig BACK_CONFIG = new WritableSignConfig(new ConfiguredLineData[] {
        new ConfiguredLineData(-1.5F / 16, -4F / 16, new Vec2(1, 1), new Vec2(2, 2), 10F / 16, 3, 0),
        new ConfiguredLineData(0, -3.5F / 16, new Vec2(1, 1), new Vec2(2, 2), 13F / 16, 3, 0)
    }, false, 0, 120, WritableSignConfig.DEFAULT_SCALE, 0, 0, 0, .1F,
        state -> SignTextConfig.facingRotation(state) + (state.getValue(TownSignBlock.VARIANT) == TownSignVariant.BOTH ? 180 : 0), 0);

    @Override
    public WritableSignConfig getRenderConfig() { return FRONT_CONFIG; }

    public WritableSignConfig getBackRenderConfig() { return BACK_CONFIG; }

    public WritableSignConfig getTownSignRenderConfig(TownSignBlock.ETownSignSide side) {
        switch (side) {
            case BACK:
                return this.getBackRenderConfig();
            default:
            case FRONT:
                return this.getRenderConfig();
        }
    }


    public void setBackText(String text, int line) {
        if (line < 0 || line >= linesBack.length) return;
        String value = text == null ? "" : text;
        if (!value.equals(linesBack[line])) {
            linesBack[line] = value;
            notifyUpdate();
        }
    }

    public void setBackTexts(String[] messages) {
        setFaceTexts(null, messages);
    }

    public void setFaceTexts(String[] front, String[] back) {
        boolean changed = front != null && updateTexts(front);
        if (back != null) for (int i = 0; i < linesBack.length; i++) {
            String text = i < back.length && back[i] != null ? back[i] : "";
            if (!text.equals(linesBack[i])) {
                linesBack[i] = text;
                changed = true;
            }
        }
        if (changed) notifyUpdate();
    }

    public String getBackText(int line) {
        return line >= 0 && line < linesBack.length ? linesBack[line] : "";
    }

    @Override
    public void loadAdditional(CompoundTag compound, HolderLookup.Provider provider) {
        super.loadAdditional(compound, provider);
        for (int i = 0; i < linesBack.length; i++) {
            this.linesBack[i] = compound.getString("lineBack" + i);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        for (int i = 0; i < linesBack.length; i++) tag.putString("lineBack" + i, linesBack[i]);
    }

}
