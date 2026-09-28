package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.DiagonalVoxelShapes;
import com.destan.trafficengine.block.data.ITrafficPostLike;
import com.destan.trafficengine.block.data.TrafficSignShape;
import com.destan.trafficengine.block.entity.TrafficSignBlockEntity;
import com.destan.trafficengine.data.TrafficSignTextureData;
import com.destan.trafficengine.data.TrafficSignTextureManager;
import com.destan.trafficengine.item.CreativePatternCatalogueItem;
import com.destan.trafficengine.item.PatternCatalogueItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TrafficSignBlock extends BaseEntityBlock implements SimpleWaterloggedBlock, ITrafficPostLike {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty WALL_MOUNTED = BooleanProperty.create("wall_mounted");
    public static final BooleanProperty DIAGONAL = BooleanProperty.create("diagonal");
    public static final BooleanProperty POST_MOUNTED = BooleanProperty.create("post_mounted");
    public static final EnumProperty<TrafficSignShape> SHAPE = EnumProperty.create("shape", TrafficSignShape.class);

    private static final VoxelShape[][] DIAGONAL_SHAPES = new VoxelShape[TrafficSignShape.values().length][4];
    private static final VoxelShape[][] POST_MOUNTED_SHAPES =
        new VoxelShape[TrafficSignShape.values().length][4];

    static {
        for (TrafficSignShape shape : TrafficSignShape.values()) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                DIAGONAL_SHAPES[shape.ordinal()][direction.get2DDataValue()] =
                    DiagonalVoxelShapes.rotateClockwise45(shape.getVoxelShape(direction));

                Direction supportDirection = direction.getOpposite();
                VoxelShape plate = createTiltedPostMountedShape(
                    withoutCenterPole(shape.getVoxelShape(direction)),
                    supportDirection
                );
                POST_MOUNTED_SHAPES[shape.ordinal()][direction.get2DDataValue()] =
                    Shapes.or(plate, createPostMountedConnector(direction)).optimize();
            }
        }
    }

    private static VoxelShape createPostMountedConnector(Direction facing) {
        VoxelShape result = Shapes.empty();
        for (int slice = 0; slice < 4; slice++) {
            double minY = 7.0d + slice * 0.5d;
            double maxY = minY + 0.5d;
            double nearPlate = 19.25d - slice * 0.2d;
            VoxelShape section = switch (facing) {
                case NORTH -> Block.box(7, minY, nearPlate, 9, maxY, 23);
                case SOUTH -> Block.box(7, minY, -7, 9, maxY, 16 - nearPlate);
                case EAST -> Block.box(-7, minY, 7, 16 - nearPlate, maxY, 9);
                default -> Block.box(nearPlate, minY, 7, 23, maxY, 9);
            };
            result = Shapes.or(result, section);
        }
        return result.optimize();
    }

    private static VoxelShape createTiltedPostMountedShape(VoxelShape source, Direction supportDirection) {
        VoxelShape result = Shapes.empty();
        double baseShift = 12.0d / 16.0d;
        double slope = Math.tan(Math.toRadians(22.5d));
        int slices = 8;

        for (AABB box : source.toAabbs()) {
            for (int i = 0; i < slices; i++) {
                double minY = box.minY + (box.maxY - box.minY) * i / slices;
                double maxY = box.minY + (box.maxY - box.minY) * (i + 1) / slices;
                double middleY = (minY + maxY) * 0.5d;
                double offset = baseShift + (0.5d - middleY) * slope;
                double offsetX = supportDirection.getStepX() * offset;
                double offsetZ = supportDirection.getStepZ() * offset;

                result = Shapes.or(result, Shapes.create(
                    box.minX + offsetX,
                    minY,
                    box.minZ + offsetZ,
                    box.maxX + offsetX,
                    maxY,
                    box.maxZ + offsetZ
                ));
            }
        }
        return result.optimize();
    }

    public TrafficSignBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion()
            .sound(SoundType.LANTERN)
        );

        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(WATERLOGGED, false)
            .setValue(WALL_MOUNTED, false)
            .setValue(DIAGONAL, false)
            .setValue(POST_MOUNTED, false)
            .setValue(SHAPE, TrafficSignShape.SQUARE)
        );
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        if (pState.getValue(POST_MOUNTED)) {
            return POST_MOUNTED_SHAPES[pState.getValue(SHAPE).ordinal()]
                [pState.getValue(FACING).get2DDataValue()];
        }
        if (pState.getValue(WALL_MOUNTED)) {
            return getWallMountedShape(pState.getValue(SHAPE), pState.getValue(FACING));
        }
        return getFreestandingShape(
            pState.getValue(SHAPE),
            pState.getValue(FACING),
            pState.getValue(DIAGONAL)
        );
    }

    protected static VoxelShape getFreestandingShape(TrafficSignShape shape, Direction facing, boolean diagonal) {
        return diagonal
            ? DIAGONAL_SHAPES[shape.ordinal()][facing.get2DDataValue()]
            : shape.getVoxelShape(facing);
    }

    private static VoxelShape withoutCenterPole(VoxelShape source) {
        VoxelShape result = Shapes.empty();
        for (AABB box : source.toAabbs()) {
            boolean centerPole = Math.abs(box.minX - 7.0d / 16.0d) < 0.0001d
                && Math.abs(box.maxX - 9.0d / 16.0d) < 0.0001d
                && Math.abs(box.minY) < 0.0001d
                && Math.abs(box.minZ - 7.0d / 16.0d) < 0.0001d
                && Math.abs(box.maxZ - 9.0d / 16.0d) < 0.0001d;
            if (!centerPole) {
                result = Shapes.or(result, Shapes.create(box));
            }
        }
        return result;
    }

    private static VoxelShape getWallMountedShape(TrafficSignShape shape, Direction facing) {
        double minX = 0;
        double maxX = 16;
        double minY = 0;
        double maxY = 16;

        switch (shape) {
            case RECTANGLE -> {
                minX = 2;
                maxX = 14;
            }
            case RECTANGLE_SMALL -> {
                minX = 4;
                maxX = 12;
            }
            case RECTANGLE_HORIZONTAL -> minY = 4;
            case SMALL_UPPER -> {
                minX = 1;
                maxX = 15;
                minY = 8;
            }
            case SMALL_LOWER -> {
                minX = 1;
                maxX = 15;
                maxY = 8;
            }
            default -> {
            }
        }

        return switch (facing) {
            case NORTH -> Block.box(minX, minY, 15.5, maxX, maxY, 16);
            case SOUTH -> Block.box(16 - maxX, minY, 0, 16 - minX, maxY, 0.5);
            case EAST -> Block.box(0, minY, minX, 0.5, maxY, maxX);
            case WEST -> Block.box(15.5, minY, 16 - maxX, 16, maxY, 16 - minX);
            default -> Block.box(0, minY, 15.5, 16, maxY, 16);
        };
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        ItemStack stack = pPlayer.getInventory().getSelected();
        Item item = stack.getItem();

        if (item instanceof PatternCatalogueItem && ((item instanceof CreativePatternCatalogueItem && CreativePatternCatalogueItem.shouldUseCustomPattern(stack)) || PatternCatalogueItem.getSelectedPattern(stack) != null)) {
            if (pLevel.getBlockEntity(pPos) instanceof TrafficSignBlockEntity blockEntity) {
                if (item instanceof CreativePatternCatalogueItem && CreativePatternCatalogueItem.shouldUseCustomPattern(stack)) {
                    blockEntity.setAndResetTexture(CreativePatternCatalogueItem.getCustomImage(stack));
                } else {
                    blockEntity.setAndResetTexture(PatternCatalogueItem.getSelectedPattern(stack));
                }
            }
            
            if (pLevel.isClientSide) {
                pLevel.playSound(pPlayer, pPos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.3F, 1.5f);
            } else {
                TrafficSignTextureData data = TrafficSignTextureManager.load(item instanceof CreativePatternCatalogueItem && CreativePatternCatalogueItem.shouldUseCustomPattern(stack) ? CreativePatternCatalogueItem.getCustomImage(stack).getTextureId() : PatternCatalogueItem.getSelectedPattern(stack).getTextureId());
                pLevel.setBlockAndUpdate(pPos, pState.setValue(SHAPE, data.getShape()));
            }
            
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.FAIL;
    }

    @Override
    public BlockState rotate(BlockState pState, Rotation pRotation) {
        return pState.setValue(FACING, pRotation.rotate(pState.getValue(FACING)));
    }    

    @Override
    public BlockState mirror(BlockState pState, Mirror pMirror) {
        return pState.rotate(pMirror.getRotation(pState.getValue(FACING)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        FluidState fluidstate = pContext.getLevel().getFluidState(pContext.getClickedPos());
        boolean flag = fluidstate.getType() == Fluids.WATER;
        Direction clickedFace = pContext.getClickedFace();
        boolean wallMounted = false;
        boolean postMounted = false;

        // Only the regular traffic sign changes mode automatically. Subclasses
        // such as the double-sided and legacy wall sign keep their own behavior.
        if (this.getClass() == TrafficSignBlock.class && clickedFace.getAxis().isHorizontal()) {
            BlockPos supportPos = pContext.getClickedPos().relative(clickedFace.getOpposite());
            BlockState supportState = pContext.getLevel().getBlockState(supportPos);
            postMounted = supportState.getBlock() instanceof TrafficSignPostBlock;
            wallMounted = postMounted
                || supportState.isFaceSturdy(pContext.getLevel(), supportPos, clickedFace);
            if (postMounted) {
                wallMounted = false;
            }
        }

        float rotation = pContext.getPlayer() == null
            ? pContext.getHorizontalDirection().toYRot()
            : pContext.getPlayer().getYRot();
        int sector = getPlacementSector(rotation);
        // A sign attached to a horizontal post faces straight away from the
        // clicked post face. It must not inherit the player's 45-degree angle,
        // otherwise the plate misses the arm behind it.
        Direction facing = (wallMounted || postMounted) ? clickedFace : getEightWayFacing(sector);
        boolean diagonal = !wallMounted && !postMounted && (sector & 1) == 1;
        return this.defaultBlockState()
            .setValue(FACING, facing)
            .setValue(WATERLOGGED, flag)
            .setValue(WALL_MOUNTED, wallMounted)
            .setValue(DIAGONAL, diagonal)
            .setValue(POST_MOUNTED, postMounted);
    }

    private static int getPlacementSector(float rotation) {
        return Math.floorMod((int)Math.floor((rotation + 22.5f) / 45.0f), 8);
    }

    private static Direction getEightWayFacing(int sector) {
        return switch (sector) {
            case 0, 1 -> Direction.NORTH;
            case 2, 3 -> Direction.EAST;
            case 4, 5 -> Direction.SOUTH;
            default -> Direction.WEST;
        };
    }

    protected static double getFacingX(BlockState state) {
        Direction facing = state.getValue(FACING);
        if (!state.getValue(DIAGONAL)) {
            return facing.getStepX();
        }
        double value = Math.sqrt(0.5d);
        return facing.getStepX() * value - facing.getStepZ() * value;
    }

    protected static double getFacingZ(BlockState state) {
        Direction facing = state.getValue(FACING);
        if (!state.getValue(DIAGONAL)) {
            return facing.getStepZ();
        }
        double value = Math.sqrt(0.5d);
        return facing.getStepX() * value + facing.getStepZ() * value;
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(FACING, WATERLOGGED, WALL_MOUNTED, DIAGONAL, POST_MOUNTED, SHAPE);
    }

    @Override
    public boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
        if (pState.getValue(POST_MOUNTED)) {
            BlockPos supportPos = pPos.relative(pState.getValue(FACING).getOpposite());
            return pLevel.getBlockState(supportPos).getBlock() instanceof TrafficSignPostBlock;
        }
        if (!pState.getValue(WALL_MOUNTED)) {
            return super.canSurvive(pState, pLevel, pPos);
        }

        Direction facing = pState.getValue(FACING);
        BlockPos supportPos = pPos.relative(facing.getOpposite());
        return pLevel.getBlockState(supportPos).isFaceSturdy(pLevel, supportPos, facing);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState pState, Direction pFacing, BlockState pFacingState, LevelAccessor pLevel, BlockPos pCurrentPos, BlockPos pFacingPos) {
        if (pState.getValue(WATERLOGGED)) {
           pLevel.scheduleTick(pCurrentPos, Fluids.WATER, Fluids.WATER.getTickDelay(pLevel));
        }

        boolean supportChanged = pState.getValue(POST_MOUNTED)
            ? pFacing == pState.getValue(FACING).getOpposite()
            : pState.getValue(WALL_MOUNTED) && pFacing == pState.getValue(FACING).getOpposite();
        if (supportChanged
            && !pState.canSurvive(pLevel, pCurrentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
  
        return super.updateShape(pState, pFacing, pFacingState, pLevel, pCurrentPos, pFacingPos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public FluidState getFluidState(BlockState pState) {
        return pState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(pState);
    }

    public boolean isPathfindable(BlockState pState, BlockGetter pLevel, BlockPos pPos, PathComputationType pType) {
        return false;
    }

    @Override
    public boolean canAttach(BlockState pState, BlockPos pPos, Direction pDirection) {
        if (pState.getValue(WALL_MOUNTED) || pState.getValue(POST_MOUNTED)
            || pState.getValue(SHAPE) == TrafficSignShape.SMALL_LOWER) {
            return false;
        }
        return pState.getValue(DIAGONAL)
            ? pDirection.getAxis().isHorizontal()
            : pState.getValue(FACING).getOpposite() == pDirection;
    }

    @Override
    public boolean canConnect(BlockState pState, Direction pDirection) {
        if (pState.getValue(POST_MOUNTED)) {
            // The inclined plate already reaches into the supporting post's
            // block. A separate horizontal arm passes through the sign and is
            // visible from the front, so do not render one for this mounting.
            return false;
        }
        return ITrafficPostLike.super.canConnect(pState, pDirection);
    }

    /* BLOCK ENTITY */
    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new TrafficSignBlockEntity(pPos, pState);
    }
}
