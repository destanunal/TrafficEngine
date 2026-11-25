package com.destan.trafficengine.block;

import org.jetbrains.annotations.Nullable;

import com.destan.trafficengine.block.data.IPaintableBlock;
import com.destan.trafficengine.util.ColorValue;
import com.destan.trafficengine.block.data.ITrafficPostLike;
import com.destan.trafficengine.block.entity.StreetSignBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StreetSignBlock extends WritableTrafficSign implements ITrafficPostLike, IPaintableBlock {
   public static final ColorValue DEFAULT_PANEL_COLOR = ColorValue.fromInt(0xFF194F74);
   public static final float POST_PLATE_WIDTH = 26F / 16, WALL_PLATE_WIDTH = 22F / 16;
   public static final float POST_TEXT_CENTER = 1.5F / 16;

   @Override
   public ColorValue getDefaultColor() {
      return DEFAULT_PANEL_COLOR;
   }

   public static final VoxelShape SHAPE_NORTH = Block.box(7.5, 7.5, -3.5, 8.5, 16.5, 22.5);
   public static final VoxelShape SHAPE_SOUTH = RailingGeometry.rotate(SHAPE_NORTH, Direction.SOUTH);
   public static final VoxelShape SHAPE_WEST = RailingGeometry.rotate(SHAPE_NORTH, Direction.WEST);
   public static final VoxelShape SHAPE_EAST = RailingGeometry.rotate(SHAPE_NORTH, Direction.EAST);

   public static final BooleanProperty WALL_MOUNTED = BooleanProperty.create("wall_mounted");
   private static final VoxelShape[] WALL_SHAPES = new VoxelShape[4];

   static {
      VoxelShape north = Block.box(-3, 7.5, 14.9, 19, 16.5, 16);
      for (Direction facing : Direction.Plane.HORIZONTAL) {
         WALL_SHAPES[facing.get2DDataValue()] = RailingGeometry.rotate(north, facing);
      }
   }

   public StreetSignBlock() {
      super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.SNOW)
            .strength(0.2f)
            .sound(SoundType.BAMBOO));
      registerDefaultState(defaultBlockState().setValue(WALL_MOUNTED, false));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(WALL_MOUNTED);
   }

   @Override
   public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
      if (pState.getValue(WALL_MOUNTED)) return WALL_SHAPES[pState.getValue(FACING).get2DDataValue()];
      switch (pState.getValue(FACING)) {
         default:
         case NORTH:
            return SHAPE_NORTH;
         case EAST:
            return SHAPE_EAST;
         case SOUTH:
            return SHAPE_SOUTH;
         case WEST:
            return SHAPE_WEST;
      }
   }

   @Override
   public VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
      return getShape(pState, pLevel, pPos, pContext);
   }

   @Override
   public void attack(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer) {
      onRemoveColor(pState, pLevel, pPos, pPlayer);
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
      return new StreetSignBlockEntity(pPos, pState);
   }

   @Override
   public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      Direction facing = state.getValue(FACING);
      BlockPos supportPos = pos.relative(facing.getOpposite());
      BlockState support = level.getBlockState(supportPos);
      if (state.getValue(WALL_MOUNTED)) return supportsWallPlate(support, level, supportPos, facing);
      return support.getBlock() instanceof ITrafficPostLike post && post.canAttach(support, supportPos, facing);
   }

   private static boolean supportsWallPlate(BlockState support, BlockGetter level, BlockPos pos, Direction face) {
      return !(support.getBlock() instanceof ITrafficPostLike) && support.isFaceSturdy(level, pos, face, SupportType.CENTER);
   }

   public BlockState updateShape(BlockState pState, Direction pFacing, BlockState pFacingState, LevelAccessor pLevel, BlockPos pCurrentPos, BlockPos pFacingPos) {
      if (pFacing.getOpposite() == pState.getValue(FACING) && !pState.canSurvive(pLevel, pCurrentPos)) {
         return Blocks.AIR.defaultBlockState();
      } else {
         if (pState.getValue(WATERLOGGED)) {
            pLevel.scheduleTick(pCurrentPos, Fluids.WATER, Fluids.WATER.getTickDelay(pLevel));
         }

         return super.updateShape(pState, pFacing, pFacingState, pLevel, pCurrentPos, pFacingPos);
      }
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext pContext) {
      if (!pContext.replacingClickedOnBlock()) {
         BlockState blockstate = pContext.getLevel().getBlockState(pContext.getClickedPos().relative(pContext.getClickedFace().getOpposite()));
         if (blockstate.is(this) && blockstate.getValue(FACING) == pContext.getClickedFace()) {
            return null;
         }
      }

      LevelReader level = pContext.getLevel();
      BlockPos pos = pContext.getClickedPos();
      boolean waterlogged = level.getFluidState(pos).getType() == Fluids.WATER;
      Direction clicked = pContext.getClickedFace();
      if (!pContext.replacingClickedOnBlock() && clicked.getAxis().isHorizontal()) {
         BlockState attached = attachedState(level, pos, clicked, waterlogged);
         if (attached != null) return attached;
      }
      for (Direction direction : pContext.getNearestLookingDirections()) {
         if (!direction.getAxis().isHorizontal()) continue;
         BlockState attached = attachedState(level, pos, direction.getOpposite(), waterlogged);
         if (attached != null) return attached;
      }
      return null;
   }

   private BlockState attachedState(LevelReader level, BlockPos pos, Direction facing, boolean waterlogged) {
      BlockPos supportPos = pos.relative(facing.getOpposite());
      BlockState support = level.getBlockState(supportPos);
      boolean wall = supportsWallPlate(support, level, supportPos, facing);
      if (!wall && !(support.getBlock() instanceof ITrafficPostLike post && post.canAttach(support, supportPos, facing))) return null;
      return defaultBlockState().setValue(FACING, facing).setValue(WALL_MOUNTED, wall).setValue(WATERLOGGED, waterlogged);
   }

   @Override
   public boolean canAttach(BlockState pState, BlockPos pPos, Direction pDirection) {
      return false;
   }

   @Override
   public boolean canConnect(BlockState pState, Direction pDirection) {
      return false;
   }
}
