package com.destan.trafficengine.block.entity;

import com.destan.trafficengine.block.DoubleTrafficLightBlock;
import com.destan.trafficengine.block.TrafficLightBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;


/** The rear signal is owned data, never a second registered/ticking world block entity. */
public final class DoubleTrafficLightBlockEntity extends TrafficLightBlockEntity {
    private final RearSignal rear;
    public DoubleTrafficLightBlockEntity(BlockPos pos,BlockState state){this(com.destan.trafficengine.registry.ModBlockEntities.TRAFFIC_LIGHT_BLOCK_ENTITY.get(),pos,state);}
    protected DoubleTrafficLightBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);rear=new RearSignal(pos,state,this);}
    public TrafficLightBlockEntity face(int index){return index==1?rear:this;}
    @Override public void setLevel(Level level){super.setLevel(level);rear.setLevel(level);}
    @Override public void setRemoved(){super.setRemoved();rear.setRemoved();}
    @Override public void clearRemoved(){super.clearRemoved();rear.clearRemoved();}
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        rear.load(tag.getCompound("BackSignal"));
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("BackSignal",rear.saveWithoutMetadata());
    }
    public static void tickBoth(Level level,BlockPos pos,BlockState state,TrafficLightBlockEntity entity){
        TrafficLightBlockEntity.tick(level,pos,state,entity);
        if(entity instanceof DoubleTrafficLightBlockEntity light)TrafficLightBlockEntity.tick(level,pos,light.rear.getBlockState(),light.rear);
    }
    private static final class RearSignal extends TrafficLightBlockEntity {
        private final DoubleTrafficLightBlockEntity owner;
        private BlockState lastOwner,lastState;
        RearSignal(BlockPos pos,BlockState state,DoubleTrafficLightBlockEntity owner){super(owner.getType(),pos,state);this.owner=owner;}
        @Override public int getSignalFace(){return 1;}
        @Override public BlockState getBlockState(){
            if(owner==null)return super.getBlockState();
            BlockState current=owner.getBlockState();
            if(current!=lastOwner){lastOwner=current;lastState=current.setValue(TrafficLightBlock.MODEL,current.getValue(DoubleTrafficLightBlock.BACK_MODEL)).setValue(TrafficLightBlock.FACING,current.getValue(TrafficLightBlock.FACING).getOpposite());}
            return lastState;
        }
        @Override public void notifyUpdate(){if(owner!=null)owner.notifyUpdate();}
        @Override public void setChanged(){if(owner!=null)owner.setChanged();}
    }
}
