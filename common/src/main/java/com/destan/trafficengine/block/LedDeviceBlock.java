package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.LedDeviceType;
import com.destan.trafficengine.block.data.IPaintableBlock;
import com.destan.trafficengine.block.data.ITrafficPostLike;
import com.destan.trafficengine.block.entity.LedDeviceBlockEntity;
import com.destan.trafficengine.client.ClientWrapper;
import com.destan.trafficengine.registry.ModBlockEntities;
import com.destan.trafficengine.registry.ModBlocks;
import com.destan.trafficengine.registry.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class LedDeviceBlock extends BaseEntityBlock implements IPaintableBlock, ITrafficPostLike {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POST_MOUNTED = BooleanProperty.create("post_mounted");
    public static final BooleanProperty POST_ABOVE = BooleanProperty.create("post_above");
    public static final BooleanProperty POST_SIDE = BooleanProperty.create("post_side");
    public static final BooleanProperty WALL_MOUNTED = BooleanProperty.create("wall_mounted");
    private static final VoxelShape DISPLAY_N = Block.box(0, 0, 5.5, 16, 16, 7);
    private static final VoxelShape DISPLAY_S = Block.box(0, 0, 9, 16, 16, 10.5);
    private static final VoxelShape DISPLAY_E = Block.box(9, 0, 0, 10.5, 16, 16);
    private static final VoxelShape DISPLAY_W = Block.box(5.5, 0, 0, 7, 16, 16);
    private static final VoxelShape LIGHT_N = Block.box(1, 0, 5.5, 15, 16, 8);
    private static final VoxelShape LIGHT_S = Block.box(1, 0, 8, 15, 16, 10.5);
    private static final VoxelShape LIGHT_E = Block.box(8, 0, 1, 10.5, 16, 15);
    private static final VoxelShape LIGHT_W = Block.box(5.5, 0, 1, 8, 16, 15);
    private static final VoxelShape LIGHT_HANGING_N = Block.box(1, 0, 5.5, 15, 14, 8);
    private static final VoxelShape LIGHT_HANGING_S = Block.box(1, 0, 8, 15, 14, 10.5);
    private static final VoxelShape LIGHT_HANGING_E = Block.box(8, 0, 1, 10.5, 14, 15);
    private static final VoxelShape LIGHT_HANGING_W = Block.box(5.5, 0, 1, 8, 14, 15);
    private static final VoxelShape DISPLAY_WALL_N = Block.box(0, 0, 14.5, 16, 16, 16);
    private static final VoxelShape DISPLAY_WALL_S = Block.box(0, 0, 0, 16, 16, 1.5);
    private static final VoxelShape DISPLAY_WALL_W = Block.box(14.5, 0, 0, 16, 16, 16);
    private static final VoxelShape DISPLAY_WALL_E = Block.box(0, 0, 0, 1.5, 16, 16);
    private static final VoxelShape LIGHT_WALL_N = Block.box(1, 0, 13.5, 15, 16, 16);
    private static final VoxelShape LIGHT_WALL_S = Block.box(1, 0, 0, 15, 16, 2.5);
    private static final VoxelShape LIGHT_WALL_W = Block.box(13.5, 0, 1, 16, 16, 15);
    private static final VoxelShape LIGHT_WALL_E = Block.box(0, 0, 1, 2.5, 16, 15);
    private static final VoxelShape POST_CONNECTOR_BOTTOM = Block.box(7, 0, 7, 9, 16, 9);
    private static final VoxelShape LED_POST_CONNECTOR_BOTTOM = Block.box(7, 0, 7, 9, 7, 9);
    private static final VoxelShape POST_CONNECTOR_TOP = Block.box(7, 0, 7, 9, 16, 9);
    private static final VoxelShape LED_POST_CONNECTOR_TOP = Block.box(7, 7, 7, 9, 16, 9);
    private static final VoxelShape SIDE_CONNECTOR_N = Block.box(7, 7, 7, 9, 9, 16);
    private static final VoxelShape SIDE_CONNECTOR_S = Block.box(7, 7, 0, 9, 9, 9);
    private static final VoxelShape SIDE_CONNECTOR_E = Block.box(0, 7, 7, 9, 9, 9);
    private static final VoxelShape SIDE_CONNECTOR_W = Block.box(7, 7, 7, 16, 9, 9);
    private final LedDeviceType deviceType;

    public LedDeviceBlock(LedDeviceType deviceType) {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0F)
            .sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
        this.deviceType = deviceType;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
            .setValue(POST_MOUNTED, false).setValue(POST_ABOVE, false)
            .setValue(POST_SIDE, false).setValue(WALL_MOUNTED, false));
    }

    public LedDeviceType getDeviceType() { return deviceType; }

    private static Direction displayWidthDirection(Direction facing) {
        return facing.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
    }

    private boolean needsDisplayParts(BlockState state) {
        return deviceType == LedDeviceType.TRAFFIC_DISPLAY;
    }

    private static BlockPos displayPartPos(BlockPos origin, Direction widthDirection, boolean upper, int offset, boolean hanging) {
        BlockPos result = upper ? (hanging ? origin.below() : origin.above()) : origin;
        return result.relative(widthDirection, offset);
    }

    private static boolean isCenteredDisplay(BlockState state) {
        return state.getValue(POST_MOUNTED) && !state.getValue(WALL_MOUNTED);
    }

    private static int[] displayOffsets(BlockState state) {
        return isCenteredDisplay(state) ? new int[]{-1, 0, 1} : new int[]{0, 1};
    }

    public static void ensureDisplayParts(Level level, BlockPos origin, BlockState state) {
        if (!(state.getBlock() instanceof LedDeviceBlock ledDevice)
            || !ledDevice.needsDisplayParts(state)) return;

        Direction facing = state.getValue(FACING);
        Direction widthDirection = displayWidthDirection(facing);
        boolean hanging = state.getValue(POST_ABOVE);
        boolean centered = isCenteredDisplay(state);
        for (boolean upper : new boolean[]{false, true}) {
            for (int offset : displayOffsets(state)) {
                if (!upper && offset == 0) continue;
                BlockPos partPos = displayPartPos(origin, widthDirection, upper, offset, hanging);
                BlockState partState = ModBlocks.LED_DEVICE_PART.get().defaultBlockState()
                    .setValue(LedDevicePartBlock.FACING, facing)
                    .setValue(LedDevicePartBlock.UPPER, upper)
                    .setValue(LedDevicePartBlock.SIDE, offset != 0)
                    .setValue(LedDevicePartBlock.OFFSET, offset + 1)
                    .setValue(LedDevicePartBlock.CENTERED, centered)
                    .setValue(LedDevicePartBlock.HANGING, hanging)
                    .setValue(LedDevicePartBlock.WALL_MOUNTED, state.getValue(WALL_MOUNTED));
                BlockState existing = level.getBlockState(partPos);
                if (existing.isAir() || existing.getBlock() instanceof LedDevicePartBlock) {
                    if (existing != partState) level.setBlock(partPos, partState, Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void removeDisplayParts(LevelAccessor level, BlockPos origin, BlockState state) {
        Direction widthDirection = displayWidthDirection(state.getValue(FACING));
        boolean hanging = state.getValue(POST_ABOVE);
        for (boolean upper : new boolean[]{false, true}) {
            for (int offset : displayOffsets(state)) {
                if (!upper && offset == 0) continue;
                BlockPos partPos = displayPartPos(origin, widthDirection, upper, offset, hanging);
                if (level.getBlockState(partPos).getBlock() instanceof LedDevicePartBlock) {
                    level.destroyBlock(partPos, false);
                }
            }
        }
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        VoxelShape panel;
        if (state.getValue(WALL_MOUNTED)) {
            panel = switch (facing) {
                case NORTH -> deviceType == LedDeviceType.TRAFFIC_DISPLAY ? DISPLAY_WALL_N : LIGHT_WALL_N;
                case SOUTH -> deviceType == LedDeviceType.TRAFFIC_DISPLAY ? DISPLAY_WALL_S : LIGHT_WALL_S;
                case WEST -> deviceType == LedDeviceType.TRAFFIC_DISPLAY ? DISPLAY_WALL_W : LIGHT_WALL_W;
                default -> deviceType == LedDeviceType.TRAFFIC_DISPLAY ? DISPLAY_WALL_E : LIGHT_WALL_E;
            };
        } else if (state.getValue(POST_ABOVE)) {
            panel = switch (deviceType) {
                case TRAFFIC_DISPLAY -> switch (facing) {
                    case NORTH -> DISPLAY_N;
                    case SOUTH -> DISPLAY_S;
                    case EAST -> DISPLAY_E;
                    default -> DISPLAY_W;
                };
                case LED_LIGHT -> switch (facing) {
                    case NORTH -> LIGHT_HANGING_N;
                    case SOUTH -> LIGHT_HANGING_S;
                    case EAST -> LIGHT_HANGING_E;
                    default -> LIGHT_HANGING_W;
                };
            };
        } else if (deviceType == LedDeviceType.LED_LIGHT
            && state.getValue(POST_MOUNTED) && !state.getValue(POST_SIDE)) {
            panel = switch (facing) {
                case NORTH -> LIGHT_HANGING_N;
                case SOUTH -> LIGHT_HANGING_S;
                case EAST -> LIGHT_HANGING_E;
                default -> LIGHT_HANGING_W;
            };
        } else {
            panel = switch (deviceType) {
                case TRAFFIC_DISPLAY -> switch (facing) {
                    case NORTH -> DISPLAY_N;
                    case SOUTH -> DISPLAY_S;
                    case EAST -> DISPLAY_E;
                    default -> DISPLAY_W;
                };
                case LED_LIGHT -> switch (facing) {
                    case NORTH -> LIGHT_N;
                    case SOUTH -> LIGHT_S;
                    case EAST -> LIGHT_E;
                    default -> LIGHT_W;
                };
            };
        }
        if (state.getValue(POST_SIDE)) {
            VoxelShape connector = switch (facing) {
                case NORTH -> SIDE_CONNECTOR_N;
                case SOUTH -> SIDE_CONNECTOR_S;
                case EAST -> SIDE_CONNECTOR_E;
                default -> SIDE_CONNECTOR_W;
            };
            return Shapes.or(panel, connector);
        }
        if (!state.getValue(POST_MOUNTED)) return panel;
        VoxelShape connector = state.getValue(POST_ABOVE)
            ? deviceType == LedDeviceType.LED_LIGHT ? LED_POST_CONNECTOR_TOP : POST_CONNECTOR_TOP
            : deviceType == LedDeviceType.LED_LIGHT ? LED_POST_CONNECTOR_BOTTOM : POST_CONNECTOR_BOTTOM;
        return Shapes.or(panel, connector);
    }
    @Override public void attack(BlockState state, Level level, BlockPos pos, Player player) { onRemoveColor(state, level, pos, player); }
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        BlockState support = context.getLevel().getBlockState(context.getClickedPos().relative(clickedFace.getOpposite()));
        boolean sideOfPost = clickedFace.getAxis().isHorizontal() && support.getBlock() instanceof TrafficSignPostBlock;
        boolean wallMounted = clickedFace.getAxis().isHorizontal() && !sideOfPost;
        boolean postBelow = context.getLevel().getBlockState(context.getClickedPos().below()).getBlock() instanceof TrafficSignPostBlock;
        boolean postAbove = context.getLevel().getBlockState(context.getClickedPos().above()).getBlock() instanceof TrafficSignPostBlock;
        boolean postMounted = !wallMounted && (sideOfPost || postBelow || postAbove);
        BlockState result = defaultBlockState()
            .setValue(FACING, clickedFace.getAxis().isHorizontal() ? clickedFace : context.getHorizontalDirection().getOpposite())
            .setValue(POST_MOUNTED, postMounted)
            .setValue(POST_ABOVE, !wallMounted && postAbove)
            .setValue(POST_SIDE, !wallMounted && sideOfPost)
            .setValue(WALL_MOUNTED, wallMounted);
        if (needsDisplayParts(result)) {
            Direction widthDirection = displayWidthDirection(result.getValue(FACING));
            boolean hanging = result.getValue(POST_ABOVE);
            for (boolean upper : new boolean[]{false, true}) {
                for (int offset : displayOffsets(result)) {
                    if (!upper && offset == 0) continue;
                    if (!context.getLevel().isEmptyBlock(displayPartPos(context.getClickedPos(), widthDirection, upper, offset, hanging))) {
                        return null;
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) ensureDisplayParts(level, pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && deviceType == LedDeviceType.TRAFFIC_DISPLAY) {
            removeDisplayParts(level, pos, state);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!state.getValue(WALL_MOUNTED)) {
            boolean postBelow = level.getBlockState(pos.below()).getBlock() instanceof TrafficSignPostBlock;
            boolean postAbove = level.getBlockState(pos.above()).getBlock() instanceof TrafficSignPostBlock;
            boolean postBehind = level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())).getBlock() instanceof TrafficSignPostBlock;
            state = state.setValue(POST_MOUNTED, postBelow || postAbove || postBehind)
                .setValue(POST_ABOVE, postAbove)
                .setValue(POST_SIDE, postBehind && !postAbove && !postBelow);
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, POST_MOUNTED, POST_ABOVE, POST_SIDE, WALL_MOUNTED); }

    @Override public boolean canAttach(BlockState state, BlockPos pos, Direction direction) { return false; }
    @Override public boolean canConnect(BlockState state, Direction direction) { return true; }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).is(ModItemTags.WRENCHES)) return InteractionResult.PASS;
        if (level.isClientSide && level.getBlockEntity(pos) instanceof LedDeviceBlockEntity) {
            ClientWrapper.showLedDeviceScreen(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LedDeviceBlockEntity(pos, state); }
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.LED_DEVICE_BLOCK_ENTITY.get(), LedDeviceBlockEntity::tick);
    }
}
