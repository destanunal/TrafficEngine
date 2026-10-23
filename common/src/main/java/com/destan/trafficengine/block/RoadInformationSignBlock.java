package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.ITrafficPostLike;
import com.destan.trafficengine.block.entity.RoadInformationSignBlockEntity;
import com.destan.trafficengine.client.ClientWrapper;
import com.destan.trafficengine.registry.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.item.context.BlockPlaceContext;

public class RoadInformationSignBlock extends WritableTrafficSign implements ITrafficPostLike {
    public enum Kind { HIGHWAY, DIRECTION, VARIABLE_SPEED }
    public static final BooleanProperty UP = BooleanProperty.create("upper_connection");
    public static final BooleanProperty POST_MOUNTED = BooleanProperty.create("post_mounted");
    public static final BooleanProperty WALL_MOUNTED = BooleanProperty.create("wall_mounted");
    private final Kind kind;
    private final VoxelShape[][][][] shapes = new VoxelShape[4][2][2][2];
    public RoadInformationSignBlock(Kind kind) {
        super(Properties.of().mapColor(MapColor.METAL).strength(1.2F).sound(SoundType.METAL).noOcclusion());
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(UP, false).setValue(POST_MOUNTED, false).setValue(WALL_MOUNTED, false));
        for (Direction facing : Direction.Plane.HORIZONTAL) for (int mounted=0;mounted<2;mounted++)
                for (int up=0;up<2;up++) for (int wall=0;wall<2;wall++) {
            shapes[facing.get2DDataValue()][mounted][up][wall] = createShape(kind,facing,mounted==1,up==1,wall==1);
        }
    }
    public static double plateThickness(Kind kind) { return kind==Kind.VARIABLE_SPEED?1:0.625; }
    public static double frontDepth(boolean mounted) { return mounted?22:4; }
    public static double frontDepth(boolean mounted, boolean wallMounted) {
        return wallMounted ? 16-plateThickness(Kind.VARIABLE_SPEED) : frontDepth(mounted);
    }
    public static double topHeight(Kind kind, boolean mounted) {
        return kind==Kind.HIGHWAY?(mounted?20:24):kind==Kind.DIRECTION?14:16;
    }
    private static VoxelShape createShape(Kind kind, Direction facing, boolean mounted, boolean upper, boolean wallMounted) {
        if (kind==Kind.VARIABLE_SPEED && wallMounted) {
            return box(facing,0,0,frontDepth(mounted,true),16,16,16);
        }
        return createShape(kind,facing,mounted,upper);
    }
    private static VoxelShape createShape(Kind kind, Direction facing, boolean mounted, boolean upper) {
        double minX=kind==Kind.VARIABLE_SPEED?0:-8, maxX=kind==Kind.VARIABLE_SPEED?16:24;
        double minY=kind==Kind.DIRECTION?2:kind==Kind.HIGHWAY && mounted?-4:0;
        double top=topHeight(kind,mounted), front=frontDepth(mounted), back=front+plateThickness(kind);
        VoxelShape shape=box(facing,minX,minY,front,maxX,top,back);
        if (mounted) {
            if(back<23) shape=Shapes.or(shape,box(facing,7,7,back,9,9,23));
        } else {
            // A highway panel extends above its block; a connected upper pole
            // already supplies that part of the carrier.
            double carrierTop=upper?16:kind==Kind.DIRECTION?14:top;
            shape=Shapes.or(shape,box(facing,7,0,7,9,carrierTop,9));
            double low=kind==Kind.DIRECTION?5:3, high=kind==Kind.HIGHWAY?19:kind==Kind.DIRECTION?9:11;
            shape=Shapes.or(shape,box(facing,7,low,back,9,low+2,9),box(facing,7,high,back,9,high+2,9));
        }
        return shape.optimize();
    }
    private static VoxelShape box(Direction facing,double x1,double y1,double z1,double x2,double y2,double z2) {
        return switch(facing) {
            case EAST -> Block.box(16-z2,y1,x1,16-z1,y2,x2);
            case SOUTH -> Block.box(16-x2,y1,16-z2,16-x1,y2,16-z1);
            case WEST -> Block.box(z1,y1,16-x2,z2,y2,16-x1);
            default -> Block.box(x1,y1,z1,x2,y2,z2);
        };
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(UP,POST_MOUNTED,WALL_MOUNTED);
    }
    private static boolean upperConnection(BlockState neighbor) {
        return neighbor.getBlock() instanceof ITrafficPostLike post && post.canConnect(neighbor, Direction.DOWN);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state=super.getStateForPlacement(context);
        Direction face=context.getClickedFace();
        if(face.getAxis().isHorizontal()) {
            boolean sideOfPost=context.getLevel().getBlockState(context.getClickedPos().relative(face.getOpposite()))
                    .getBlock() instanceof TrafficSignPostBlock;
            if (sideOfPost) {
                state=state.setValue(FACING,face).setValue(POST_MOUNTED,true);
            } else if (kind==Kind.VARIABLE_SPEED) {
                state=state.setValue(FACING,face).setValue(WALL_MOUNTED,true);
            }
        }
        return state.setValue(UP,!state.getValue(POST_MOUNTED) && !state.getValue(WALL_MOUNTED)
            && upperConnection(context.getLevel().getBlockState(context.getClickedPos().above())));
    }
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return super.updateShape(state,direction,neighbor,level,pos,neighborPos)
            .setValue(UP,!state.getValue(POST_MOUNTED) && !state.getValue(WALL_MOUNTED) && upperConnection(level.getBlockState(pos.above())));
    }
    public Kind kind() { return kind; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RoadInformationSignBlockEntity(pos, state);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[state.getValue(FACING).get2DDataValue()][state.getValue(POST_MOUNTED)?1:0][state.getValue(UP)?1:0][state.getValue(WALL_MOUNTED)?1:0];
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).is(ModItemTags.WRENCHES)) {
            if (level.isClientSide && level.getBlockEntity(pos) instanceof RoadInformationSignBlockEntity sign)
                ClientWrapper.showRoadInformationSignScreen(sign);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
    @Override public boolean canAttach(BlockState state, BlockPos pos, Direction direction) { return !state.getValue(POST_MOUNTED) && !state.getValue(WALL_MOUNTED); }
    @Override public boolean canConnect(BlockState state, Direction direction) {
        return !state.getValue(POST_MOUNTED) && !state.getValue(WALL_MOUNTED) && (direction==Direction.UP || direction==Direction.DOWN);
    }
}
