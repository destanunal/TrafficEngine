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
    private static final double[][][] BOXES={{{7,0,7,9,8.0,9},{7,8.0,6.8462822,9,9.9509032,9.0},{7,9.5607226,6.3910363,9,11.8268343,8.8078528},{7,11.0614675,5.6517569,9,13.5557023,8.2387953},{7,12.4445619,4.6568542,9,15.0710678,7.3146961},{7,13.6568542,3.4445619,9,16,6.0710678},{7,14.6517569,2.6649828,9,16,4.5557023},{7,15.1111111,2.0614675,9,16,3.5812285},{7,15.3910363,0.5607226,9,16,2.8268343},{7,15.8462822,0,9,16,0.9509032},{7,15.9504639,0,9,16,0.1111111}},{{7,0,3.4445619,9,0.3146961,6.0710678},{7,0,2.6649828,9,0.8888889,4.5557023},{7,0,2.0614675,9,1.2387953,3.5812285},{7,0,0.5607226,9,1.8078528,2.8268343},{7,0,0,9,1.9380799,0.9509032},{7,0,0,9,2.0,0.1111111}},{{7,15.8462822,15.8888889,9,16,16},{7,15.9504639,15.0,9,16,16}},{{7,0,15.8888889,9,1.9380799,16},{7,0,15.0,9,2.0,16},{7,0.0,0,9,2.0,15.0}},{{7,0,0,9,2,16}},{{7,0,7,9,2,16}}};
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
