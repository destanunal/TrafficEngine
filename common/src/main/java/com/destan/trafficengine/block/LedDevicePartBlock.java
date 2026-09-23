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

/** Invisible selection-only cells belonging to the other three quarters of a 2x2 display. */
public class LedDevicePartBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");
    public static final BooleanProperty SIDE = BooleanProperty.create("side");
    // Block-state integer values cannot be negative; 0, 1 and 2 represent
    // the physical offsets -1, 0 and 1 respectively.
    public static final IntegerProperty OFFSET = IntegerProperty.create("offset", 0, 2);
    public static final BooleanProperty CENTERED = BooleanProperty.create("centered");
    public static final BooleanProperty HANGING = BooleanProperty.create("hanging");
    public static final BooleanProperty WALL_MOUNTED = BooleanProperty.create("wall_mounted");

    public LedDevicePartBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0F)
            .sound(SoundType.METAL).noCollission().noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
            .setValue(UPPER, false).setValue(SIDE, false).setValue(OFFSET, 1).setValue(CENTERED, false).setValue(HANGING, false)
            .setValue(WALL_MOUNTED, false));
    }

    private static Direction widthDirection(Direction facing) {
        return facing.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
    }

    private static BlockPos masterPos(BlockPos pos, BlockState state) {
        BlockPos master = state.getValue(UPPER)
            ? (state.getValue(HANGING) ? pos.above() : pos.below()) : pos;
        return master.relative(widthDirection(state.getValue(FACING)), 1 - state.getValue(OFFSET));
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        double from = 0;
        double to = 16;
        int offset = state.getValue(OFFSET) - 1;
        if (state.getValue(CENTERED)) {
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
                && ledDevice.getDeviceType() == LedDeviceType.TRAFFIC_DISPLAY) {
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
            && ledDevice.getDeviceType() == LedDeviceType.TRAFFIC_DISPLAY) {
            return ledDevice.use(masterState, level, masterPos, player, hand,
                new BlockHitResult(hit.getLocation(), hit.getDirection(), masterPos, hit.isInside()));
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, UPPER, SIDE, OFFSET, CENTERED, HANGING, WALL_MOUNTED);
    }
}
