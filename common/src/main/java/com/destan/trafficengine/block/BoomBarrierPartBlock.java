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

/** Arm hit/collision cells share one renderer on their motor. No extra entities or ticking work. */
public class BoomBarrierPartBlock extends Block {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING=BoomBarrierBlock.FACING;
    public static final BooleanProperty VERTICAL=BooleanProperty.create("vertical");
    public static final IntegerProperty INDEX=IntegerProperty.create("index",1,BoomBarrierBlock.MAX_LENGTH);
    private static final VoxelShape[][][] SHAPES=new VoxelShape[4][2][2];
    static {
        for(Direction facing:Direction.Plane.HORIZONTAL)for(int terminal=0;terminal<2;terminal++) {
            boolean last=terminal==1;
            VoxelShape horizontal=switch(facing) {
                case NORTH -> Block.box(10.75,11.5,last?6.5:0,13.25,13.5,16);
                case SOUTH -> Block.box(2.75,11.5,0,5.25,13.5,last?9.5:16);
                case WEST -> Block.box(last?6.5:0,11.5,2.75,16,13.5,5.25);
                default -> Block.box(0,11.5,10.75,last?9.5:16,13.5,13.25);
            };
            VoxelShape vertical=switch(facing) {
                case NORTH -> Block.box(10.75,0,7,13.25,last?14:16,9);
                case SOUTH -> Block.box(2.75,0,7,5.25,last?14:16,9);
                case WEST -> Block.box(7,0,2.75,9,last?14:16,5.25);
                default -> Block.box(7,0,10.75,9,last?14:16,13.25);
            };
            SHAPES[facing.get2DDataValue()][0][terminal]=horizontal;
            SHAPES[facing.get2DDataValue()][1][terminal]=vertical;
        }
    }
    public BoomBarrierPartBlock() {
        super(BlockBehaviour.Properties.of().strength(3).sound(SoundType.METAL).noOcclusion().dynamicShape().noLootTable()
            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(VERTICAL,false)
            .setValue(INDEX,1).setValue(BoomBarrierBlock.OPEN,false));
    }
    public static BlockPos masterPos(BlockPos pos,BlockState state) {
        return state.getValue(VERTICAL)?pos.below(state.getValue(INDEX))
            :pos.relative(state.getValue(FACING).getOpposite(),state.getValue(INDEX));
    }
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        if(state.getValue(VERTICAL)!=state.getValue(BoomBarrierBlock.OPEN))return Shapes.empty();
        BlockPos master=masterPos(pos,state);
        if(level instanceof Level world && !world.isLoaded(master))return Shapes.empty();
        BlockState owner=level.getBlockState(master);
        if(!(owner.getBlock() instanceof BoomBarrierBlock) || state.getValue(INDEX)>BoomBarrierBlock.length(owner))return Shapes.empty();
        return SHAPES[state.getValue(FACING).get2DDataValue()][state.getValue(VERTICAL)?1:0]
            [state.getValue(INDEX)==BoomBarrierBlock.length(owner)?1:0];
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player) {
        if(!level.isClientSide) {
            BlockPos master=masterPos(pos,state);BlockState owner=level.getBlockState(master);
            if(owner.getBlock() instanceof BoomBarrierBlock)
                BoomBarrierBlock.removeArm(level,master,owner,player,InteractionHand.MAIN_HAND,state.getValue(INDEX)-1,true);
        }
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved) {
        super.onRemove(state,level,pos,next,moved);
        if(!level.isClientSide && !next.is(this)) {
            BlockPos master=masterPos(pos,state);BlockState owner=level.getBlockState(master);
            if(owner.getBlock() instanceof BoomBarrierBlock && state.getValue(INDEX)<=BoomBarrierBlock.length(owner))
                level.scheduleTick(master,owner.getBlock(),1);
        }
    }
    public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        BlockPos master=masterPos(pos,state);BlockState owner=level.getBlockState(master);
        return owner.getBlock() instanceof BoomBarrierBlock barrier?barrier.use(owner,level,master,player,hand,hit):InteractionResult.PASS;
    }
    @Override public BlockState rotate(BlockState state,net.minecraft.world.level.block.Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public BlockState mirror(BlockState state,net.minecraft.world.level.block.Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,VERTICAL,INDEX,BoomBarrierBlock.OPEN);}
    @Override protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.Block> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return com.destan.trafficengine.util.PortingInteractions.forItem(use(state,level,pos,player,hand,hit));
    }
}
