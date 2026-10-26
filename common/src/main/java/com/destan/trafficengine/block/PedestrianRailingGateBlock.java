package com.destan.trafficengine.block;

import com.destan.trafficengine.registry.ModItemTags;
import net.minecraft.core.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Static hinged steel panel. No entity, ticker or animated renderer. */
public final class PedestrianRailingGateBlock extends FenceGateBlock {
    public static final BooleanProperty GRAY=PedestrianRailingBlock.GRAY;
    private static final VoxelShape[][] OUTLINES=new VoxelShape[2][4];
    static {
        VoxelShape posts=Shapes.or(box(0,0,7,2,16,9),box(14,0,7,16,16,9));
        for(int open=0;open<2;open++) {
            VoxelShape north=Shapes.or(posts,open==0?box(2,2.5,7.35,14,16,8.65):box(1.35,2.5,8,2.65,16,20)).optimize();
            for(Direction dir:Direction.Plane.HORIZONTAL)OUTLINES[open][dir.get2DDataValue()]=RailingGeometry.rotate(north,dir);
        }
    }
    public PedestrianRailingGateBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(2F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion(),WoodType.OAK);
        registerDefaultState(defaultBlockState().setValue(GRAY,true));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(GRAY);}
    @Override public VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return OUTLINES[state.getValue(OPEN)?1:0][state.getValue(FACING).get2DDataValue()];}
    @Override public VoxelShape getOcclusionShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.empty();}
    public InteractionResult use(BlockState state,Level world,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(!player.mayBuild()||!world.mayInteract(player,pos))return InteractionResult.FAIL;
        if(!world.isClientSide) {
            if(player.getItemInHand(hand).is(ModItemTags.WRENCHES))world.setBlockAndUpdate(pos,state.cycle(GRAY));
            else {
                boolean open=!state.getValue(OPEN);world.setBlockAndUpdate(pos,state.setValue(OPEN,open));
                play(world,pos,open);world.gameEvent(player,open?GameEvent.BLOCK_OPEN:GameEvent.BLOCK_CLOSE,pos);
            }
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }
    @Override public void neighborChanged(BlockState state,Level world,BlockPos pos,Block neighbor,BlockPos from,boolean moving) {
        if(world.isClientSide)return;
        boolean powered=world.hasNeighborSignal(pos);
        if(powered==state.getValue(POWERED))return;
        world.setBlockAndUpdate(pos,state.setValue(POWERED,powered).setValue(OPEN,powered));
        if(state.getValue(OPEN)!=powered){play(world,pos,powered);world.gameEvent(null,powered?GameEvent.BLOCK_OPEN:GameEvent.BLOCK_CLOSE,pos);}
    }
    private static void play(Level world,BlockPos pos,boolean open){world.playSound(null,pos,open?SoundEvents.IRON_TRAPDOOR_OPEN:SoundEvents.IRON_TRAPDOOR_CLOSE,SoundSource.BLOCKS,.35F,1.15F);}
    
}
