package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.TrafficLightModel;
import com.destan.trafficengine.block.entity.TrafficLightBlockEntity;
import com.destan.trafficengine.client.ClientWrapper;
import com.destan.trafficengine.registry.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Compact hazard heads with a short mounting stem. No ticking or blink packets. */
public final class WarningLightBlock extends TrafficLightBlock {
    public static final BooleanProperty HORIZONTAL = BooleanProperty.create("horizontal");
    private static final VoxelShape[][][] SHAPES = new VoxelShape[2][2][4];
    static {
        for (int layout = 0; layout < 2; layout++) for (int lamps = 1; lamps <= 2; lamps++) {
            boolean horizontal = layout == 1;
            double minX = lamps == 2 && horizontal ? 2.5 : 5.5;
            double minY = lamps == 2 && !horizontal ? 2.5 : 5.5;
            VoxelShape north = Shapes.or(Block.box(minX, minY, 6.5, 16 - minX, 16 - minY, 9.25), Block.box(7, 0, 7, 9, minY, 9));
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                VoxelShape shape = Shapes.empty();
                for (var box : north.toAabbs()) shape = Shapes.or(shape, switch (facing) {
                    case SOUTH -> Shapes.box(1-box.maxX, box.minY, 1-box.maxZ, 1-box.minX, box.maxY, 1-box.minZ);
                    case EAST -> Shapes.box(1-box.maxZ, box.minY, box.minX, 1-box.minZ, box.maxY, box.maxX);
                    case WEST -> Shapes.box(box.minZ, box.minY, 1-box.maxX, box.maxZ, box.maxY, 1-box.minX);
                    default -> Shapes.create(box);
                });
                SHAPES[layout][lamps-1][facing.get2DDataValue()] = shape.optimize();
            }
        }
    }
    public WarningLightBlock() { registerDefaultState(defaultBlockState().setValue(MODEL, TrafficLightModel.ONE_LIGHT).setValue(HORIZONTAL, false).setValue(DIAGONAL, false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder); builder.add(HORIZONTAL); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace().getAxis().isHorizontal() ? context.getClickedFace() : context.getHorizontalDirection().getOpposite();
        return super.getStateForPlacement(context).setValue(FACING, facing).setValue(DIAGONAL, false)
            .setValue(HORIZONTAL, false);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return null; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(HORIZONTAL) ? 1 : 0][state.getValue(MODEL).getLightsCount() == 1 ? 0 : 1][state.getValue(FACING).get2DDataValue()];
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return getShape(state, level, pos, context); }
    @Override public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }
    @Override public boolean canAttach(BlockState state, BlockPos pos, Direction side) { return side.getAxis().isVertical(); }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).is(ModItemTags.WRENCHES)) return InteractionResult.PASS;
        if (level.isClientSide) ClientWrapper.showWarningLightSettings(level, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TrafficLightBlockEntity lamp) lamp.setWarningPowered(level.hasNeighborSignal(pos));
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, entity, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TrafficLightBlockEntity lamp) lamp.setWarningPowered(level.hasNeighborSignal(pos));
    }
}
