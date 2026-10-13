package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.ConnectedLineStyle;
import com.destan.trafficengine.block.data.RoadType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Static baked markings: connections change only when adjacent road cells change. */
public class ConnectedRoadBlock extends PaintedAsphaltBlock {
    public static final EnumProperty<ConnectedLineStyle> STYLE = EnumProperty.create("line_style", ConnectedLineStyle.class);
    public static final BooleanProperty STROKE = BooleanProperty.create("straight_stroke");
    public ConnectedRoadBlock(RoadType type) {
        super(type, type.getBlock());
        registerDefaultState(defaultBlockState().setValue(STYLE, ConnectedLineStyle.SOLID).setValue(STROKE,false)
            .setValue(PipeBlock.NORTH, false).setValue(PipeBlock.EAST, false)
            .setValue(PipeBlock.SOUTH, false).setValue(PipeBlock.WEST, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STYLE, STROKE, PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST);
    }
    public static BlockState reconnect(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            boolean connected = neighbor.getBlock() instanceof ConnectedRoadBlock
                && neighbor.getValue(STYLE) == state.getValue(STYLE);
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), connected);
        }
        return state;
    }
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return reconnect(state, level, pos);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState result = super.rotate(state, rotation);
        for (Direction direction : Direction.Plane.HORIZONTAL)
            result = result.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(rotation.rotate(direction)),
                state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction)));
        return result;
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState result = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        for (Direction direction : Direction.Plane.HORIZONTAL)
            result = result.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(mirror.mirror(direction)),
                state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction)));
        return result;
    }
}
