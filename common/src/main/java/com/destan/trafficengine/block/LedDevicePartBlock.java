package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.LedDeviceType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.BlockHitResult;

/** Invisible selection-only cells belonging to a multi-block traffic display. */
public class LedDevicePartBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 2);
    public static final IntegerProperty VERTICAL_OFFSET = IntegerProperty.create("vertical_offset", 0, 2);
    public static final BooleanProperty SIDE = BooleanProperty.create("side");
    // Block-state integer values cannot be negative; 0, 1 and 2 represent
    // the physical offsets -1, 0 and 1 respectively.
    public static final IntegerProperty OFFSET = IntegerProperty.create("offset", 0, 3);
    public static final BooleanProperty CENTERED = BooleanProperty.create("centered");
    public static final BooleanProperty HANGING = BooleanProperty.create("hanging");
    public static final BooleanProperty WALL_MOUNTED = BooleanProperty.create("wall_mounted");

    public LedDevicePartBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0F)
            .sound(SoundType.METAL).noCollission().noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
            .setValue(UPPER, false).setValue(ROW, 0).setValue(VERTICAL_OFFSET, 1)
            .setValue(SIDE, false).setValue(OFFSET, 1).setValue(CENTERED, false).setValue(HANGING, false)
            .setValue(WALL_MOUNTED, false));
    }

    private static Direction widthDirection(Direction facing) {
        return facing.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
    }

    private static BlockPos masterPos(BlockPos pos, BlockState state) {
        int row = state.getValue(ROW);
        int verticalOffset;
        if (row > 0 || (state.getValue(UPPER) && state.getValue(VERTICAL_OFFSET) == 1)) {
            // Compatibility with parts saved before VERTICAL_OFFSET existed.
            int legacyRow = Math.max(1, row);
            verticalOffset = state.getValue(HANGING) ? -legacyRow : legacyRow;
        } else {
            verticalOffset = state.getValue(VERTICAL_OFFSET) - 1;
        }
        BlockPos master = pos.below(verticalOffset);
        return master.relative(widthDirection(state.getValue(FACING)), 1 - state.getValue(OFFSET));
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        double from = 0;
        double to = 16;
        int offset = state.getValue(OFFSET) - 1;
        BlockState masterState = level.getBlockState(masterPos(pos, state));
        boolean halfWidthEdge = masterState.getBlock() instanceof LedDeviceBlock ledDevice
            && ledDevice.getDeviceType().getDisplaySize() == 2;
        if (state.getValue(CENTERED) && halfWidthEdge) {
            if (offset < 0) from = 8;
            if (offset > 0) to = 8;
        }
        if (!state.getValue(WALL_MOUNTED)) {
            return switch (facing) {
                case NORTH -> Block.box(from, 0, 5.5, to, 16, 7);
                case SOUTH -> Block.box(from, 0, 9, to, 16, 10.5);
                case EAST -> Block.box(9, 0, from, 10.5, 16, to);
                default -> Block.box(5.5, 0, from, 7, 16, to);
            };
        }
        return switch (facing) {
            case NORTH -> Block.box(from, 0, 14.5, to, 16, 16);
            case SOUTH -> Block.box(from, 0, 0, to, 16, 1.5);
            case WEST -> Block.box(14.5, 0, from, 16, 16, to);
            default -> Block.box(0, 0, from, 1.5, 16, to);
        };
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos masterPos = masterPos(pos, state);
            BlockState masterState = level.getBlockState(masterPos);
            if (masterState.getBlock() instanceof LedDeviceBlock ledDevice
                && ledDevice.getDeviceType().isTrafficDisplay()) {
                level.destroyBlock(masterPos, !player.isCreative(), player);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos masterPos = masterPos(pos, state);
        BlockState masterState = level.getBlockState(masterPos);
        if (masterState.getBlock() instanceof LedDeviceBlock ledDevice
            && ledDevice.getDeviceType().isTrafficDisplay()) {
            return ledDevice.use(masterState, level, masterPos, player, hand,
                new BlockHitResult(hit.getLocation(), hit.getDirection(), masterPos, hit.isInside()));
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, UPPER, ROW, VERTICAL_OFFSET, SIDE, OFFSET, CENTERED, HANGING, WALL_MOUNTED);
    }
}
