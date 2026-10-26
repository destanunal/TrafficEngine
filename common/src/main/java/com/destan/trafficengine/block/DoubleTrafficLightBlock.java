package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.TrafficLightModel;
import com.destan.trafficengine.block.data.TrafficLightTrigger;
import com.destan.trafficengine.block.entity.TrafficLightBlockEntity;
import com.destan.trafficengine.block.entity.DoubleTrafficLightBlockEntity;
import com.destan.trafficengine.client.ClientWrapper;
import com.destan.trafficengine.registry.ModBlockEntities;
import com.destan.trafficengine.registry.ModItemTags;
import com.destan.trafficengine.util.SignalFaces;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class DoubleTrafficLightBlock extends TrafficLightBlock {
    public static final EnumProperty<TrafficLightModel> BACK_MODEL=EnumProperty.create("back_model",TrafficLightModel.class);
    private static final VoxelShape[][][][] SHAPES=new VoxelShape[4][4][4][2];
    public DoubleTrafficLightBlock(){super();registerDefaultState(defaultBlockState().setValue(BACK_MODEL,TrafficLightModel.THREE_LIGHTS));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(BACK_MODEL);}
    @Override public VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){
        var front=state.getValue(MODEL);var back=state.getValue(BACK_MODEL);var dir=state.getValue(FACING);int diagonal=state.getValue(DIAGONAL)?1:0;
        VoxelShape shape=SHAPES[front.ordinal()][back.ordinal()][dir.get2DDataValue()][diagonal];
        if(shape==null){shape=Shapes.or(getCachedShape(front,dir,diagonal==1),getCachedShape(back,dir.getOpposite(),diagonal==1)).optimize();SHAPES[front.ordinal()][back.ordinal()][dir.get2DDataValue()][diagonal]=shape;}
        return shape;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new DoubleTrafficLightBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return createTickerHelper(type,ModBlockEntities.TRAFFIC_LIGHT_BLOCK_ENTITY.get(),DoubleTrafficLightBlockEntity::tickBoth);
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player.getItemInHand(hand).is(ModItemTags.WRENCHES)){
            if(level.isClientSide&&!player.isShiftKeyDown())ClientWrapper.showTrafficLightConfigScreen(level,pos,SignalFaces.hit(state,pos,hit.getLocation()));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state,level,pos,player,hand,hit);
    }
    @Override public void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){
        if(level.isClientSide)return;
        if(level.getBlockEntity(pos) instanceof DoubleTrafficLightBlockEntity light){boolean power=level.hasNeighborSignal(pos);
            for(int i=0;i<2;i++){var face=light.face(i);if(face.getSchedule().getTrigger()!=TrafficLightTrigger.REDSTONE)continue;
                if(power&&!face.isPowered()){face.setPowered(true);face.startSchedule(true);}
                else if(!power&&face.isPowered()){face.setPowered(false);face.stopSchedule();}
            }
        }
    }
    @Override public boolean canAttach(BlockState state,BlockPos pos,net.minecraft.core.Direction direction){return direction.getAxis().isVertical();}
}
