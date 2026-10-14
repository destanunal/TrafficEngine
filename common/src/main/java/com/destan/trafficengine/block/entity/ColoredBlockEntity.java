package com.destan.trafficengine.block.entity;

import com.destan.trafficengine.block.entity.SyncedBlockEntity;
import com.destan.trafficengine.block.data.IColorBlockEntity;
import com.destan.trafficengine.block.data.RoadBlock;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ColoredBlockEntity extends SyncedBlockEntity implements IColorBlockEntity {

    // Properties
    protected PaintColor color = PaintColor.NONE;
    // Road blocks use a second tint for their painted marking. Keeping this
    // separate lets a white/blue marking sit on an already painted road.
    private PaintColor markingColor = PaintColor.NONE;
    private static final String NBT_MARKING_COLOR = "marking_color";
    private float[] strokeVertices=new float[0];
    private int roadDashGap=2;
    private int automaticLinePosition=-1;
    public int getAutomaticLinePosition() {return automaticLinePosition;}
    public int getRoadDashGap() {return roadDashGap;}
    public float[] getStrokeVertices() {return strokeVertices;}
    public void setStrokeVertices(float[] vertices) {
        setStrokeVertices(vertices,2);
    }
    public void setStrokeVertices(float[] vertices,int dashGap) {
        setStrokeVertices(vertices,dashGap,-1);
    }
    public void setStrokeVertices(float[] vertices,int dashGap,int automaticPosition) {
        roadDashGap=dashGap==4?4:2;
        automaticLinePosition=Math.max(-1,Math.min(2,automaticPosition));
        strokeVertices=vertices.clone();notifyUpdate();
        if (level != null) {
            refreshRoadMeshes();
            level.sendBlockUpdated(getBlockPos(),getBlockState(),getBlockState(),3);
        }
    }
    private void refreshRoadMeshes() {
        if (level == null || !level.isClientSide) return;
        com.destan.trafficengine.client.model.RoadStrokeModels.refresh(this);
        if (!com.destan.trafficengine.item.RoadMarkingMesh.connectedRoad(getBlockState())) return;
        // Changes to a lane's position can change its neighbours even if their block
        // states stay identical. Include one-level slope/asphalt transitions too.
        for (var side : net.minecraft.core.Direction.Plane.HORIZONTAL) for (int dy=-1;dy<=1;dy++) {
            BlockPos pos=getBlockPos().relative(side).offset(0,dy,0);
            if (level.getBlockEntity(pos) instanceof ColoredBlockEntity neighbour
                    && com.destan.trafficengine.item.RoadMarkingMesh.connectedRoad(neighbour.getBlockState())) {
                com.destan.trafficengine.client.model.RoadStrokeModels.refresh(neighbour);
                level.sendBlockUpdated(pos,neighbour.getBlockState(),neighbour.getBlockState(),3);
            }
        }
    }
    @Override public void setRemoved() {
        super.setRemoved();
        refreshRoadMeshes();
    }

    protected ColoredBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ColoredBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLORED_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void loadAdditional(CompoundTag compound, HolderLookup.Provider provider) {
        PaintColor previousColor = this.color;
        PaintColor previousMarkingColor = this.markingColor;
        float[] previousStroke=strokeVertices;
        int previousAutomaticPosition=automaticLinePosition;
        super.loadAdditional(compound, provider);
        int savedPosition=compound.contains("road_auto_position")?compound.getInt("road_auto_position"):-2;
        automaticLinePosition=savedPosition>=-2 && savedPosition<=2?savedPosition:-1;
        int[] packed=compound.getIntArray("road_stroke");
        roadDashGap=compound.getInt("road_dash_gap")==4?4:2;
        strokeVertices=new float[packed.length<=384 && packed.length%12==0?packed.length:0];
        for(int i=0;i<strokeVertices.length;i++) {
            float value=Float.intBitsToFloat(packed[i]);
            if(!Float.isFinite(value) || value<0 || value>1.01F) {strokeVertices=new float[0];break;}
            strokeVertices[i]=value;
        }
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
        // Colour arrives in block-entity data, after the block-state update.
        // Rebuild the client mesh now instead of waiting for a second click.
        if (level != null && level.isClientSide
                && (previousColor != this.color || previousMarkingColor != this.markingColor
                    || previousAutomaticPosition!=automaticLinePosition
                    || !java.util.Arrays.equals(previousStroke,strokeVertices))) {
            refreshRoadMeshes();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }    

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt(NBT_COLOR, color.getIndex());
        tag.putInt(NBT_MARKING_COLOR, markingColor.getIndex());
        tag.putInt("road_dash_gap",roadDashGap);
        tag.putInt("road_auto_position",automaticLinePosition);
        if(strokeVertices.length>0) {
            int[] packed=new int[strokeVertices.length];
            for(int i=0;i<packed.length;i++)packed[i]=Float.floatToRawIntBits(strokeVertices[i]);
            tag.putIntArray("road_stroke",packed);
        }
    }

    /* GETTERS AND SETTERS */
    @Override
    public void setColor(PaintColor color) {
        this.color = color;
        notifyUpdate();
        getLevel().sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
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
        getLevel().sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }
}
