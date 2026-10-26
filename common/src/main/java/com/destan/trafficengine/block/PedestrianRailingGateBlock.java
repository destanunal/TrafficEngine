package com.destan.trafficengine.block;

import com.destan.trafficengine.registry.ModItemTags;
import net.minecraft.core.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Static steel gate. Only edits and Redstone events inspect its loaded column. */
public final class PedestrianRailingGateBlock extends FenceGateBlock {
    public static final BooleanProperty GRAY=PedestrianRailingBlock.GRAY;
    public static final BooleanProperty UP=BlockStateProperties.UP,DOWN=BlockStateProperties.DOWN;
    private static final int MAX_STACK_HEIGHT=1024;
    private static final ThreadLocal<Boolean> UPDATING=ThreadLocal.withInitial(()->false);
    private static final VoxelShape[][][] OUTLINES=new VoxelShape[2][2][4];
    static {
        VoxelShape posts=Shapes.or(box(0,0,7,2,16,9),box(14,0,7,16,16,9));
        for(int down=0;down<2;down++)for(int open=0;open<2;open++) {
            double bottom=down==1?0:2.5;
            VoxelShape north=Shapes.or(posts,open==0?box(2,bottom,7.35,14,16,8.65):box(1.35,bottom,8,2.65,16,20)).optimize();
            for(Direction dir:Direction.Plane.HORIZONTAL)OUTLINES[down][open][dir.get2DDataValue()]=RailingGeometry.rotate(north,dir);
        }
    }
    public PedestrianRailingGateBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(2F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion(),WoodType.OAK);
        registerDefaultState(defaultBlockState().setValue(GRAY,true).setValue(UP,false).setValue(DOWN,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(GRAY,UP,DOWN);}
    @Override public VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return OUTLINES[state.getValue(DOWN)?1:0][state.getValue(OPEN)?1:0][state.getValue(FACING).get2DDataValue()];}
    @Override public VoxelShape getOcclusionShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.empty();}
    static boolean joins(BlockState first,BlockState second){return second.getBlock()==first.getBlock()&&second.getValue(FACING)==first.getValue(FACING);}
    private static BlockState connections(BlockState state,BlockGetter world,BlockPos pos){return state.setValue(UP,joins(state,world.getBlockState(pos.above()))).setValue(DOWN,joins(state,world.getBlockState(pos.below())));}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        BlockState state=super.getStateForPlacement(context);
        if(state==null)return null;
        Level world=context.getLevel();BlockPos pos=context.getClickedPos();
        BlockState adjacent=world.getBlockState(pos.below());
        if(!joins(state,adjacent))adjacent=world.getBlockState(pos.above());
        if(joins(state,adjacent))state=state.setValue(OPEN,adjacent.getValue(OPEN)).setValue(GRAY,adjacent.getValue(GRAY)).setValue(POWERED,adjacent.getValue(POWERED));
        return connections(state,world,pos);
    }
    @Override public BlockState updateShape(BlockState state,Direction side,BlockState other,LevelAccessor world,BlockPos pos,BlockPos from){
        state=super.updateShape(state,side,other,world,pos,from);
        return side==Direction.UP?state.setValue(UP,joins(state,other)):side==Direction.DOWN?state.setValue(DOWN,joins(state,other)):state;
    }
    @Override public void onPlace(BlockState state,Level world,BlockPos pos,BlockState old,boolean moving){
        super.onPlace(state,world,pos,old,moving);
        if(old.getBlock()!=this)neighborChanged(state,world,pos,this,pos,moving);
    }
    /** Finite, loaded-only range. No chunk loads, scheduled tasks or retained world references. */
    static long stackBounds(LevelReader world,BlockPos pos,BlockState state){
        BlockPos.MutableBlockPos cursor=pos.mutable();int bottom=pos.getY(),top=bottom;
        while(bottom>world.getMinBuildHeight()&&pos.getY()-bottom<MAX_STACK_HEIGHT-1){
            cursor.setY(bottom-1);if(!world.hasChunkAt(cursor)||!joins(state,world.getBlockState(cursor)))break;bottom--;
        }
        while(top<world.getMaxBuildHeight()-1&&top-bottom<MAX_STACK_HEIGHT-1){
            cursor.setY(top+1);if(!world.hasChunkAt(cursor)||!joins(state,world.getBlockState(cursor)))break;top++;
        }
        return ((long)bottom<<32)|(top&0xffffffffL);
    }
    private static boolean powered(Level world,BlockPos pos,long bounds){
        BlockPos.MutableBlockPos cursor=pos.mutable();
        for(int y=(int)(bounds>>32);y<=(int)bounds;y++){cursor.setY(y);if(world.hasNeighborSignal(cursor))return true;}
        return false;
    }
    private static void applyColumn(Level world,BlockPos pos,long bounds,BooleanProperty property,boolean value,boolean redstone){
        UPDATING.set(true);
        try{
            BlockPos.MutableBlockPos cursor=pos.mutable();
            for(int y=(int)(bounds>>32);y<=(int)bounds;y++){
                cursor.setY(y);BlockState before=world.getBlockState(cursor),after=before.setValue(property,value);
                if(redstone)after=after.setValue(OPEN,value);
                if(after!=before)world.setBlockAndUpdate(cursor,after);
            }
        }finally{UPDATING.remove();}
    }
    public InteractionResult use(BlockState state,Level world,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(!player.mayBuild()||!world.mayInteract(player,pos))return InteractionResult.FAIL;
        if(!world.isClientSide&&!UPDATING.get()){
            long bounds=stackBounds(world,pos,state);
            // Respect protected cells when operating a tall gate from any segment.
            BlockPos.MutableBlockPos cursor=pos.mutable();
            for(int y=(int)(bounds>>32);y<=(int)bounds;y++){cursor.setY(y);if(!world.mayInteract(player,cursor))return InteractionResult.FAIL;}
            if(player.getItemInHand(hand).is(ModItemTags.WRENCHES))applyColumn(world,pos,bounds,GRAY,!state.getValue(GRAY),false);
            else {
                boolean open=!state.getValue(OPEN);applyColumn(world,pos,bounds,OPEN,open,false);
                play(world,pos,open);world.gameEvent(player,open?GameEvent.BLOCK_OPEN:GameEvent.BLOCK_CLOSE,pos);
            }
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }
    @Override public void neighborChanged(BlockState state,Level world,BlockPos pos,Block neighbor,BlockPos from,boolean moving) {
        if(world.isClientSide||UPDATING.get())return;
        long bounds=stackBounds(world,pos,state);boolean power=powered(world,pos,bounds);
        if(power==state.getValue(POWERED))return;
        applyColumn(world,pos,bounds,POWERED,power,true);
        if(state.getValue(OPEN)!=power){play(world,pos,power);world.gameEvent(null,power?GameEvent.BLOCK_OPEN:GameEvent.BLOCK_CLOSE,pos);}
    }
    private static void play(Level world,BlockPos pos,boolean open){world.playSound(null,pos,open?SoundEvents.IRON_TRAPDOOR_OPEN:SoundEvents.IRON_TRAPDOOR_CLOSE,SoundSource.BLOCKS,.35F,1.15F);}

}
