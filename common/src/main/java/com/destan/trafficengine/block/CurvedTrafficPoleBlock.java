package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.ITrafficPostLike;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.shapes.*;

/** One static mast arm, split into occupied cells for ordinary chunk rendering and picking.
 * No block entity, custom renderer, world scan or recurring scheduled tick. */
public final class CurvedTrafficPoleBlock extends Block implements SimpleWaterloggedBlock, ITrafficPostLike {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART=IntegerProperty.create("part",0,5);
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    private static final int[][] OFFSETS={{0,0},{1,0},{0,-1},{1,-1},{1,-2},{1,-3}}; // {height, northward Z}
    private static final double[][][] BOXES={
        {{7,0,6.82742,9,3.244443,9},{7,3.007999,6.333774,9,6.212585,8.817209},{7,5.724304,5.544874,9,8.913384,8.277796},{7,8.167006,4.485395,9,11.352946,7.40554},{7,10.357046,3.176819,9,13.537626,6.225349},{7,12.315116,1.633295,9,15.470316,4.76533},{7,14.065371,0.342608,9,16,3.06092},{7,15.240881,0,9,16,1.645838},{7,15.606273,0,9,16,1.125813},{7,15.968598,0,9,16,0.584784}},
        {{7,0,0.342608,9,0.759119,3.06092},{7,0,0,9,1.179602,1.645838},{7,0,0,9,1.591861,1.125813},{7,0,0,9,2.665987,0.584784}},
        {{7,15.240881,15.890715,9,16,16},{7,15.606273,15.415216,9,16,16},{7,15.968598,13.952122,9,16,16}},
        {{7,0,15.890715,9,1.179602,16},{7,0,15.415216,9,1.591861,16},{7,0,13.952122,9,2.665987,16},{7,0.964366,11.833112,9,3.941152,15.006556},{7,2.155017,9.572141,9,5.031639,12.737136},{7,3.178729,7.187326,9,5.950409,10.328686},{7,4.049591,4.703598,9,6.713373,7.812674},{7,4.778738,2.145116,9,7.33566,5.213758},{7,5.378089,0,9,7.781814,2.557363},{7,5.811986,0,9,7.834398,0.173379}},
        {{7,5.378089,15.826621,9,7.781814,16},{7,5.811986,15.550339,9,7.834398,16},{7,5.857563,12.912856,9,8.219289,15.854619},{7,6.234506,10.287624,9,8.508965,13.161524},{7,6.517331,7.690472,9,8.71617,10.472707},{7,6.720344,5.146546,9,8.854849,7.821924},{7,6.856646,2.680819,9,8.938841,5.233619},{7,6.939446,0.316111,9,8.98187,2.732404},{7,6.982067,0,9,8.985542,0.345046},{7,6.985611,0,9,8.997743,0.008638}},
        {{7,6.982067,15.991362,9,8.985542,16},{7,6.985611,14.083057,9,8.997743,16},{7,6.997749,11.934994,9,9.000071,14.090497},{7,6.889443,10.703671,9,9.000071,12.065006},{7,6.590603,9.495686,9,8.837964,11.225776},{7,6.115219,8.458364,9,8.3376,10.504314},{7,5.495686,7.6624,9,7.541636,9.884781},{7,4.774224,7.162036,9,6.504314,9.409397},{7,3.934457,7.0,9,5.296329,9.110557},{7,0,7.0,9,4.065543,9.0}}
    };
    private static final VoxelShape BREAK_SHAPE=box(7,7,7,9,9,9);
    public static VoxelShape destructionShape(){return BREAK_SHAPE;}
    private static final VoxelShape[][] SHAPES=new VoxelShape[OFFSETS.length][4];
    // Only synchronous placement/removal needs this guard; never used by rendering/shape queries.
    private static final ThreadLocal<Boolean> EDITING=ThreadLocal.withInitial(()->false);
    static {
        for(int part=0;part<BOXES.length;part++) {
            VoxelShape north=Shapes.empty();
            for(double[] b:BOXES[part])north=Shapes.or(north,box(b[0],b[1],b[2],b[3],b[4],b[5]));
            north=north.optimize();
            for(Direction facing:Direction.Plane.HORIZONTAL)SHAPES[part][facing.get2DDataValue()]=RailingGeometry.rotate(north,facing);
        }
    }
    public CurvedTrafficPoleBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(2.5F).requiresCorrectToolForDrops()
            .sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PART,0).setValue(WATERLOGGED,false));
    }
    public static int partCount(){return OFFSETS.length;}
    public static BlockPos partPos(BlockPos origin,Direction facing,int part) {
        return origin.relative(facing,-OFFSETS[part][1]).above(OFFSETS[part][0]);
    }
    public static BlockPos masterPos(BlockPos pos,BlockState state) {
        int[] delta=OFFSETS[state.getValue(PART)];
        return pos.relative(state.getValue(FACING),delta[1]).below(delta[0]);
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context) {
        return SHAPES[state.getValue(PART)][state.getValue(FACING).get2DDataValue()];
    }
    @Override public VoxelShape getOcclusionShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.empty();}
    @Override public FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,PART,WATERLOGGED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing=context.getHorizontalDirection();
        return canPlaceAssembly(context,facing)?defaultBlockState().setValue(FACING,facing):null;
    }
    public boolean canPlaceAssembly(BlockPlaceContext context,Direction facing) {
        Level world=context.getLevel();BlockPos origin=context.getClickedPos();Player player=context.getPlayer();
        for(int part=0;part<OFFSETS.length;part++) {
            BlockPos pos=partPos(origin,facing,part);
            if(!world.isLoaded(pos)||!world.isInWorldBounds(pos)||!world.getWorldBorder().isWithinBounds(pos))return false;
            if(player!=null&&(!player.mayBuild()||!world.mayInteract(player,pos)))return false;
            BlockState existing=world.getBlockState(pos);
            if(existing.hasBlockEntity()||!existing.canBeReplaced(BlockPlaceContext.at(context,pos,context.getClickedFace())))return false;
            BlockState state=stateFor(world,pos,facing,part);
            if(!world.isUnobstructed(state,pos,player==null?CollisionContext.empty():CollisionContext.of(player)))return false;
        }
        return true;
    }
    private BlockState stateFor(Level world,BlockPos pos,Direction facing,int part) {
        return defaultBlockState().setValue(FACING,facing).setValue(PART,part)
            .setValue(WATERLOGGED,world.getFluidState(pos).getType()==Fluids.WATER);
    }
    /** Preflight all cells before writing; restore their exact old states on rejection. */
    public boolean placeAssembly(BlockPlaceContext context,BlockState root) {
        Direction facing=root.getValue(FACING);
        if(!canPlaceAssembly(context,facing))return false;
        Level world=context.getLevel();BlockPos origin=context.getClickedPos();
        BlockState[] old=new BlockState[OFFSETS.length];int placed=0;boolean success=false;
        EDITING.set(true);
        try {
            for(int part=0;part<OFFSETS.length;part++) {
                BlockPos pos=partPos(origin,facing,part);old[part]=world.getBlockState(pos);
                BlockState state=stateFor(world,pos,facing,part);
                if(!world.setBlock(pos,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE))return false;
                placed++;
            }
            success=true;
        } finally {
            if(!success)for(int part=placed-1;part>=0;part--)world.setBlock(partPos(origin,facing,part),old[part],Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            EDITING.remove();
        }
        for(int part=0;part<OFFSETS.length;part++) {
            BlockPos pos=partPos(origin,facing,part);
            world.getBlockState(pos).updateNeighbourShapes(world,pos,Block.UPDATE_CLIENTS);
            world.updateNeighborsAt(pos,this);
        }
        return true;
    }
    private boolean matches(BlockState state,Direction facing,int part) {
        return state.is(this)&&state.getValue(FACING)==facing&&state.getValue(PART)==part;
    }
    @Override public void onRemove(BlockState state,Level world,BlockPos pos,BlockState next,boolean moved) {
        super.onRemove(state,world,pos,next,moved);
        if(world.isClientSide||next.is(this)||EDITING.get())return;
        BlockPos master=masterPos(pos,state);Direction facing=state.getValue(FACING);
        if(state.getValue(PART)!=0) {
            if(world.isLoaded(master)&&matches(world.getBlockState(master),facing,0))world.destroyBlock(master,true);
            return;
        }
        EDITING.set(true);
        try {
            for(int part=1;part<OFFSETS.length;part++) {
                BlockPos target=partPos(master,facing,part);
                if(!world.isLoaded(target))continue; // Never load another chunk to remove a prop.
                BlockState current=world.getBlockState(target);
                if(matches(current,facing,part))world.setBlock(target,current.getFluidState().createLegacyBlock(),Block.UPDATE_ALL);
            }
        } finally {EDITING.remove();}
    }
    @Override public BlockState playerWillDestroy(Level world,BlockPos pos,BlockState state,Player player) {
        if(!world.isClientSide&&player.isCreative()&&state.getValue(PART)!=0) {
            BlockPos master=masterPos(pos,state);
            if(world.isLoaded(master)&&matches(world.getBlockState(master),state.getValue(FACING),0))world.destroyBlock(master,false);
        }
        return super.playerWillDestroy(world,pos,state,player);
    }
    @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor world,BlockPos pos,BlockPos neighborPos) {
        if(state.getValue(WATERLOGGED))world.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(world));
        // One coalesced integrity check after neighbour/chunk-edge updates, never every tick.
        if(!world.isClientSide()&&!EDITING.get())world.scheduleTick(pos,this,1);
        return state;
    }
    @Override public void tick(BlockState state,ServerLevel world,BlockPos pos,RandomSource random) {
        BlockPos master=masterPos(pos,state);Direction facing=state.getValue(FACING);
        if(!world.isLoaded(master))return;
        if(!matches(world.getBlockState(master),facing,0)) {
            world.setBlock(pos,state.getFluidState().createLegacyBlock(),Block.UPDATE_ALL);return;
        }
        // Validate the six explicit cells only after an edit; no area scans or rescheduling.
        for(int part=0;part<OFFSETS.length;part++) {
            BlockPos target=partPos(master,facing,part);
            if(world.isLoaded(target)&&!matches(world.getBlockState(target),facing,part)){world.destroyBlock(master,true);return;}
        }
    }
    @Override public BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override public boolean canConnect(BlockState state,Direction direction) {
        return direction==Direction.DOWN&&(state.getValue(PART)==0||state.getValue(PART)==5);
    }
    @Override public boolean canAttach(BlockState state,BlockPos pos,Direction direction){return canConnect(state,direction);}
    @Override protected com.mojang.serialization.MapCodec<? extends Block> codec(){return com.mojang.serialization.MapCodec.unit(this);}
}
