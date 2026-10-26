package com.destan.trafficengine.block;

import com.destan.trafficengine.registry.ModItemTags;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Terrain-aligned panel spanning toward FACING. Geometry is cached; terrain is read only on edits. */
public final class SlopedPedestrianRailingBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    public static final IntegerProperty RISE=IntegerProperty.create("rise",0,8);
    public static final IntegerProperty SINK=PedestrianRailingBlock.SINK;
    public static final BooleanProperty GRAY=PedestrianRailingBlock.GRAY;
    public static final BooleanProperty DESCENDING=BooleanProperty.create("descending");
    public static final BooleanProperty BACK=BooleanProperty.create("back_connection");
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    private static final VoxelShape[][][][][][] SHAPES=new VoxelShape[2][9][8][4][2][2];
    public SlopedPedestrianRailingBlock(){
        super(Properties.of().mapColor(MapColor.METAL).strength(2F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion());
        registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(RISE,1).setValue(DESCENDING,false).setValue(SINK,0).setValue(GRAY,true).setValue(BACK,false).setValue(WATERLOGGED,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,RISE,SINK,GRAY,BACK,WATERLOGGED,DESCENDING);}
    private static int supportHeight(BlockGetter world,BlockPos pos){
        BlockState below=world.getBlockState(pos.below());
        if(below.getBlock() instanceof AsphaltSlope||below.getBlock() instanceof AsphaltCurbSlope||below.getBlock() instanceof SidewalkSlopeBlock)return -RailingGeometry.sinkFor(below);
        return below.isCollisionShapeFullBlock(world,pos.below())?0:Integer.MIN_VALUE;
    }
    private static int grade(BlockGetter world,BlockPos pos,Direction facing,int sink){
        // Include a support block one level lower, so slab-to-ground descents work.
        for(int dy=1;dy>=-1;dy--){
            BlockPos target=pos.relative(facing).above(dy);
            if(world instanceof Level level&&!level.isLoaded(target.below()))continue;
            int height=supportHeight(world,target);
            if(height==Integer.MIN_VALUE)continue;
            int rise=height+dy*8+sink;
            if(rise>=-8&&rise<=8&&rise>=sink-8)return rise;
        }
        return Integer.MIN_VALUE;
    }
    public static BlockState connections(BlockState state,BlockGetter world,BlockPos pos){
        int sink=RailingGeometry.sinkFor(world.getBlockState(pos.below()));
        BlockPos behind=pos.relative(state.getValue(FACING).getOpposite());BlockState other=world.getBlockState(behind);
        boolean back=other.getBlock() instanceof PedestrianRailingBlock&&other.getValue(SINK)==sink;
        return state.setValue(SINK,sink).setValue(BACK,back);
    }
    public static BlockState terrainProfile(BlockState state,BlockGetter world,BlockPos pos){
        BlockState anchored=connections(state,world,pos);
        int rise=grade(world,pos,anchored.getValue(FACING),anchored.getValue(SINK));
        return rise==Integer.MIN_VALUE?anchored:anchored.setValue(RISE,Math.abs(rise)).setValue(DESCENDING,rise<0);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        BlockGetter world=context.getLevel();BlockPos pos=context.getClickedPos();Direction preferred=context.getHorizontalDirection();
        int sink=RailingGeometry.sinkFor(world.getBlockState(pos.below()));
        Direction facing=preferred;int rise=grade(world,pos,facing,sink);
        // Prefer the requested axis, but find the real incline if it is viewed from its side.
        if(rise==0||rise==Integer.MIN_VALUE)for(Direction candidate:new Direction[]{preferred.getOpposite(),preferred.getClockWise(),preferred.getCounterClockWise()}){
            int alternate=grade(world,pos,candidate,sink);
            if(alternate!=Integer.MIN_VALUE&&alternate!=0){facing=candidate;rise=alternate;break;}
        }
        return connections(defaultBlockState().setValue(FACING,facing).setValue(RISE,rise==Integer.MIN_VALUE?1:Math.abs(rise)).setValue(DESCENDING,rise!=Integer.MIN_VALUE&&rise<0)
            .setValue(WATERLOGGED,context.getLevel().getFluidState(pos).getType()==Fluids.WATER),world,pos);
    }
    @Override public BlockState updateShape(BlockState state,Direction side,BlockState other,LevelAccessor world,BlockPos pos,BlockPos otherPos){
        if(state.getValue(WATERLOGGED))world.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(world));
        return side==Direction.DOWN||side.getAxis().isHorizontal()?terrainProfile(state,world,pos):state;
    }
    /** Only a changed ramp layer schedules these checks; never on ordinary frame/tick paths. */
    public static void terrainChanged(Level world,BlockPos support){
        if(world.isClientSide)return;
        for(Direction direction:Direction.Plane.HORIZONTAL)for(int dy=0;dy<=2;dy++){
            BlockPos target=support.relative(direction).above(dy);
            if(!world.isLoaded(target))continue;
            BlockState state=world.getBlockState(target);
            if(state.getBlock() instanceof SlopedPedestrianRailingBlock)world.scheduleTick(target,state.getBlock(),1);
        }
    }
    @Override public void tick(BlockState state,net.minecraft.server.level.ServerLevel world,BlockPos pos,net.minecraft.util.RandomSource random){
        BlockState updated=terrainProfile(state,world,pos);
        if(updated!=state)world.setBlockAndUpdate(pos,updated);
    }
    public InteractionResult use(BlockState state,Level world,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!player.getItemInHand(hand).is(ModItemTags.WRENCHES))return InteractionResult.PASS;
        if(!player.mayBuild()||!world.mayInteract(player,pos))return InteractionResult.FAIL;
        if(!world.isClientSide)world.setBlockAndUpdate(pos,player.isShiftKeyDown()?state.cycle(RISE):state.cycle(GRAY));
        return InteractionResult.sidedSuccess(world.isClientSide);
    }
    /** Keep the lower rail above both halves of the stepped support, including a full-block grade. */
    public static double lowerRailHeight(int rise){return 2.5+Math.abs(rise);}
    private static VoxelShape cached(BlockState state,boolean collision){
        int c=collision?1:0,s=state.getValue(SINK),d=state.getValue(FACING).get2DDataValue(),b=state.getValue(BACK)?1:0;
        boolean descending=state.getValue(DESCENDING);int r=state.getValue(RISE);
        if(descending)r=Math.min(r,8-s);
        int fall=descending&&r>0?1:0;
        VoxelShape found=SHAPES[c][r][s][d][b][fall];if(found!=null)return found;
        double drop=s*2,rise=r*(fall==1?-2:2),top=collision?24:16;
        VoxelShape north=Shapes.or(box(7,-drop,7,9,top-drop,9),box(7.1,rise-drop,-8.9,8.9,top+rise-drop-.25,-7.1));
        for(int i=0;i<8;i++){
            double z1=8-i*2,z0=z1-2,y0=rise*i/8-drop,y1=rise*(i+1)/8-drop;
            north=Shapes.or(north,box(7.35,lowerRailHeight(r)+Math.min(y0,y1),z0,8.65,top+Math.max(y0,y1),z1));
        }
        if(b==1)north=Shapes.or(north,box(7.35,2.5-drop,8,8.65,top-drop,16));
        found=RailingGeometry.rotate(north.optimize(),state.getValue(FACING));SHAPES[c][r][s][d][b][fall]=found;return found;
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return cached(state,false);}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return cached(state,true);}
    @Override public VoxelShape getOcclusionShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.empty();}
    @Override public FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override public BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public BlockState mirror(BlockState state,Mirror mirror){return state.setValue(FACING,mirror.mirror(state.getValue(FACING)));}
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState state,Level world,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return com.destan.trafficengine.util.PortingInteractions.forItem(use(state,world,pos,player,hand,hit));}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level world,BlockPos pos,Player player,BlockHitResult hit){return use(state,world,pos,player,InteractionHand.MAIN_HAND,hit);}
    @Override public com.mojang.serialization.MapCodec<net.minecraft.world.level.block.HorizontalDirectionalBlock> codec(){return com.mojang.serialization.MapCodec.unit(this);}
}
