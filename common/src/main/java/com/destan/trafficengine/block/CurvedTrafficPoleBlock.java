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
    public static final BooleanProperty LAMP_SUPPORT=BooleanProperty.create("lamp_support");
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    private static final int[][] OFFSETS={{0,0},{1,0},{0,-1},{1,-1},{1,-2},{1,-3}}; // {height, northward Z}
    private static final double[][][] BOXES={{{7,0.0,6.4286096,9,4.9341457,9.0},{7,4.4301897,4.7294774,9,9.6610565,8.3640755},{7,8.7077482,1.952676,9,13.992044,6.4876592},{7,12.6753271,0,9,16,3.4580852},{7,15.239602,0,9,16,0.6494574}},{{7,0,0,9,0.760398,3.4580852},{7,0,0,9,1.7652172,0.6494574}},{{7,12.6753271,15.3505426,9,16,16},{7,15.239602,14.1912852,9,16,16}},{{7,0,15.3505426,9,0.760398,16},{7,0,14.1912852,9,1.7652172,16},{7,0.1759083,9.582974,9,4.8459464,15.4054092},{7,3.064595,4.3044434,9,7.1265156,10.4922524},{7,5.2197019,0,9,8.5274977,4.9078184}},{{7,5.2197019,14.5623983,9,8.5274977,16},{7,6.5501958,8.5835921,9,9.0,14.8628602},{7,7.0,0,9,9.0,8.5835921}},{{7,7,9,9,9,16},{7,7,7,9,9,9},{7,7,0,9,9,7},{7,0,7,9,7,9}}};
    private static final VoxelShape BREAK_SHAPE=box(7,7,7,9,9,9);
    public static VoxelShape destructionShape(){return BREAK_SHAPE;}
    private static final VoxelShape[] MOUNTED_SHAPES=new VoxelShape[4];
    private static final VoxelShape[][] SHAPES=new VoxelShape[OFFSETS.length][4];
    // Only synchronous placement/removal needs this guard; never used by rendering/shape queries.
    private static final ThreadLocal<Boolean> EDITING=ThreadLocal.withInitial(()->false);
    static {
        for(int part=0;part<BOXES.length;part++) {
            VoxelShape north=Shapes.empty();
            // The final box is the optional downward socket, not part of the bare arm.
            int boxes=BOXES[part].length-(part==5?1:0);
            for(int i=0;i<boxes;i++) {
                double[] b=BOXES[part][i];north=Shapes.or(north,box(b[0],b[1],b[2],b[3],b[4],b[5]));
            }
            north=north.optimize();
            for(Direction facing:Direction.Plane.HORIZONTAL)SHAPES[part][facing.get2DDataValue()]=RailingGeometry.rotate(north,facing);
            if(part==5) {
                VoxelShape mounted=Shapes.or(north,box(7,0,7,9,7,9)).optimize();
                for(Direction facing:Direction.Plane.HORIZONTAL)MOUNTED_SHAPES[facing.get2DDataValue()]=RailingGeometry.rotate(mounted,facing);
            }
        }
    }
    public CurvedTrafficPoleBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(2.5F).requiresCorrectToolForDrops()
            .sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PART,0).setValue(WATERLOGGED,false).setValue(LAMP_SUPPORT,false));
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
        int part=state.getValue(PART), facing=state.getValue(FACING).get2DDataValue();
        return part==5&&state.getValue(LAMP_SUPPORT)?MOUNTED_SHAPES[facing]:SHAPES[part][facing];
    }
    @Override public VoxelShape getOcclusionShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.empty();}
    @Override public FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,PART,WATERLOGGED,LAMP_SUPPORT);}
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
            .setValue(WATERLOGGED,world.getFluidState(pos).getType()==Fluids.WATER)
            .setValue(LAMP_SUPPORT,part==5&&needsLowerSocket(world.getBlockState(pos.below())));
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
    @Override public void playerWillDestroy(Level world,BlockPos pos,BlockState state,Player player) {
        if(!world.isClientSide&&player.isCreative()&&state.getValue(PART)!=0) {
            BlockPos master=masterPos(pos,state);
            if(world.isLoaded(master)&&matches(world.getBlockState(master),state.getValue(FACING),0))world.destroyBlock(master,false);
        }
        super.playerWillDestroy(world,pos,state,player);
    }
    @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor world,BlockPos pos,BlockPos neighborPos) {
        if(state.getValue(WATERLOGGED))world.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(world));
        // One coalesced integrity check after neighbour/chunk-edge updates, never every tick.
        if(!world.isClientSide()&&!EDITING.get())world.scheduleTick(pos,this,1);
        return state.getValue(PART)==5&&direction==Direction.DOWN
            ?state.setValue(LAMP_SUPPORT,needsLowerSocket(neighbor)):state;
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
    /** Face points from the support towards the attached equipment. No world lookup. */
    public static boolean isMountingPost(BlockState support,Direction face) {
        if(support.getBlock() instanceof TrafficSignPostBlock)return true;
        if(!(support.getBlock() instanceof CurvedTrafficPoleBlock))return false;
        int part=support.getValue(PART);
        return face==Direction.DOWN&&(part==0||part==5)
            ||part==5&&face.getAxis()==support.getValue(FACING).getAxis();
    }
    private static boolean needsLowerSocket(BlockState attached) {
        if(attached.getBlock() instanceof TrafficLightBlock || attached.getBlock() instanceof TrafficSignPostBlock)return true;
        if(attached.getBlock() instanceof RoadInformationSignBlock)
            return !attached.getValue(RoadInformationSignBlock.POST_MOUNTED)&&!attached.getValue(RoadInformationSignBlock.WALL_MOUNTED);
        if(attached.getBlock() instanceof TrafficSignBlock)
            return !attached.getValue(TrafficSignBlock.POST_MOUNTED)&&!attached.getValue(TrafficSignBlock.WALL_MOUNTED);
        return attached.getBlock() instanceof LedDeviceBlock && !attached.getValue(LedDeviceBlock.WALL_MOUNTED)
            && attached.getValue(LedDeviceBlock.POST_ABOVE);
    }
    @Override public boolean canConnect(BlockState state,Direction direction) {
        int part=state.getValue(PART);
        return direction==Direction.DOWN&&(part==0||part==5)
            ||part==5&&direction.getAxis()==state.getValue(FACING).getAxis();
    }
    @Override public boolean canAttach(BlockState state,BlockPos pos,Direction direction){return direction==Direction.DOWN&&(state.getValue(PART)==0||state.getValue(PART)==5);}
    
}
