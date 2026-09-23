package com.destan.trafficengine.block;

import java.util.HashMap;
import java.util.Map;

import com.destan.trafficengine.block.data.TrafficLightModel;
import com.destan.trafficengine.block.entity.TrafficLightBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HorizontalTrafficLightBlock extends TrafficLightBlock {

    public static final VoxelShape POLE_X = Block.box(0, 7, 7, 16, 9, 9);
    public static final VoxelShape POLE_Z = Block.box(7, 7, 0, 9, 9, 16);

    private static final Map<TrafficLightModel, Map<Direction, VoxelShape>> SHAPES = new HashMap<>();

    private static VoxelShape createSafeBox(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Shapes.box(x1 / 16.0, y1 / 16.0, z1 / 16.0, x2 / 16.0, y2 / 16.0, z2 / 16.0);
    }

    static {
        for (TrafficLightModel model : TrafficLightModel.values()) {
            Map<Direction, VoxelShape> dirShapes = new HashMap<>();

            int lights = model.getLightsCount();
            double minX = 0.0;
            double maxX = 16.0;

            if (lights == 1) {
                minX = 5.0;
                maxX = 11.0;
            } else if (lights == 2) {
                minX = 2.5;
                maxX = 13.5;
            } else if (lights == 3) {
                minX = 0.0;
                maxX = 16.0;
            } else if (lights >= 4) {
                minX = -2.5;
                maxX = 18.5;
            }

            dirShapes.put(Direction.NORTH, Shapes.or(POLE_X, createSafeBox(minX, 4.0, 0.5, maxX, 12.0, 7.0)));
            dirShapes.put(Direction.SOUTH, Shapes.or(POLE_X, createSafeBox(16.0 - maxX, 4.0, 9.0, 16.0 - minX, 12.0, 15.5)));
            dirShapes.put(Direction.WEST,  Shapes.or(POLE_Z, createSafeBox(0.5, 4.0, minX, 7.0, 12.0, maxX)));
            dirShapes.put(Direction.EAST,  Shapes.or(POLE_Z, createSafeBox(9.0, 4.0, 16.0 - maxX, 15.5, 12.0, 16.0 - minX)));

            SHAPES.put(model, dirShapes);
        }
    }

    public HorizontalTrafficLightBlock() {
        super();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState()
            .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER)
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(DIAGONAL, false);
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        TrafficLightModel model = pState.getValue(MODEL);
        Direction facing = pState.getValue(FACING);
        Map<Direction, VoxelShape> dirMap = SHAPES.get(model);
        if (dirMap != null && dirMap.containsKey(facing)) {
            return dirMap.get(facing);
        }
        return POLE_X;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return getShape(pState, pLevel, pPos, pContext);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
        Direction facing = pState.getValue(FACING);
        return (facing.getAxis() == Direction.Axis.X) ? POLE_Z : POLE_X;
    }

    @Override
    public boolean canConnect(BlockState pState, Direction pDirection) {
        Direction facing = pState.getValue(FACING);

        // The built-in support bar always runs horizontally and perpendicular
        // to the direction in which the traffic-light lenses face.
        return pDirection.getAxis().isHorizontal()
            && pDirection.getAxis() != facing.getAxis();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new TrafficLightBlockEntity(pPos, pState);
    }
}
