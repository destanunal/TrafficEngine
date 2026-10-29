package com.destan.trafficengine.block;

import com.destan.trafficengine.data.RoadSignDesign;
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
    public enum Kind { HIGHWAY, DIRECTION, VARIABLE_SPEED, LARGE }
    public static final BooleanProperty UP = BooleanProperty.create("upper_connection");
    public static final BooleanProperty POST_MOUNTED = BooleanProperty.create("post_mounted");
    public static final BooleanProperty WALL_MOUNTED = BooleanProperty.create("wall_mounted");
    public static final BooleanProperty DIAGONAL = BooleanProperty.create("diagonal");
    public static final BooleanProperty HANGING = BooleanProperty.create("hanging");
    private final Kind kind;
    private final VoxelShape[][][][][][] shapes = new VoxelShape[4][2][2][2][2][2];
    public RoadInformationSignBlock(Kind kind) {
        super(Properties.of().mapColor(MapColor.METAL).strength(1.2F).sound(SoundType.METAL).noOcclusion());
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(UP,false).setValue(POST_MOUNTED,false)
            .setValue(WALL_MOUNTED,false).setValue(DIAGONAL,false).setValue(HANGING,false));
        // Alias equivalent mounting states. No rotation, allocation or world reads on lookup.
        var unique = new java.util.HashMap<Integer,VoxelShape[]>();
        for (Direction facing : Direction.Plane.HORIZONTAL) for (int mounted=0;mounted<2;mounted++)
                for (int up=0;up<2;up++) for (int wall=0;wall<2;wall++) for (int hanging=0;hanging<2;hanging++) {
            boolean w=kind==Kind.VARIABLE_SPEED && wall==1;
            boolean m=!w && mounted==1;
            boolean h=!w && !m && kind!=Kind.VARIABLE_SPEED && hanging==1;
            boolean u=!w && !m && !h && kind!=Kind.VARIABLE_SPEED && up==1;
            int key=facing.get2DDataValue()*16+(w?8:0)+(m?4:0)+(h?2:0)+(u?1:0);
            VoxelShape[] pair=unique.get(key);
            if(pair==null) {
                var shape=createShape(kind,facing,m,u,w,h);
                pair=new VoxelShape[]{shape,com.destan.trafficengine.block.data.DiagonalVoxelShapes.rotateClockwise45(shape,.5,.5,1.0)};
                unique.put(key,pair);
            }
            shapes[facing.get2DDataValue()][mounted][up][wall][0][hanging]=pair[0];
            shapes[facing.get2DDataValue()][mounted][up][wall][1][hanging]=pair[1];
        }
    }
    public static double plateThickness(Kind kind) { return kind==Kind.VARIABLE_SPEED?1:0.5; }
    public static double frontDepth(boolean mounted) { return mounted?22:4; }
    public static double frontDepth(boolean mounted, boolean wallMounted) {
        return wallMounted ? 16-plateThickness(Kind.VARIABLE_SPEED) : frontDepth(mounted);
    }
    public static double topHeight(Kind kind, boolean mounted) {
        return kind==Kind.LARGE?(mounted?32:48):kind==Kind.HIGHWAY?(mounted?20:24):kind==Kind.DIRECTION?14:16;
    }
    public static double topHeight(Kind kind, boolean mounted, boolean hanging) {
        return hanging && !mounted ? 16 : topHeight(kind,mounted);
    }
    private static VoxelShape createShape(Kind kind, Direction facing, boolean mounted, boolean upper, boolean wallMounted, boolean hanging) {
        if (kind==Kind.VARIABLE_SPEED && wallMounted) {
            return box(facing,0,0,frontDepth(mounted,true),16,16,16);
        }
        return createShape(kind,facing,mounted,upper,hanging && !mounted);
    }
    private static VoxelShape createShape(Kind kind, Direction facing, boolean mounted, boolean upper, boolean hanging) {
        double minX=kind==Kind.LARGE?-24:kind==Kind.VARIABLE_SPEED?0:-8, maxX=kind==Kind.LARGE?40:kind==Kind.VARIABLE_SPEED?16:24;
        double minY=kind==Kind.LARGE && mounted?-16:kind==Kind.DIRECTION?2:kind==Kind.HIGHWAY && mounted?-4:0;
        double top=topHeight(kind,mounted), front=frontDepth(mounted), back=front+plateThickness(kind);
        VoxelShape shape=box(facing,minX,minY,front,maxX,top,back);
        // Hidden rear brackets are excluded from the selection union: their
        // short edges otherwise show through the sign artwork when targeted.
        if (mounted && kind==Kind.VARIABLE_SPEED && back<23)
            shape=Shapes.or(shape,box(facing,7,7,back,9,9,23));
        if (!mounted) {
            // A highway panel extends above its block; a connected upper pole
            // already supplies that part of the carrier.
            double carrierTop=hanging?top:upper?16:kind==Kind.DIRECTION?8.75:kind==Kind.HIGHWAY?19.23:kind==Kind.LARGE?37.71:top;
            shape=Shapes.or(shape,box(facing,7,0,7,9,carrierTop,9));
            if(kind==Kind.LARGE && upper)shape=Shapes.or(shape,box(facing,7,32,7,9,48,9));
            if(kind==Kind.VARIABLE_SPEED)shape=Shapes.or(shape,box(facing,7,3,back,9,5,9),box(facing,7,11,back,9,13,9));
        }
        return shape.move(0,hanging?(16-top)/16:0,0).optimize();
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
        super.createBlockStateDefinition(builder); builder.add(UP,POST_MOUNTED,WALL_MOUNTED,DIAGONAL,HANGING);
    }
    private static boolean upperConnection(BlockState neighbor) {
        return neighbor.getBlock() instanceof ITrafficPostLike post && post.canConnect(neighbor, Direction.DOWN);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state=super.getStateForPlacement(context);
        if (kind!=Kind.VARIABLE_SPEED && context.getPlayer()!=null) {
            int sector=Math.floorMod((int)Math.floor((context.getPlayer().getYRot()+22.5F)/45F),8);
            state=withSector(state,sector);
        }
        Direction face=context.getClickedFace();
        if(face.getAxis().isHorizontal()) {
            boolean sideOfPost=CurvedTrafficPoleBlock.isMountingPost(context.getLevel().getBlockState(
                    context.getClickedPos().relative(face.getOpposite())),face);
            if (sideOfPost) {
                state=state.setValue(FACING,face).setValue(POST_MOUNTED,true).setValue(DIAGONAL,false);
            } else if (kind==Kind.VARIABLE_SPEED) {
                state=state.setValue(FACING,face).setValue(WALL_MOUNTED,true);
            }
        }
        return connections(state,context.getLevel().getBlockState(context.getClickedPos().above()));
    }
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return connections(super.updateShape(state,direction,neighbor,level,pos,neighborPos),level.getBlockState(pos.above()));
    }
    private static BlockState connections(BlockState state, BlockState above) {
        boolean upright=!state.getValue(POST_MOUNTED) && !state.getValue(WALL_MOUNTED);
        boolean hanging=upright && above.getBlock() instanceof CurvedTrafficPoleBlock
            && above.getValue(CurvedTrafficPoleBlock.PART)==5;
        return state.setValue(HANGING,hanging).setValue(UP,upright && !hanging && upperConnection(above));
    }
    private static BlockState withSector(BlockState state,int sector) {
        Direction facing=switch ((sector&7)/2) { case 1 -> Direction.EAST; case 2 -> Direction.SOUTH; case 3 -> Direction.WEST; default -> Direction.NORTH; };
        return state.setValue(FACING,facing).setValue(DIAGONAL,(sector&1)!=0);
    }
    @Override public BlockState mirror(BlockState state,net.minecraft.world.level.block.Mirror mirror) {
        if (mirror==net.minecraft.world.level.block.Mirror.NONE) return state;
        int sector=switch(state.getValue(FACING)) { case EAST -> 2; case SOUTH -> 4; case WEST -> 6; default -> 0; };
        if (state.getValue(DIAGONAL)) sector++;
        return withSector(state,mirror==net.minecraft.world.level.block.Mirror.LEFT_RIGHT?4-sector:-sector);
    }
    public Kind kind() { return kind; }
    public static int rowCapacity(Kind kind) { return kind==Kind.LARGE?4:kind==Kind.HIGHWAY?3:2; }
    public static int maxTextSize(Kind kind) { return kind==Kind.LARGE?RoadSignDesign.MAX_TEXT_SIZE:200; }
    public static int widthInBlocks(Kind kind) { return kind==Kind.LARGE?4:2; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RoadInformationSignBlockEntity(pos, state);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[state.getValue(FACING).get2DDataValue()][state.getValue(POST_MOUNTED)?1:0][state.getValue(UP)?1:0][state.getValue(WALL_MOUNTED)?1:0][state.getValue(DIAGONAL)?1:0][state.getValue(HANGING)?1:0];
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
