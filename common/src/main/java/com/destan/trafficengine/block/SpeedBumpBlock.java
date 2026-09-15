package com.destan.trafficengine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

public class SpeedBumpBlock extends HorizontalDirectionalBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // KUSURSUZ 3 KATMANLI HİTBOX (Modelin kavislerine milimetrik oturur)
    // Kuzey/Güney Yönlü (X ekseninde uzanan)
    private static final VoxelShape SHAPE_NS = Shapes.or(
            Block.box(0.0D, 0.0D, 4.0D, 16.0D, 1.0D, 12.0D), // En Alt Geniş Katman
            Block.box(0.0D, 1.0D, 5.0D, 16.0D, 2.0D, 11.0D), // Orta Katman
            Block.box(0.0D, 2.0D, 6.0D, 16.0D, 3.0D, 10.0D)  // En Üst İnce Katman
    );

    // Doğu/Batı Yönlü (Z ekseninde uzanan)
    private static final VoxelShape SHAPE_EW = Shapes.or(
            Block.box(4.0D, 0.0D, 0.0D, 12.0D, 1.0D, 16.0D), // En Alt Geniş Katman
            Block.box(5.0D, 1.0D, 0.0D, 11.0D, 2.0D, 16.0D), // Orta Katman
            Block.box(6.0D, 2.0D, 0.0D, 10.0D, 3.0D, 16.0D)  // En Üst İnce Katman
    );

    public SpeedBumpBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        Direction dir = pState.getValue(FACING);
        return (dir == Direction.EAST || dir == Direction.WEST) ? SHAPE_EW : SHAPE_NS;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        // Artık tam olarak baktığın yola dik (enlemesine) oturacak!
        return this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(FACING);
    }
}