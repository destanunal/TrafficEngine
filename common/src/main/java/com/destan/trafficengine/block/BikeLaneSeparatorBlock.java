package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.RoundedSeparatorHitbox;
import com.destan.trafficengine.block.data.ColorableBlock;
import com.destan.trafficengine.util.ColorValue;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import java.util.List;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

/** Eight-way placement for separators along straight and diagonal bike lanes. */
public class BikeLaneSeparatorBlock extends ColorableBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private final VoxelShape shapeNS;
    private final VoxelShape shapeEW;
    public static final BooleanProperty DIAGONAL = BooleanProperty.create("diagonal");
    private final VoxelShape diagonalNS;
    private final VoxelShape diagonalEW;

    public BikeLaneSeparatorBlock(Properties properties) {
        super(properties);
        shapeNS = createProfile(0);
        shapeEW = createProfile(90);
        diagonalNS = createProfile(45);
        diagonalEW = createProfile(135);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DIAGONAL, false));
    }

    private static VoxelShape createProfile(double rotation) {
        VoxelShape shape = Shapes.empty();
        for (double[] box : RoundedSeparatorHitbox.boxes(rotation)) {
            shape = Shapes.or(shape, Block.box(box[0], box[1], box[2], box[3], box[4], box[5]));
        }
        return shape.optimize();
    }

    /** Keep the curved shell from multiplying break particles. */
    public static VoxelShape destructionShape(BlockState state) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z
            ? Block.box(5, 0, 1, 11, 4.25, 15)
            : Block.box(1, 0, 5, 15, 4.25, 11);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(DIAGONAL)) return state.getValue(FACING).getAxis() == Direction.Axis.Z ? shapeNS : shapeEW;
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? diagonalNS : diagonalEW;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        int sector = Math.floorMod((int) Math.floor(context.getRotation() / 45.0F + 0.5F), 8);
        return defaultBlockState()
            .setValue(FACING, Direction.fromYRot((sector / 2) * 90.0F))
            .setValue(DIAGONAL, (sector & 1) != 0);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        Direction facing = mirror.mirror(state.getValue(FACING));
        if (state.getValue(DIAGONAL)) facing = facing.getCounterClockWise();
        return state.setValue(FACING, facing);
    }

    @Override
    public InteractionResult onSetColor(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.CONSUME;
        return super.onSetColor(context);
    }

    @Override
    public ColorValue getDefaultColor() {
        return ColorValue.WHITE;
    }

    @Override
    public void appendHoverText(ItemStack stack, BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.trafficengine.bike_lane_separator.tooltip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, DIAGONAL);
    }
}
