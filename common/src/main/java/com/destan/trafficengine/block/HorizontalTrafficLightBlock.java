package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.TrafficLightModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HorizontalTrafficLightBlock extends TrafficLightBlock {
    public static final BooleanProperty VERTICAL_MOUNT=BooleanProperty.create("vertical_mount");
    public static final BooleanProperty BOTTOM_SUPPORT=BooleanProperty.create("bottom_support");
    public static final VoxelShape POLE_X=Block.box(0,7,7,16,9,9);
    public static final VoxelShape POLE_Z=Block.box(7,7,0,9,9,16);
    private static final VoxelShape VERTICAL_CARRIER=Block.box(7,6.72,7,9,16,9);
    private static final VoxelShape BOTTOM_CARRIER=Block.box(7,0,7,9,9.28,9);
    private static final VoxelShape[][][] SHAPES=new VoxelShape[3][TrafficLightModel.values().length][4];
    static {
        for(TrafficLightModel model:TrafficLightModel.values()) {
            double half=2.5*(model.getLightsCount()-1);
            VoxelShape housing=Block.box(4.5-half,4.5,.5,11.5+half,11.5,7);
            for(int vertical=0;vertical<3;vertical++) {
                VoxelShape north=Shapes.or(housing,vertical==2?BOTTOM_CARRIER:vertical==1?VERTICAL_CARRIER:POLE_X).optimize();
                for(Direction facing:Direction.Plane.HORIZONTAL)
                    SHAPES[vertical][model.ordinal()][facing.get2DDataValue()]=RailingGeometry.rotate(north,facing);
            }
        }
    }
    public HorizontalTrafficLightBlock(){
        super();registerDefaultState(defaultBlockState().setValue(VERTICAL_MOUNT,false).setValue(BOTTOM_SUPPORT,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){
        super.createBlockStateDefinition(builder);builder.add(VERTICAL_MOUNT,BOTTOM_SUPPORT);
    }
    /** Only placement and vertical neighbour edits inspect support; rendering/shape lookups never do. */
    public static boolean isVerticalSupport(BlockState support,Direction side){
        if(support.getBlock() instanceof CurvedTrafficPoleBlock pole)return pole.canConnect(support,side);
        return support.getBlock() instanceof TrafficSignPostBlock
            &&support.getValue(TrafficSignPostBlock.AXIS)==Direction.Axis.Y;
    }
    public static BlockState mountedState(BlockState state,BlockGetter world,BlockPos pos){
        boolean above=isVerticalSupport(world.getBlockState(pos.above()),Direction.DOWN);
        boolean below=isVerticalSupport(world.getBlockState(pos.below()),Direction.UP);
        return state.setValue(VERTICAL_MOUNT,above||below).setValue(BOTTOM_SUPPORT,!above&&below);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        return mountedState(defaultBlockState().setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).getType()==Fluids.WATER)
            .setValue(FACING,context.getHorizontalDirection().getOpposite()).setValue(DIAGONAL,false),
            context.getLevel(),context.getClickedPos());
    }
    @Override public BlockState updateShape(BlockState state,Direction side,BlockState neighbour,LevelAccessor world,BlockPos pos,BlockPos other){
        BlockState updated=super.updateShape(state,side,neighbour,world,pos,other);
        return side.getAxis()==Direction.Axis.Y?mountedState(updated,world,pos):updated;
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){
        return SHAPES[state.getValue(VERTICAL_MOUNT)?(state.getValue(BOTTOM_SUPPORT)?2:1):0][state.getValue(MODEL).ordinal()][state.getValue(FACING).get2DDataValue()];
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return getShape(state,world,pos,context);}
    @Override public VoxelShape getOcclusionShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.empty();}
    @Override public boolean canConnect(BlockState state,Direction side){
        return state.getValue(VERTICAL_MOUNT)?side==(state.getValue(BOTTOM_SUPPORT)?Direction.DOWN:Direction.UP):
            side.getAxis().isHorizontal()&&side.getAxis()!=state.getValue(FACING).getAxis();
    }
}
