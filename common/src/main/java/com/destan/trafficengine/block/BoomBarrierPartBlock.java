package com.destan.trafficengine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Invisible arm hit/collision cells. They own no entities, renderers or ticking work. */
public class BoomBarrierPartBlock extends Block {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = BoomBarrierBlock.FACING;
    public static final BooleanProperty VERTICAL = BooleanProperty.create("vertical");
    public static final IntegerProperty INDEX = IntegerProperty.create("index", 1, BoomBarrierBlock.LENGTH);
    private static final VoxelShape[][][] SHAPES = new VoxelShape[4][2][2];
    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) for (int terminal=0;terminal<2;terminal++) {
            boolean last=terminal==1;
            VoxelShape horizontal=switch(facing) {
                case NORTH -> Block.box(6.5,13,last?8:0,9.5,15,16);
                case SOUTH -> Block.box(6.5,13,0,9.5,15,last?8:16);
                case WEST -> Block.box(last?8:0,13,6.5,16,15,9.5);
                default -> Block.box(0,13,6.5,last?8:16,15,9.5);
            };
            VoxelShape vertical=facing.getAxis()==Direction.Axis.Z
                ? Block.box(6.5,0,7,9.5,last?14:16,9)
                : Block.box(7,0,6.5,9,last?14:16,9.5);
            SHAPES[facing.get2DDataValue()][0][terminal]=horizontal;
            SHAPES[facing.get2DDataValue()][1][terminal]=vertical;
        }
    }
    public BoomBarrierPartBlock() {
        super(BlockBehaviour.Properties.of().strength(3).sound(SoundType.METAL).noOcclusion().noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(VERTICAL, false)
            .setValue(INDEX, 1).setValue(BoomBarrierBlock.OPEN, false));
    }
    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        int index = state.getValue(INDEX);
        return state.getValue(VERTICAL) ? pos.below(index) : pos.relative(state.getValue(FACING).getOpposite(), index);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(VERTICAL) != state.getValue(BoomBarrierBlock.OPEN)) return Shapes.empty();
        return SHAPES[state.getValue(FACING).get2DDataValue()][state.getValue(VERTICAL)?1:0]
            [state.getValue(INDEX)==BoomBarrierBlock.LENGTH?1:0];
    }
    @Override public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos master = masterPos(pos, state);
            if (level.getBlockState(master).getBlock() instanceof BoomBarrierBlock && level.mayInteract(player,master)
                    && player.mayUseItemAt(master,Direction.UP,player.getMainHandItem()))
                level.destroyBlock(master, !player.isCreative(), player);
        }
        super.playerWillDestroy(level, pos, state, player);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        super.onRemove(state, level, pos, next, moved);
        if (!level.isClientSide && !next.is(this)) {
            BlockPos master = masterPos(pos, state);
            BlockState owner = level.getBlockState(master);
            if (owner.getBlock() instanceof BoomBarrierBlock) level.scheduleTick(master, owner.getBlock(), 1);
        }
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos master = masterPos(pos, state);
        BlockState owner = level.getBlockState(master);
        if (owner.getBlock() instanceof BoomBarrierBlock barrier)
            return barrier.use(owner, level, master, player, hand, hit);
        return InteractionResult.PASS;
    }
    @Override public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));
    }
    @Override public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, VERTICAL, INDEX, BoomBarrierBlock.OPEN);
    }
}
