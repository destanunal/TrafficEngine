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
import net.minecraft.world.level.LevelAccessor;
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
    public static final BooleanProperty HANGING = BooleanProperty.create("hanging");
    public static final BooleanProperty COMPACT = BooleanProperty.create("compact");
    public static final double STEM_HEIGHT = 2;
    private static final VoxelShape[][][][][][] SHAPES = new VoxelShape[2][2][2][2][2][4];
    static {
        for (int layout = 0; layout < 2; layout++) for (int lamps = 1; lamps <= 2; lamps++) {
            double minX = lamps == 2 && layout == 1 ? 2.5 : 5.5;
            double oldMinY = lamps == 2 && layout == 0 ? 2.5 : 5.5;
            for (int hanging = 0; hanging < 2; hanging++) for (int compact = 0; compact < 2; compact++) {
                double mount = compact == 1 ? (hanging == 1 ? 7 : -7) : 0;
                double shift = (hanging == 1 ? oldMinY - STEM_HEIGHT : STEM_HEIGHT - oldMinY) + mount;
                VoxelShape housing = Block.box(minX, oldMinY + shift, 6.4, 16 - minX, 16 - oldMinY + shift, 9.5);
                VoxelShape stem = Block.box(7, (hanging == 1 ? 16 - STEM_HEIGHT : 0) + mount, 7,
                    9, (hanging == 1 ? 16 : STEM_HEIGHT) + mount, 9);
                for (int diagonal = 0; diagonal < 2; diagonal++) {
                    // The head turns; its square carrier stays aligned with ordinary traffic posts.
                    VoxelShape head = diagonal == 0 ? housing
                        : com.destan.trafficengine.block.data.DiagonalVoxelShapes.rotateClockwise45(housing);
                    VoxelShape source = Shapes.or(head, stem);
                    for (Direction facing : Direction.Plane.HORIZONTAL)
                        SHAPES[layout][lamps - 1][diagonal][hanging][compact][facing.get2DDataValue()] = RailingGeometry.rotate(source, facing);
                }
            }
        }
    }
    public WarningLightBlock() {
        registerDefaultState(defaultBlockState().setValue(MODEL, TrafficLightModel.ONE_LIGHT)
            .setValue(HORIZONTAL, false).setValue(DIAGONAL, false).setValue(HANGING, false).setValue(COMPACT, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(HORIZONTAL, HANGING, COMPACT);
    }
    /** Offset in authored model coordinates; the BER converts it to its inverted Y axis. */
    public static double housingOffset(BlockState state) {
        double oldMinY = state.getValue(MODEL).getLightsCount() != 1 && !state.getValue(HORIZONTAL) ? 2.5 : 5.5;
        return (state.getValue(HANGING) ? oldMinY - STEM_HEIGHT : STEM_HEIGHT - oldMinY) + mountingOffset(state);
    }
    /** Horizontal-arm surfaces are seven units inside their supporting cell. */
    public static double mountingOffset(BlockState state) {
        return state.getValue(COMPACT) ? (state.getValue(HANGING) ? 7 : -7) : 0;
    }
    public static boolean isHorizontalMount(BlockState state) {
        // Part 4 has a slightly sloped underside: retain its fitted socket rather than overlapping it with a flat connector.
        return state.getBlock() instanceof TrafficSignPostBlock && state.getValue(TrafficSignPostBlock.AXIS) != Direction.Axis.Y
            || state.getBlock() instanceof CurvedTrafficPoleBlock && state.getValue(CurvedTrafficPoleBlock.PART) == 5;
    }
    public static BlockState mountingState(BlockState state, BlockState above, BlockState below) {
        boolean hanging = CurvedTrafficPoleBlock.isMountingPost(above, Direction.DOWN);
        return state.setValue(HANGING, hanging).setValue(COMPACT, isHorizontalMount(hanging ? above : below));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var level=context.getLevel();var pos=context.getClickedPos();
        return mountingState(super.getStateForPlacement(context).setValue(HORIZONTAL, false), level.getBlockState(pos.above()), level.getBlockState(pos.below()));
    }
    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState updated = super.updateShape(state, side, neighbor, level, pos, neighborPos);
        if (side == Direction.UP) {
            if (CurvedTrafficPoleBlock.isMountingPost(neighbor, Direction.DOWN))
                return updated.setValue(HANGING, true).setValue(COMPACT, isHorizontalMount(neighbor));
            // Only removal of an upper mount needs the opposite supplied support.
            return mountingState(updated, neighbor, level.getBlockState(pos.below()));
        }
        return side == Direction.DOWN && !updated.getValue(HANGING)
            ? updated.setValue(COMPACT, isHorizontalMount(neighbor)) : updated;
    }
    @Override public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        // One scheduled reconciliation on chunk load also upgrades previously placed heads.
        BlockState mounted=mountingState(state,level.getBlockState(pos.above()),level.getBlockState(pos.below()));
        if (mounted!=state) level.setBlockAndUpdate(pos,mounted);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return null; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(HORIZONTAL) ? 1 : 0][state.getValue(MODEL).getLightsCount() == 1 ? 0 : 1][state.getValue(DIAGONAL)?1:0][state.getValue(HANGING)?1:0][state.getValue(COMPACT)?1:0][state.getValue(FACING).get2DDataValue()];
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
