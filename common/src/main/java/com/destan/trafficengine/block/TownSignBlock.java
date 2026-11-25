package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.ITrafficPostLike;
import com.destan.trafficengine.block.data.TownSignVariant;
import com.destan.trafficengine.block.entity.TownSignBlockEntity;
import com.destan.trafficengine.client.ClientWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TownSignBlock extends WritableTrafficSign implements ITrafficPostLike {

    private static final VoxelShape SHAPE_COMMON = Block.box(7, 0, 7, 9, 16, 9);
    private static final VoxelShape SHAPE_SOUTH = Shapes.or(SHAPE_COMMON, Block.box(0, 4, 9, 16, 16, 9.5D));    
    private static final VoxelShape SHAPE_NORTH = Shapes.or(SHAPE_COMMON, Block.box(0, 4, 6.5D, 16, 16, 7));
    private static final VoxelShape SHAPE_EAST = Shapes.or(SHAPE_COMMON, Block.box(9, 4, 0, 9.5D, 16, 16));
    private static final VoxelShape SHAPE_WEST = Shapes.or(SHAPE_COMMON, Block.box(6.5D, 4, 0, 7, 16, 16));

    private static final VoxelShape SHAPE_BOTH_NS = Shapes.or(SHAPE_NORTH, SHAPE_SOUTH);
    private static final VoxelShape SHAPE_BOTH_EW = Shapes.or(SHAPE_EAST, SHAPE_WEST);

    public static final EnumProperty<TownSignVariant> VARIANT = EnumProperty.create("variant", TownSignVariant.class);

    public TownSignBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.0f)
            .requiresCorrectToolForDrops()
            .sound(SoundType.LANTERN)
        );

        registerDefaultState(defaultBlockState().setValue(VARIANT, TownSignVariant.FRONT));
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        switch (pState.getValue(FACING)) {
            case NORTH:
                return pState.getValue(VARIANT) == TownSignVariant.BOTH ? SHAPE_BOTH_NS : SHAPE_NORTH;
            case SOUTH:
                return pState.getValue(VARIANT) == TownSignVariant.BOTH ? SHAPE_BOTH_NS : SHAPE_SOUTH;
            case EAST:
                return pState.getValue(VARIANT) == TownSignVariant.BOTH ? SHAPE_BOTH_EW : SHAPE_EAST;
            case WEST:
                return pState.getValue(VARIANT) == TownSignVariant.BOTH ? SHAPE_BOTH_EW : SHAPE_WEST;
            default:
                return SHAPE_COMMON;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(VARIANT);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!wantsEditor(player, hand)) return InteractionResult.PASS;
        if (!mayEdit(player, pos) || !(level.getBlockEntity(pos) instanceof TownSignBlockEntity sign)) return InteractionResult.FAIL;
        if (level.isClientSide) {
            Direction facing = state.getValue(FACING);
            double facingOffset = (hit.getLocation().x - pos.getX() - .5) * facing.getStepX()
                + (hit.getLocation().z - pos.getZ() - .5) * facing.getStepZ();
            ETownSignSide side = switch (state.getValue(VARIANT)) {
                case FRONT -> ETownSignSide.FRONT;
                case BACK -> ETownSignSide.BACK;
                case BOTH -> facingOffset < 0 ? ETownSignSide.BACK : ETownSignSide.FRONT;
            };
            ClientWrapper.showTownSignScreen(sign, side);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new TownSignBlockEntity(pPos, pState);
    }

    public enum ETownSignSide implements StringRepresentable {
        FRONT("front", 0),
        BACK("back", 1);
        
        private String side;
        private int index;
        
        private ETownSignSide(String variant, int index) {
            this.side = variant;
            this.index = index;
        }
        
        public String getSide() {
            return this.side;
        }
    
        public int getIndex() {
            return this.index;
        }
    
        public String getTranslationKey() {
            return String.format("gui.trafficengine.town_sign.variant.%s", side);
        }
    
        public static ETownSignSide getSideByIndex(int index) {
            for (ETownSignSide shape : ETownSignSide.values()) {
                if (shape.getIndex() == index) {
                    return shape;
                }
            }
            return ETownSignSide.FRONT;
        }
    
        @Override
        public String getSerializedName() {
            return side;
        }
    }

    @Override
    public boolean canAttach(BlockState pState, BlockPos pPos, Direction pDirection) {
        return pState.getValue(VARIANT) != TownSignVariant.BOTH && pDirection == pState.getValue(FACING).getOpposite();
    }
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return com.destan.trafficengine.util.PortingInteractions.forItem(use(state, level, pos, player, hand, hit));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
    }

}
