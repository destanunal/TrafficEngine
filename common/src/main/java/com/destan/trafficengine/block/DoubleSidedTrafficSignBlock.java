package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.TrafficSignShape;
import com.destan.trafficengine.block.entity.TrafficSignBlockEntity;
import com.destan.trafficengine.data.NamedTrafficSignTextureReference;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Traffic sign with a plate on both sides of the center post.
 *
 * The front (the side FACING points to) uses the inherited SHAPE property,
 * the back uses BACK_SHAPE, so both sides can have a different plate shape
 * (for example a round sign on the front and a square sign on the back).
 *
 * Right click with a Pattern Catalogue on the side you want to change.
 */
public class DoubleSidedTrafficSignBlock extends TrafficSignBlock {

    public static final EnumProperty<TrafficSignShape> BACK_SHAPE = EnumProperty.create("back_shape", TrafficSignShape.class);

    // [front shape][back shape][2D direction] -> front plate + back plate + post
    private static final VoxelShape[][][] SHAPE_CACHE = new VoxelShape[TrafficSignShape.values().length][TrafficSignShape.values().length][4];

    public DoubleSidedTrafficSignBlock() {
        super();
        this.registerDefaultState(this.defaultBlockState().setValue(BACK_SHAPE, TrafficSignShape.SQUARE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(BACK_SHAPE);
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        TrafficSignShape frontShape = pState.getValue(SHAPE);
        TrafficSignShape backShape = pState.getValue(BACK_SHAPE);
        Direction facing = pState.getValue(FACING);
        int dir = facing.get2DDataValue();

        VoxelShape cached = SHAPE_CACHE[frontShape.ordinal()][backShape.ordinal()][dir];
        if (cached == null) {
            cached = Shapes.or(frontShape.getVoxelShape(facing), backShape.getVoxelShape(facing.getOpposite()));
            SHAPE_CACHE[frontShape.ordinal()][backShape.ordinal()][dir] = cached;
        }
        return cached;
    }

    /** Both plates can be attached to, so front and back count as "back of a plate". Delete this override to keep the single sided behaviour. */
    @Override
    public boolean canAttach(BlockState pState, BlockPos pPos, Direction pDirection) {
        return pState.getValue(SHAPE) != TrafficSignShape.SMALL_LOWER
                && pState.getValue(BACK_SHAPE) != TrafficSignShape.SMALL_LOWER
                && pDirection.getAxis() == pState.getValue(FACING).getAxis();
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        ItemStack stack = pPlayer.getInventory().getSelected();
        Item item = stack.getItem();

        boolean customPattern = item instanceof CreativePatternCatalogueItem && CreativePatternCatalogueItem.shouldUseCustomPattern(stack);

        if (!(item instanceof PatternCatalogueItem) || !(customPattern || PatternCatalogueItem.getSelectedPattern(stack) != null)) {
            return InteractionResult.FAIL;
        }

        if (!(pLevel.getBlockEntity(pPos) instanceof TrafficSignBlockEntity blockEntity)) {
            return InteractionResult.FAIL;
        }

        NamedTrafficSignTextureReference reference = customPattern
                ? CreativePatternCatalogueItem.getCustomImage(stack)
                : PatternCatalogueItem.getSelectedPattern(stack);

        // Front = the side FACING points to. Decide the side from where the block was hit.
        Direction facing = pState.getValue(FACING);
        Vec3 offset = pHit.getLocation().subtract(Vec3.atCenterOf(pPos));
        boolean back = offset.x * facing.getStepX() + offset.z * facing.getStepZ() < 0;

        if (back) {
            blockEntity.setAndResetBackTexture(reference);
        } else {
            blockEntity.setAndResetTexture(reference);
        }

        if (pLevel.isClientSide) {
            pLevel.playSound(pPlayer, pPos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.3F, 1.5f);
            return InteractionResult.SUCCESS;
        }

        // Each side keeps its own shape.
        TrafficSignShape newShape = shapeOf(reference.getTextureId());
        if (newShape == null) {
            newShape = pState.getValue(back ? BACK_SHAPE : SHAPE);
        }
        pLevel.setBlockAndUpdate(pPos, pState.setValue(back ? BACK_SHAPE : SHAPE, newShape));

        return InteractionResult.SUCCESS;
    }

    /** Shape of the given texture, or null if unknown. */
    private static TrafficSignShape shapeOf(String textureId) {
        if (textureId == null || textureId.equals("empty")) {
            return null;
        }
        TrafficSignTextureData data = TrafficSignTextureManager.load(textureId);
        return data == null ? null : data.getShape();
    }
}