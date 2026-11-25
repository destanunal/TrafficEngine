package com.destan.trafficengine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TrafficBarrelBlock extends TrafficConeBlock {

    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(3, 0, 3, 13, 1.12, 13),
        barrelBody(1.12, 13.85),
        Block.box(5.7, 13.85, 7.57, 10.3, 15.83, 8.43)
    ).optimize();

    private static final VoxelShape COLLISION = barrelBody(0, 24);

    private static VoxelShape barrelBody(double bottom, double top) {
        return Shapes.or(
            Block.box(3.9, bottom, 6.1, 12.1, top, 9.9),
            Block.box(4.35, bottom, 4.35, 11.65, top, 11.65),
            Block.box(6.1, bottom, 3.9, 9.9, top, 12.1)
        ).optimize();
    }
    
    public TrafficBarrelBlock() {
        super();
    }    

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE;
    }
    
    @Override
    public VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return COLLISION;
    }
}
