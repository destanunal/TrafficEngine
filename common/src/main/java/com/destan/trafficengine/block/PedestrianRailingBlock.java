package com.destan.trafficengine.block;

import com.destan.trafficengine.registry.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/** Vanilla's cached fence connectivity and collision, with slim steel rail models. */
public final class PedestrianRailingBlock extends FenceBlock {
    public static final BooleanProperty UP = net.minecraft.world.level.block.state.properties.BlockStateProperties.UP;
    public static final BooleanProperty DOWN = net.minecraft.world.level.block.state.properties.BlockStateProperties.DOWN;
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty SINK=net.minecraft.world.level.block.state.properties.IntegerProperty.create("sink",0,7);
    private static final net.minecraft.world.phys.shapes.VoxelShape[][] OUTLINES=new net.minecraft.world.phys.shapes.VoxelShape[8][16],COLLISIONS=new net.minecraft.world.phys.shapes.VoxelShape[8][16];
    public static final BooleanProperty GRAY = BooleanProperty.create("gray");
    public PedestrianRailingBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(2F).requiresCorrectToolForDrops()
            .sound(SoundType.METAL).noOcclusion());
        registerDefaultState(defaultBlockState().setValue(SINK,0).setValue(GRAY, true).setValue(UP,false).setValue(DOWN,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(GRAY,UP,DOWN,SINK);
    }
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState state=super.getStateForPlacement(context);
        return state==null?null:verticalConnections(state,context.getLevel(),context.getClickedPos());
    }
    private static BlockState verticalConnections(BlockState state,net.minecraft.world.level.BlockGetter world,BlockPos pos) {
        return state.setValue(SINK,RailingGeometry.sinkFor(world.getBlockState(pos.below()))).setValue(UP,world.getBlockState(pos.above()).getBlock() instanceof PedestrianRailingBlock)
            .setValue(DOWN,world.getBlockState(pos.below()).getBlock() instanceof PedestrianRailingBlock);
    }
    @Override public BlockState updateShape(BlockState state,net.minecraft.core.Direction side,BlockState neighbour,
            net.minecraft.world.level.LevelAccessor world,BlockPos pos,BlockPos other) {
        BlockState updated=super.updateShape(state,side,neighbour,world,pos,other);
        if(side==net.minecraft.core.Direction.UP)return updated.setValue(UP,neighbour.getBlock() instanceof PedestrianRailingBlock);
        if(side==net.minecraft.core.Direction.DOWN)return updated.setValue(DOWN,neighbour.getBlock() instanceof PedestrianRailingBlock).setValue(SINK,RailingGeometry.sinkFor(neighbour));
        return updated;
    }
    @Override public boolean connectsTo(BlockState other,boolean sturdy,net.minecraft.core.Direction side){
        if(other.getBlock() instanceof SlopedPedestrianRailingBlock)return side==other.getValue(SlopedPedestrianRailingBlock.FACING).getOpposite();
        return super.connectsTo(other,sturdy,side);
    }
    private static int connectionIndex(BlockState state){return (state.getValue(NORTH)?1:0)|(state.getValue(EAST)?2:0)|(state.getValue(SOUTH)?4:0)|(state.getValue(WEST)?8:0);}
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter world,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){
        int sink=state.getValue(SINK);if(sink==0)return super.getShape(state,world,pos,context);int index=connectionIndex(state);
        var found=OUTLINES[sink][index];if(found==null)OUTLINES[sink][index]=found=super.getShape(state,world,pos,context).move(0,-sink/8D,0);return found;
    }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state,net.minecraft.world.level.BlockGetter world,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){
        int sink=state.getValue(SINK);if(sink==0)return super.getCollisionShape(state,world,pos,context);int index=connectionIndex(state);
        var found=COLLISIONS[sink][index];if(found==null)COLLISIONS[sink][index]=found=super.getCollisionShape(state,world,pos,context).move(0,-sink/8D,0);return found;
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).is(ModItemTags.WRENCHES)) return InteractionResult.PASS;
        if (!level.isClientSide) level.setBlockAndUpdate(pos, verticalConnections(state.cycle(GRAY),level,pos));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    
    
}
