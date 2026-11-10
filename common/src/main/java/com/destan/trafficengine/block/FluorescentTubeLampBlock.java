package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.ITrafficPostLike;
import com.destan.trafficengine.block.data.StreetLampShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FluorescentTubeLampBlock extends StreetLightBlock {
    public static final BooleanProperty CEILING_MOUNTED = BooleanProperty.create("ceiling_mounted");
    // Model pixels: existing housing top 9.3 -> ceiling underside 16.
    private static final double CEILING_RISE = 6.7 / 16;
    private static final Direction[] CONNECTIONS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP};
    private static final VoxelShape[] CEILING_SHAPES = new VoxelShape[4];
    private static final VoxelShape[][] POLE_SHAPES = new VoxelShape[4][32];
    static {
        VoxelShape[] arms = {SHAPE_NORTH, SHAPE_EAST, SHAPE_SOUTH, SHAPE_WEST, SHAPE_UP};
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            int index = facing.get2DDataValue();
            VoxelShape housing = StreetLampShapes.FLUORESCENT.get(facing);
            CEILING_SHAPES[index] = housing.move(0, CEILING_RISE, 0);
            POLE_SHAPES[index][0] = housing;
            for (int mask = 1; mask < 32; mask++) {
                int bit = Integer.numberOfTrailingZeros(mask);
                POLE_SHAPES[index][mask] = Shapes.or(POLE_SHAPES[index][mask & (mask - 1)], arms[bit]);
            }
        }
    }

    public FluorescentTubeLampBlock() {
        super();
        registerDefaultState(defaultBlockState().setValue(CEILING_MOUNTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CEILING_MOUNTED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        BlockPos above = context.getClickedPos().above();
        return mountingState(state, context.getLevel(), context.getClickedPos(), context.getLevel().getBlockState(above));
    }

    /** No render/tick terrain queries. Old pole-mounted states default to false. */
    private BlockState mountingState(BlockState state, BlockGetter level, BlockPos pos, BlockState above) {
        boolean ceiling = !(above.getBlock() instanceof ITrafficPostLike)
                && above.isFaceSturdy(level, pos.above(), Direction.DOWN);
        boolean wasCeiling = state.getValue(CEILING_MOUNTED);
        state = state.setValue(CEILING_MOUNTED, ceiling);
        if (ceiling) {
            for (Direction direction : CONNECTIONS) state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), false);
        } else if (wasCeiling) {
            // Restore ordinary connectors once, when the ceiling is removed.
            for (Direction direction : CONNECTIONS) {
                BlockPos neighborPos = pos.relative(direction);
                BlockState neighbor = level.getBlockState(neighborPos);
                state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), connectsTo(state, neighbor,
                        neighbor.isFaceSturdy(level, neighborPos, direction.getOpposite()), direction.getOpposite()));
            }
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState updated = super.updateShape(state, direction, neighbor, level, pos, neighborPos);
        return direction == Direction.UP ? mountingState(updated, level, pos, neighbor) : updated;
    }

    @Override
    public boolean connectsTo(BlockState state, BlockState target, boolean solid, Direction direction) {
        if (state.getValue(CEILING_MOUNTED)
                || target.is(this) && target.getValue(CEILING_MOUNTED)) return false;
        return super.connectsTo(state, target, solid, direction);
    }

    @Override
    public boolean canConnect(BlockState state, Direction direction) {
        return !state.getValue(CEILING_MOUNTED) && super.canConnect(state, direction);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int facing = state.getValue(FACING).get2DDataValue();
        if (state.getValue(CEILING_MOUNTED)) return CEILING_SHAPES[facing];
        int mask = (state.getValue(NORTH) ? 1 : 0) | (state.getValue(EAST) ? 2 : 0)
                | (state.getValue(SOUTH) ? 4 : 0) | (state.getValue(WEST) ? 8 : 0) | (state.getValue(UP) ? 16 : 0);
        return POLE_SHAPES[facing][mask];
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(CEILING_MOUNTED) ? Shapes.empty() : super.getOcclusionShape(state, level, pos);
    }
}
