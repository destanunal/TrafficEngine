package com.destan.trafficengine.block;

import java.util.ArrayList;
import java.util.List;
import com.destan.trafficengine.block.entity.BoomBarrierBlockEntity;
import com.destan.trafficengine.registry.ModBlocks;
import com.destan.trafficengine.registry.ModItems;
import com.destan.trafficengine.registry.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

/** One motor and lightweight collision cells; the modular arm never needs a ticker. */
public class BoomBarrierBlock extends BaseEntityBlock {
    public static final int LENGTH = 4, MAX_LENGTH = 16;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final IntegerProperty ARM_LENGTH = IntegerProperty.create("length",0,MAX_LENGTH);
    public static final double PIVOT_Y = 12.5 / 16.0, PIVOT_SIDE = 12.0 / 16.0;
    public static final double ARM_OFFSET = 1.5 / 16.0;
    private static final VoxelShape[] BODY = new VoxelShape[4], CLOSED_BODY = new VoxelShape[4];
    static {
        // Clockwise placement keeps the arm to the right; this side puts the joint toward the player.
        VoxelShape north=Shapes.or(Block.box(2,0,3.5,11,1.5,12.5),Block.box(3,1.5,4.5,10,14,11.5),
            Block.box(2.75,14,4.25,10.25,16,11.75),Block.box(9.875,10.25,5.75,10.1,14.75,10.25),
            Block.box(10.1,9,4.5,13.45,16,11.5));
        VoxelShape root=Block.box(10.75,11.5,0,13.25,13.5,6.5);
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            VoxelShape body=rotate(north,facing);
            BODY[facing.get2DDataValue()]=body;
            CLOSED_BODY[facing.get2DDataValue()]=Shapes.or(body,rotate(root,facing)).optimize();
        }
    }
    private static VoxelShape rotate(VoxelShape source,Direction facing) {
        VoxelShape result=Shapes.empty();
        for(AABB b:source.toAabbs()) {
            AABB rotated=switch(facing) {
                case EAST -> new AABB(1-b.maxZ,b.minY,b.minX,1-b.minZ,b.maxY,b.maxX);
                case SOUTH -> new AABB(1-b.maxX,b.minY,1-b.maxZ,1-b.minX,b.maxY,1-b.minZ);
                case WEST -> new AABB(b.minZ,b.minY,1-b.maxX,b.maxZ,b.maxY,1-b.minX);
                default -> b;
            };
            result=Shapes.or(result,Shapes.create(rotated));
        }
        return result.optimize();
    }
    public static int length(BlockState state) {return state.getValue(ARM_LENGTH);}
    public static AABB renderBounds(BlockPos pos,BlockState state) {
        Direction facing=state.getValue(FACING);int length=length(state);
        int dx=facing.getStepX()*length,dz=facing.getStepZ()*length;
        return new AABB(pos.getX()+Math.min(0,dx),pos.getY(),pos.getZ()+Math.min(0,dz),
            pos.getX()+1+Math.max(0,dx),pos.getY()+length+1,pos.getZ()+1+Math.max(0,dz));
    }
    public BoomBarrierBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3)
            .sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
        // Missing length in existing 1.8 world palettes retains the original four-block arm.
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(OPEN,false)
            .setValue(POWERED,false).setValue(ARM_LENGTH,LENGTH));
    }
    public static BlockPos partPos(BlockPos origin,Direction facing,boolean vertical,int index) {
        return vertical?origin.above(index):origin.relative(facing,index);
    }
    private static boolean belongs(BlockState state,BlockPos pos,BlockPos origin) {
        return state.getBlock() instanceof BoomBarrierPartBlock && BoomBarrierPartBlock.masterPos(pos,state).equals(origin);
    }
    private static boolean room(Level level,BlockPos origin,Direction facing,int length) {
        for(int axis=0;axis<2;axis++)for(int i=1;i<=length;i++) {
            BlockPos pos=partPos(origin,facing,axis==1,i);
            if(!level.isInWorldBounds(pos) || !level.isLoaded(pos))return false;
            BlockState state=level.getBlockState(pos);
            if(!state.isAir() && !belongs(state,pos,origin))return false;
        }
        return true;
    }
    private static boolean mayEdit(Level level,BlockPos pos,BlockState state,Player player,ItemStack tool,int length) {
        if(!player.mayBuild() || !level.mayInteract(player,pos) || !player.mayUseItemAt(pos,Direction.UP,tool))return false;
        for(int axis=0;axis<2;axis++)for(int i=1;i<=length;i++) {
            BlockPos part=partPos(pos,state.getValue(FACING),axis==1,i);
            if(!level.isInWorldBounds(part) || !level.isLoaded(part) || !level.mayInteract(player,part)
                || !player.mayUseItemAt(part,Direction.UP,tool))return false;
        }
        return true;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean powered=context.getLevel().hasNeighborSignal(context.getClickedPos());
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getClockWise()).setValue(OPEN,powered)
            .setValue(POWERED,powered).setValue(ARM_LENGTH,0);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);
        if(!level.isClientSide)syncParts(level,pos,state);
    }
    private static void syncParts(Level level,BlockPos origin,BlockState state) {
        for(int axis=0;axis<2;axis++)for(int i=1;i<=length(state);i++) {
            BlockPos pos=partPos(origin,state.getValue(FACING),axis==1,i);
            if(!level.isLoaded(pos))continue;
            BlockState old=level.getBlockState(pos);
            if(old.isAir() || belongs(old,pos,origin)) {
                BlockState part=ModBlocks.BOOM_BARRIER_PART.get().defaultBlockState().setValue(FACING,state.getValue(FACING))
                    .setValue(BoomBarrierPartBlock.VERTICAL,axis==1).setValue(BoomBarrierPartBlock.INDEX,i).setValue(OPEN,state.getValue(OPEN));
                if(old!=part)level.setBlock(pos,part,Block.UPDATE_ALL);
            }
        }
    }
    private static boolean sweepClear(Level level,BlockPos origin,Direction facing,int length) {
        double radius=length+ARM_OFFSET+0.1;
        for(int distance=0;distance<=length;distance++)for(int height=0;height<=length;height++) {
            if(distance==0 && height==0)continue;
            double near=Math.max(0,distance-0.5),low=Math.max(0,height-PIVOT_Y);
            if(near*near+low*low>radius*radius)continue;
            BlockPos pos=origin.relative(facing,distance).above(height);
            if(!level.isInWorldBounds(pos) || !level.isLoaded(pos))return false;
            BlockState state=level.getBlockState(pos);
            if(!state.isAir() && !belongs(state,pos,origin))return false;
        }
        return true;
    }
    private static void resize(Level level,BlockPos pos,BlockState state,int length) {
        BlockState updated=state.setValue(ARM_LENGTH,length);
        level.setBlockAndUpdate(pos,updated);
        for(int axis=0;axis<2;axis++)for(int i=length+1;i<=length(state);i++) {
            BlockPos part=partPos(pos,state.getValue(FACING),axis==1,i);
            if(belongs(level.getBlockState(part),part,pos))level.removeBlock(part,false);
        }
        syncParts(level,pos,updated);
    }
    private static InteractionResult message(Player player,String suffix,Object... args) {
        player.displayClientMessage(Component.translatable("block.trafficengine.boom_barrier."+suffix,args),true);
        return InteractionResult.FAIL;
    }
    public static InteractionResult addArm(Level level,BlockPos pos,BlockState state,Player player,InteractionHand hand) {
        if(state.getValue(OPEN))return message(player,"close_to_resize");
        int length=length(state);if(length==MAX_LENGTH)return message(player,"maximum",MAX_LENGTH);
        if(!mayEdit(level,pos,state,player,player.getItemInHand(hand),length+1))return InteractionResult.FAIL;
        if(!room(level,pos,state.getValue(FACING),length+1) || !sweepClear(level,pos,state.getValue(FACING),length+1))return message(player,"blocked");
        if(!level.isClientSide) {
            resize(level,pos,state,length+1);
            if(!player.isCreative())player.getItemInHand(hand).shrink(1);
            level.playSound(null,pos,SoundEvents.METAL_PLACE,SoundSource.BLOCKS,0.5F,1.15F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    /** Removing any arm segment also removes the unsupported segments beyond it. */
    public static boolean removeArm(Level level,BlockPos pos,BlockState state,Player player,InteractionHand hand,int newLength,boolean drop) {
        if(state.getValue(OPEN) && !drop){message(player,"close_to_resize");return false;}
        if(newLength<0 || newLength>=length(state) || !mayEdit(level,pos,state,player,player.getItemInHand(hand),length(state)))return false;
        if(!level.isClientSide) {
            ItemStack removed=new ItemStack(ModItems.BOOM_BARRIER_ARM.get(),length(state)-newLength);
            resize(level,pos,state,newLength);
            if(!player.isCreative()) {
                if(drop)Block.popResource(level,pos,removed);
                else if(!player.getInventory().add(removed))player.drop(removed,false);
            }
        }
        return true;
    }
    private static boolean change(Level level,BlockPos pos,BlockState state,boolean open) {
        if(open==state.getValue(OPEN))return true;
        if(!sweepClear(level,pos,state.getValue(FACING),length(state)))return false;
        BlockState updated=state.setValue(OPEN,open);level.setBlockAndUpdate(pos,updated);syncParts(level,pos,updated);
        level.playSound(null,pos,open?SoundEvents.IRON_TRAPDOOR_OPEN:SoundEvents.IRON_TRAPDOOR_CLOSE,SoundSource.BLOCKS,0.6F,0.85F);
        return true;
    }
    public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(!player.getItemInHand(hand).is(ModItemTags.WRENCHES))return InteractionResult.PASS;
        if(!mayEdit(level,pos,state,player,player.getItemInHand(hand),length(state)))return InteractionResult.FAIL;
        if(player.isShiftKeyDown())
            return length(state)==0 || removeArm(level,pos,state,player,hand,length(state)-1,false)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.FAIL;
        if(!level.isClientSide && !change(level,pos,state,!state.getValue(OPEN)))return message(player,"blocked");
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,BlockPos from,boolean moved) {
        if(level.isClientSide)return;
        boolean powered=level.hasNeighborSignal(pos);
        if(powered!=state.getValue(POWERED)) {
            BlockState changed=state.setValue(POWERED,powered);level.setBlock(pos,changed,Block.UPDATE_CLIENTS);change(level,pos,changed,powered);
        }
    }
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved) {
        super.onPlace(state,level,pos,old,moved);
        if(!level.isClientSide && !old.is(this))level.scheduleTick(pos,this,1);
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if(room(level,pos,state.getValue(FACING),length(state)))syncParts(level,pos,state);
    }
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder builder) {
        List<ItemStack> drops=new ArrayList<>(super.getDrops(state,builder));
        if(length(state)>0)drops.add(new ItemStack(ModItems.BOOM_BARRIER_ARM.get(),length(state)));
        return drops;
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved) {
        if(!next.is(this) && !level.isClientSide)for(int axis=0;axis<2;axis++)for(int i=1;i<=length(state);i++) {
            BlockPos part=partPos(pos,state.getValue(FACING),axis==1,i);
            if(level.isLoaded(part) && belongs(level.getBlockState(part),part,pos))level.removeBlock(part,false);
        }
        super.onRemove(state,level,pos,next,moved);
    }
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return (state.getValue(OPEN) || length(state)==0?BODY:CLOSED_BODY)[state.getValue(FACING).get2DDataValue()];
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new BoomBarrierBlockEntity(pos,state);}
    @Override public BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,OPEN,POWERED,ARM_LENGTH);}
    @Override protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return com.mojang.serialization.MapCodec.unit(this);
    }
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return com.destan.trafficengine.util.PortingInteractions.forItem(use(state,level,pos,player,hand,hit));
    }
}
