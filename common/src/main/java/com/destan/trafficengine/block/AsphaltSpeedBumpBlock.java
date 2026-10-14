package com.destan.trafficengine.block;

import java.util.List;
import com.destan.trafficengine.block.data.ColorableBlock;
import com.destan.trafficengine.registry.ModItemTags;
import com.destan.trafficengine.util.ColorValue;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One inventory item per road feature; neighbours supply its ramps or end caps. */
public class AsphaltSpeedBumpBlock extends ColorableBlock implements SimpleWaterloggedBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty START = BooleanProperty.create("connected_start");
    public static final BooleanProperty END = BooleanProperty.create("connected_end");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape[] SHAPES = createShapes();

    public AsphaltSpeedBumpBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5F)
            .sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
            .setValue(START, false).setValue(END, false).setValue(WATERLOGGED, false));
    }

    public Direction startDirection(BlockState state) {
        Direction facing = state.getValue(FACING);
        return facing.getCounterClockWise();
    }

    private boolean connects(BlockState neighbour, BlockState state) {
        return neighbour.is(this)
            && neighbour.getValue(FACING).getAxis() == state.getValue(FACING).getAxis();
    }

    private BlockState connections(BlockState state, LevelAccessor level, BlockPos pos) {
        Direction start = startDirection(state);
        return state.setValue(START, connects(level.getBlockState(pos.relative(start)), state))
            .setValue(END, connects(level.getBlockState(pos.relative(start.getOpposite())), state));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection())
            .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
        return connections(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
            LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        Direction start = startDirection(state);
        if (direction == start) return state.setValue(START, connects(neighbour, state));
        if (direction == start.getOpposite()) return state.setValue(END, connects(neighbour, state));
        return state;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).is(ModItemTags.WRENCHES)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, connections(rotate(state, Rotation.CLOCKWISE_90), level, pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult onSetColor(UseOnContext context) {
        // Reuse the existing brush colour sync without replacing the road feature.
        if (context.getLevel().isClientSide) return InteractionResult.CONSUME;
        return super.onSetColor(context);
    }

    @Override
    public ColorValue getDefaultColor() {
        return ColorValue.WHITE;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        Direction facing = state.getValue(FACING);
        Direction mirrored = mirror.mirror(facing);
        Direction start = startDirection(state);
        Direction newStart = mirrored.getCounterClockWise();
        boolean swap = mirror.mirror(start) != newStart;
        return state.setValue(FACING, mirrored)
            .setValue(START, swap ? state.getValue(END) : state.getValue(START))
            .setValue(END, swap ? state.getValue(START) : state.getValue(END));
    }

    @Override
    public void appendHoverText(ItemStack stack, BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        String id = "rubber_speed_bump"; // Kept for compatibility with existing worlds.
        tooltip.add(Component.translatable("block.trafficengine." + id + ".tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.trafficengine.modular_road.tooltip.controls").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, START, END, WATERLOGGED);
    }

    /** A simple effect shape avoids spawning particles for every collision ramp step. */
    public static VoxelShape destructionShape(BlockState state) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z
            ? Block.box(0, 0, 0.2, 16, 2.6, 15.8)
            : Block.box(0.2, 0, 0, 15.8, 2.6, 16);
    }

    private static VoxelShape[] createShapes() {
        VoxelShape[] shapes = new VoxelShape[4];
        double height = 2.605; // Includes the thin painted surface.
        double rampLength = 2.6 / Math.tan(Math.PI / 8);
        double toe = 6.5 - rampLength;
        // Each tier covers its complete ramp section, including the ground-level toes.
        VoxelShape north = Shapes.or(
            Block.box(0, 0, toe, 16, height * 0.25, 16 - toe),
            Block.box(0, height * 0.25, toe + rampLength * 0.25, 16, height * 0.625, 16 - toe - rampLength * 0.25),
            Block.box(0, height * 0.625, toe + rampLength * 0.625, 16, height, 16 - toe - rampLength * 0.625));
        VoxelShape current = north.optimize();
        for (Direction direction : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            shapes[direction.get2DDataValue()] = current;
            VoxelShape rotated = Shapes.empty();
            for (AABB box : current.toAabbs()) {
                rotated = Shapes.or(rotated, Shapes.box(1 - box.maxZ, box.minY, box.minX,
                    1 - box.minZ, box.maxY, box.maxX));
            }
            current = rotated.optimize();
        }
        return shapes;
    }
}
