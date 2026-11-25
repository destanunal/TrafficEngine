package com.destan.trafficengine.block;

import com.destan.trafficengine.block.data.ITrafficPostLike;
import com.destan.trafficengine.util.LoadedRoadData;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.shapes.*;

/** One static mast arm, split into occupied cells for ordinary chunk rendering and picking.
 * No block entity, custom renderer, world scan or recurring scheduled tick. */
public final class CurvedTrafficPoleBlock extends Block implements SimpleWaterloggedBlock, ITrafficPostLike {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART=IntegerProperty.create("part",0,5);
    public static final BooleanProperty LAMP_SUPPORT=BooleanProperty.create("lamp_support");
    public static final BooleanProperty TOP_SUPPORT=BooleanProperty.create("top_support");
    public static final IntegerProperty SIGN_MOUNTS = IntegerProperty.create("sign_mounts", 0, 15);
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    private static final int[][] OFFSETS={{0,0},{1,0},{0,-1},{1,-1},{1,-2},{1,-3}}; // {height, northward Z}
    private static final double[][][] BOXES={{{7,0.0,6.4286096,9,4.9341457,9.0},{7,4.4301897,4.7294774,9,9.6610565,8.3640755},{7,8.7077482,1.952676,9,13.992044,6.4876592},{7,12.6753271,0,9,16,3.4580852},{7,15.239602,0,9,16,0.6494574}},{{7,0,0,9,0.760398,3.4580852},{7,0,0,9,1.7652172,0.6494574}},{{7,12.6753271,15.3505426,9,16,16},{7,15.239602,14.1912852,9,16,16}},{{7,0,15.3505426,9,0.760398,16},{7,0,14.1912852,9,1.7652172,16},{7,0.1759083,9.582974,9,4.8459464,15.4054092},{7,3.064595,4.3044434,9,7.1265156,10.4922524},{7,5.2197019,0,9,8.5274977,4.9078184}},{{7,5.2197019,14.5623983,9,8.5274977,16},{7,6.5501958,8.5835921,9,9.0,14.8628602},{7,7.0,0,9,9.0,8.5835921}},{{7,7,9,9,9,16},{7,7,7,9,9,9},{7,7,0,9,9,7},{7,0,7,9,7,9}}};
    private static final VoxelShape BREAK_SHAPE=box(7,7,7,9,9,9);
    public static VoxelShape destructionShape(){return BREAK_SHAPE;}
    private static final VoxelShape[][][] MOUNTED_SHAPES=new VoxelShape[OFFSETS.length][4][4];
    private static final VoxelShape[][] SHAPES=new VoxelShape[OFFSETS.length][4];
    private static final VoxelShape[][][] SIGN_SHAPES = new VoxelShape[2][16][4];
    // In pole-local order: north, east, south, west.
    private static final VoxelShape[][] SIGN_BRACES = {
        {
            Shapes.or(box(7.2866, 7.175787, 4.624995, 8.7134, 8.002244, 5.082738), box(7.51, 7.362244, 3.9, 8.49, 8.28, 4.624995)),
            Shapes.or(box(9.23, 7.36644, 5.268807, 9.719203, 8.63356, 6.896341), box(9.719203, 7.68, 5.916341, 12.1, 8.32, 8.43)),
            Shapes.or(box(7.2866, 8.076638, 7.075018, 8.7134, 8.824213, 7.83778), box(7.51, 7.72, 7.83778, 8.49, 8.716638, 12.1)),
            Shapes.or(box(6.242128, 7.36644, 5.268807, 6.77, 8.63356, 6.922225), box(3.9, 7.68, 5.942225, 6.242128, 8.32, 8.43))
        },
        {
            Shapes.empty(),
            Shapes.or(box(9.23, 4.080521, 7.407769, 9.620643, 5.54458, 8.592231), box(9.620643, 4.90458, 7.51, 12.1, 8.28, 8.49)),
            Shapes.empty(),
            Shapes.or(box(6.399217, 4.080521, 7.407769, 6.77, 5.522349, 8.592231), box(3.9, 4.882349, 7.51, 6.399217, 8.28, 8.49))
        }
    };
    private static final VoxelShape[] SIGN_BANDS = {
        box(6.77, 7.099947, 4.75856, 9.23, 8.900053, 7.399196),
        box(6.77, 3.467109, 7.090643, 9.23, 6.10747, 8.909357)
    };
    private static final int SIGN_MOUNTS_PER_TICK = 64;
    private static final ConcurrentHashMap<ServerLevel, Queue<BlockPos>> PENDING_SIGN_MOUNTS = new ConcurrentHashMap<>();
    // Only synchronous placement/removal needs this guard; never used by rendering/shape queries.
    private static final ThreadLocal<Boolean> EDITING=ThreadLocal.withInitial(()->false);
    static {
        TickEvent.SERVER_LEVEL_POST.register(CurvedTrafficPoleBlock::processSignMounts);
        LifecycleEvent.SERVER_LEVEL_UNLOAD.register(PENDING_SIGN_MOUNTS::remove);
        for(int part=0;part<BOXES.length;part++) {
            VoxelShape north=Shapes.empty();
            // The final box is the optional downward socket, not part of the bare arm.
            int boxes=BOXES[part].length-(part==5?1:0);
            for(int i=0;i<boxes;i++) {
                double[] b=BOXES[part][i];north=Shapes.or(north,box(b[0],b[1],b[2],b[3],b[4],b[5]));
            }
            north=north.optimize();
            for(Direction facing:Direction.Plane.HORIZONTAL)SHAPES[part][facing.get2DDataValue()]=RailingGeometry.rotate(north,facing);
            if (hasSignSaddle(part)) {
                int section = part == 0 ? 0 : 1;
                VoxelShape band = Shapes.or(north, SIGN_BANDS[section]);
                for (int mask = 1; mask < 16; mask++) {
                    if (part == 3 && (mask & 5) != 0) continue;
                    VoxelShape mounted = band;
                    for (int side = 0; side < 4; side++)
                        if ((mask & (1 << side)) != 0) mounted = Shapes.or(mounted, SIGN_BRACES[section][side]);
                    mounted = mounted.optimize();
                    for (Direction facing : Direction.Plane.HORIZONTAL)
                        SIGN_SHAPES[section][mask][facing.get2DDataValue()] = RailingGeometry.rotate(mounted, facing);
                }
            }
            if(part==4||part==5) {
                for(int sockets=0;sockets<4;sockets++) {
                    VoxelShape mounted=north;
                    if((sockets&1)!=0)mounted=Shapes.or(mounted,box(7,0,7,9,7,9));
                    if((sockets&2)!=0)mounted=Shapes.or(mounted,box(7,8.96,7,9,16,9));
                    mounted=mounted.optimize();
                    for(Direction facing:Direction.Plane.HORIZONTAL)MOUNTED_SHAPES[part][sockets][facing.get2DDataValue()]=RailingGeometry.rotate(mounted,facing);
                }
            }
        }
    }
    public CurvedTrafficPoleBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(2.5F).requiresCorrectToolForDrops()
            .sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PART,0).setValue(WATERLOGGED,false).setValue(LAMP_SUPPORT,false).setValue(TOP_SUPPORT,false).setValue(SIGN_MOUNTS,0));
    }
    public static int partCount(){return OFFSETS.length;}
    public static BlockPos partPos(BlockPos origin,Direction facing,int part) {
        return origin.relative(facing,-OFFSETS[part][1]).above(OFFSETS[part][0]);
    }
    public static BlockPos masterPos(BlockPos pos,BlockState state) {
        int[] delta=OFFSETS[state.getValue(PART)];
        return pos.relative(state.getValue(FACING),delta[1]).below(delta[0]);
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context) {
        int part=state.getValue(PART), facing=state.getValue(FACING).get2DDataValue();
        if (hasSignSaddle(part)) {
            int mask = state.getValue(SIGN_MOUNTS);
            if (part == 3) mask &= 10;
            if (mask != 0) return SIGN_SHAPES[part == 0 ? 0 : 1][mask][facing];
        }
        return (part==4||part==5)?MOUNTED_SHAPES[part][(state.getValue(LAMP_SUPPORT)?1:0)|(state.getValue(TOP_SUPPORT)?2:0)][facing]:SHAPES[part][facing];
    }
    @Override public VoxelShape getOcclusionShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.empty();}
    @Override public FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,PART,WATERLOGGED,LAMP_SUPPORT,TOP_SUPPORT,SIGN_MOUNTS);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing=context.getHorizontalDirection();
        return canPlaceAssembly(context,facing)?defaultBlockState().setValue(FACING,facing):null;
    }
    public boolean canPlaceAssembly(BlockPlaceContext context,Direction facing) {
        Level world=context.getLevel();BlockPos origin=context.getClickedPos();Player player=context.getPlayer();
        for(int part=0;part<OFFSETS.length;part++) {
            BlockPos pos=partPos(origin,facing,part);
            if(!world.isLoaded(pos)||!world.isInWorldBounds(pos)||!world.getWorldBorder().isWithinBounds(pos))return false;
            if(player!=null&&(!player.mayBuild()||!world.mayInteract(player,pos)))return false;
            BlockState existing=world.getBlockState(pos);
            if(existing.hasBlockEntity()||!existing.canBeReplaced(BlockPlaceContext.at(context,pos,context.getClickedFace())))return false;
            BlockState state=stateFor(world,pos,facing,part);
            if(!world.isUnobstructed(state,pos,player==null?CollisionContext.empty():CollisionContext.of(player)))return false;
        }
        return true;
    }
    private BlockState stateFor(Level world,BlockPos pos,Direction facing,int part) {
        return defaultBlockState().setValue(FACING,facing).setValue(PART,part)
            .setValue(WATERLOGGED,world.getFluidState(pos).getType()==Fluids.WATER)
            .setValue(LAMP_SUPPORT,(part==4||part==5)&&needsLowerSocket(world.getBlockState(pos.below())))
            .setValue(TOP_SUPPORT,(part==4||part==5)&&needsUpperSocket(world.getBlockState(pos.above())));
    }
    /** Preflight all cells before writing; restore their exact old states on rejection. */
    public boolean placeAssembly(BlockPlaceContext context,BlockState root) {
        Direction facing=root.getValue(FACING);
        if(!canPlaceAssembly(context,facing))return false;
        Level world=context.getLevel();BlockPos origin=context.getClickedPos();
        BlockState[] old=new BlockState[OFFSETS.length];int placed=0;boolean success=false;
        EDITING.set(true);
        try {
            for(int part=0;part<OFFSETS.length;part++) {
                BlockPos pos=partPos(origin,facing,part);old[part]=world.getBlockState(pos);
                BlockState state=stateFor(world,pos,facing,part);
                if(!world.setBlock(pos,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE))return false;
                placed++;
            }
            success=true;
        } finally {
            if(!success)for(int part=placed-1;part>=0;part--)world.setBlock(partPos(origin,facing,part),old[part],Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            EDITING.remove();
        }
        for(int part=0;part<OFFSETS.length;part++) {
            BlockPos pos=partPos(origin,facing,part);
            world.getBlockState(pos).updateNeighbourShapes(world,pos,Block.UPDATE_CLIENTS);
            world.updateNeighborsAt(pos,this);
        }
        return true;
    }
    private boolean matches(BlockState state,Direction facing,int part) {
        return state.is(this)&&state.getValue(FACING)==facing&&state.getValue(PART)==part;
    }
    @Override public void onRemove(BlockState state,Level world,BlockPos pos,BlockState next,boolean moved) {
        super.onRemove(state,world,pos,next,moved);
        if(world.isClientSide||next.is(this)||EDITING.get()||com.destan.trafficengine.blueprint.BlueprintPlacement.writing())return;
        BlockPos master=masterPos(pos,state);Direction facing=state.getValue(FACING);
        if(state.getValue(PART)!=0) {
            if(world.isLoaded(master)&&matches(world.getBlockState(master),facing,0))world.destroyBlock(master,true);
            return;
        }
        EDITING.set(true);
        try {
            for(int part=1;part<OFFSETS.length;part++) {
                BlockPos target=partPos(master,facing,part);
                if(!world.isLoaded(target))continue; // Never load another chunk to remove a prop.
                BlockState current=world.getBlockState(target);
                if(matches(current,facing,part))world.setBlock(target,current.getFluidState().createLegacyBlock(),Block.UPDATE_ALL);
            }
        } finally {EDITING.remove();}
    }
    @Override public void playerWillDestroy(Level world,BlockPos pos,BlockState state,Player player) {
        if(!world.isClientSide&&player.isCreative()&&state.getValue(PART)!=0) {
            BlockPos master=masterPos(pos,state);
            if(world.isLoaded(master)&&matches(world.getBlockState(master),state.getValue(FACING),0))world.destroyBlock(master,false);
        }
        super.playerWillDestroy(world,pos,state,player);
    }
    @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor world,BlockPos pos,BlockPos neighborPos) {
        if(state.getValue(WATERLOGGED))world.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(world));
        // One coalesced integrity check after neighbour/chunk-edge updates, never every tick.
        if(!world.isClientSide()&&!EDITING.get())world.scheduleTick(pos,this,1);
        if(state.getValue(PART)==4||state.getValue(PART)==5) {
            if(direction==Direction.DOWN)return state.setValue(LAMP_SUPPORT,needsLowerSocket(neighbor));
            if(direction==Direction.UP)return state.setValue(TOP_SUPPORT,needsUpperSocket(neighbor));
        }
        return state;
    }
    @Override public void tick(BlockState state,ServerLevel world,BlockPos pos,RandomSource random) {
        BlockPos master=masterPos(pos,state);Direction facing=state.getValue(FACING);
        if(!world.isLoaded(master))return;
        if(!matches(world.getBlockState(master),facing,0)) {
            world.setBlock(pos,state.getFluidState().createLegacyBlock(),Block.UPDATE_ALL);return;
        }
        // Validate the six explicit cells only after an edit; no area scans or rescheduling.
        for(int part=0;part<OFFSETS.length;part++) {
            BlockPos target=partPos(master,facing,part);
            if(world.isLoaded(target)&&!matches(world.getBlockState(target),facing,part)){world.destroyBlock(master,true);return;}
        }
        if (hasSignSaddle(state.getValue(PART))) {
            int mounts = findSignMounts(state, world, pos);
            if (state.getValue(SIGN_MOUNTS) != mounts)
                world.setBlock(pos, state.setValue(SIGN_MOUNTS, mounts), Block.UPDATE_CLIENTS);
        }
    }
    @Override public BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        int mounts = state.getValue(SIGN_MOUNTS);
        int mirrored = (mounts & 5) | ((mounts & 2) << 2) | ((mounts & 8) >> 2);
        return state.rotate(mirror.getRotation(state.getValue(FACING))).setValue(SIGN_MOUNTS, mirrored);
    }
    /** Face points from the support towards the attached equipment. No world lookup. */
    public static boolean isMountingPost(BlockState support,Direction face) {
        if(support.getBlock() instanceof TrafficSignPostBlock)return true;
        if(!(support.getBlock() instanceof CurvedTrafficPoleBlock))return false;
        int part=support.getValue(PART);
        return face==Direction.UP&&(part==4||part==5)
            ||face==Direction.DOWN&&(part==0||part==4||part==5)
            ||part==0&&face.getAxis().isHorizontal()
            ||(part==4||part==5)&&face.getAxis().isHorizontal();
    }
    /** A traffic sign can use a lateral saddle on the inclined middle segment. */
    public static boolean isTrafficSignMount(BlockState support, Direction face) {
        if (!face.getAxis().isHorizontal()) return false;
        if (support.getBlock() instanceof CurvedTrafficPoleBlock && support.getValue(PART) == 3)
            return face.getAxis() != support.getValue(FACING).getAxis();
        return isMountingPost(support, face);
    }
    private static boolean hasSignSaddle(int part) {
        return part == 0 || part == 3;
    }
    static boolean usesSignBand(BlockState support, Direction face) {
        return support.getBlock() instanceof CurvedTrafficPoleBlock
            && hasSignSaddle(support.getValue(PART)) && isTrafficSignMount(support, face);
    }
    private static int mountBit(Direction facing, Direction face) {
        return 1 << ((face.get2DDataValue() - facing.get2DDataValue()) & 3);
    }

    private static int findSignMounts(BlockState state, ServerLevel level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        int previous = state.getValue(SIGN_MOUNTS);
        int mounts = 0;
        for (Direction face : Direction.Plane.HORIZONTAL) {
            if (!usesSignBand(state, face)) continue;
            int bit = mountBit(facing, face);
            BlockPos neighbor = pos.relative(face);
            var chunk = LoadedRoadData.chunk(level, neighbor);
            if (chunk == null) {
                mounts |= previous & bit;
                continue;
            }
            BlockState sign = chunk.getBlockState(neighbor);
            if (sign.hasProperty(TrafficSignBlock.CURVED_MOUNT) && sign.getValue(TrafficSignBlock.POST_MOUNTED)
                    && sign.getValue(TrafficSignBlock.FACING) == face) mounts |= bit;
        }
        return mounts;
    }
    /** Block entities may be initialized before their chunk is accessible to the level. */
    public static void queueSignMount(ServerLevel level, BlockPos signPos) {
        PENDING_SIGN_MOUNTS.computeIfAbsent(level, unused -> new ConcurrentLinkedQueue<>()).add(signPos.immutable());
    }
    private static void processSignMounts(ServerLevel level) {
        Queue<BlockPos> pending = PENDING_SIGN_MOUNTS.get(level);
        if (pending == null) return;
        for (int i = 0; i < SIGN_MOUNTS_PER_TICK; i++) {
            BlockPos signPos = pending.poll();
            if (signPos == null) return;
            BlockState sign = LoadedRoadData.blockState(level, signPos);
            if (!(sign.getBlock() instanceof TrafficSignBlock) || !sign.getValue(TrafficSignBlock.POST_MOUNTED)) continue;
            Direction face = sign.getValue(TrafficSignBlock.FACING);
            BlockPos support = signPos.relative(face.getOpposite());
            var supportChunk = LoadedRoadData.chunk(level, support);
            if (supportChunk == null) continue;
            BlockState state = supportChunk.getBlockState(support);
            BlockState updatedSign = TrafficSignBlock.withPostHardware(sign, state);
            if (updatedSign != sign) level.setBlock(signPos, updatedSign, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            if (updatedSign.hasProperty(TrafficSignBlock.CURVED_MOUNT) && usesSignBand(state, face)
                    && (state.getValue(SIGN_MOUNTS) & mountBit(state.getValue(FACING), face)) == 0) {
                level.scheduleTick(support, state.getBlock(), 1);
            }
        }
    }
    private static boolean needsUpperSocket(BlockState attached) {
        if(attached.getBlock() instanceof WarningLightBlock && attached.getValue(WarningLightBlock.COMPACT))return false;
        if(attached.getBlock() instanceof ChevronPanelBlock)return true;
        if(attached.getBlock() instanceof LedDeviceBlock)return !attached.getValue(LedDeviceBlock.WALL_MOUNTED)&&!attached.getValue(LedDeviceBlock.POST_ABOVE);
        return needsLowerSocket(attached);
    }
    private static boolean needsLowerSocket(BlockState attached) {
        if(attached.getBlock() instanceof WarningLightBlock && attached.getValue(WarningLightBlock.COMPACT))return false;
        if(attached.getBlock() instanceof TrafficLightBlock || attached.getBlock() instanceof TrafficSignPostBlock)return true;
        if(attached.getBlock() instanceof RoadInformationSignBlock)
            return !attached.getValue(RoadInformationSignBlock.POST_MOUNTED)&&!attached.getValue(RoadInformationSignBlock.WALL_MOUNTED);
        if(attached.getBlock() instanceof TrafficSignBlock)
            return !attached.getValue(TrafficSignBlock.POST_MOUNTED)&&!attached.getValue(TrafficSignBlock.WALL_MOUNTED);
        return attached.getBlock() instanceof LedDeviceBlock && !attached.getValue(LedDeviceBlock.WALL_MOUNTED)
            && attached.getValue(LedDeviceBlock.POST_ABOVE);
    }
    @Override public boolean canConnect(BlockState state,Direction direction) {
        int part=state.getValue(PART);
        return direction==Direction.UP&&(part==4||part==5)
            ||direction==Direction.DOWN&&(part==0||part==4||part==5)
            ||part==0&&direction.getAxis().isHorizontal()
            ||(part==4||part==5)&&direction.getAxis().isHorizontal();
    }
    @Override public boolean canAttach(BlockState state,BlockPos pos,Direction direction){return canConnect(state,direction);}
    
}
