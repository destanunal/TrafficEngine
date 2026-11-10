import com.destan.trafficengine.block.*;
import com.destan.trafficengine.block.data.StreetLampShapes;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.shapes.*;
import java.util.*;
import java.lang.reflect.*;

public class FluorescentCeilingCheck {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static class World implements BlockGetter {
        final Map<BlockPos,BlockState> blocks=new HashMap<>();
        int reads,waterTicks;
        public BlockState getBlockState(BlockPos pos){reads++;return blocks.getOrDefault(pos,Blocks.AIR.defaultBlockState());}
        public BlockEntity getBlockEntity(BlockPos pos){throw new AssertionError("No block entity reads");}
        public FluidState getFluidState(BlockPos pos){return Fluids.EMPTY.defaultFluidState();}
        public int getHeight(){return 384;}
        public int getMinBuildHeight(){return -64;}
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var registry=BuiltInRegistries.BLOCK;
        var holders=MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");holders.setAccessible(true);
        var frozen=MappedRegistry.class.getDeclaredField("frozen");frozen.setAccessible(true);
        Object previous=holders.get(registry);boolean wasFrozen=frozen.getBoolean(registry);
        FluorescentTubeLampBlock lamp;StreetLightBlock postLike;
        try{frozen.setBoolean(registry,false);holders.set(registry,new IdentityHashMap<>());
            lamp=new FluorescentTubeLampBlock();postLike=new StreetLightBlock();
        }finally{holders.set(registry,previous);frozen.setBoolean(registry,wasFrozen);}
        var mount=FluorescentTubeLampBlock.class.getDeclaredMethod("mountingState",BlockState.class,BlockGetter.class,BlockPos.class,BlockState.class);
        mount.setAccessible(true);
        World world=new World();BlockPos pos=BlockPos.ZERO;
        check(!lamp.defaultBlockState().getValue(FluorescentTubeLampBlock.CEILING_MOUNTED),"Old states retain pole mounting by default");
        var context=CollisionContext.empty();
        Direction[] directions={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST,Direction.UP};
        var properties=new net.minecraft.world.level.block.state.properties.BooleanProperty[]{StreetLightBlock.NORTH,StreetLightBlock.EAST,StreetLightBlock.SOUTH,StreetLightBlock.WEST,StreetLightBlock.UP};
        VoxelShape[] arms={Block.box(7,7,0,9,9,7),Block.box(9,7,7,16,9,9),Block.box(7,7,9,9,9,16),Block.box(0,7,7,7,9,9),Block.box(7,9,7,9,16,9)};
        for(Direction facing:Direction.Plane.HORIZONTAL)for(int mask=0;mask<32;mask++)for(boolean lit:new boolean[]{false,true})for(boolean water:new boolean[]{false,true}) {
            BlockState original=lamp.defaultBlockState().setValue(StreetLampBaseBlock.FACING,facing).setValue(StreetLampBaseBlock.LIT,lit).setValue(StreetLampBaseBlock.WATERLOGGED,water);
            VoxelShape expected=StreetLampShapes.FLUORESCENT.get(facing);
            for(int i=0;i<5;i++){original=original.setValue(properties[i],(mask&(1<<i))!=0);if((mask&(1<<i))!=0)expected=Shapes.or(expected,arms[i]);}
            int reads=world.reads;
            var shape=original.getShape(world,pos);
            check(!Shapes.joinIsNotEmpty(shape,expected,BooleanOp.NOT_SAME),"All original connector/collision shapes are preserved");
            for(int repeat=0;repeat<100;repeat++)check(original.getShape(world,pos)==shape,"Pole shape queries reuse cached geometry");
            check(world.reads==reads,"Shape queries perform no world scans");
            BlockState mounted=(BlockState)mount.invoke(lamp,original,world,pos,Blocks.STONE.defaultBlockState());
            check(mounted.getValue(FluorescentTubeLampBlock.CEILING_MOUNTED),"Solid ceiling enables flush mounting");
            check(mounted.getValue(StreetLampBaseBlock.FACING)==facing&&mounted.getValue(StreetLampBaseBlock.LIT)==lit&&mounted.getValue(StreetLampBaseBlock.WATERLOGGED)==water,"Mounting retains direction, power and water state");
            for(int i=0;i<5;i++)check(!mounted.getValue(properties[i])&&!lamp.canConnect(mounted,directions[i]),"Ceiling mode has no dangling pole connector");
            var ceiling=mounted.getShape(world,pos);
            check(Math.abs(ceiling.max(Direction.Axis.Y)-1)<1e-9,"Housing top is exactly flush with ceiling");
            check(ceiling.toAabbs().size()==1,"Ceiling lamp has one simple selection/collision box");
            check(!Shapes.joinIsNotEmpty(ceiling,StreetLampShapes.FLUORESCENT.get(facing).move(0,6.7/16,0),BooleanOp.NOT_SAME),"Model and picking share the exact ceiling rise");
            check(ceiling==mounted.getShape(world,pos),"Ceiling geometry is cached");
            check(mounted.getOcclusionShape(world,pos).isEmpty(),"No phantom center stem occludes ceiling lighting");
            BlockState removed=(BlockState)mount.invoke(lamp,mounted,world,pos,Blocks.AIR.defaultBlockState());
            check(!removed.getValue(FluorescentTubeLampBlock.CEILING_MOUNTED),"Removing ceiling restores ordinary mounting");
        }
        for(BlockState above:new BlockState[]{Blocks.GLASS.defaultBlockState(),Blocks.OAK_SLAB.defaultBlockState(),Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE,net.minecraft.world.level.block.state.properties.SlabType.TOP),Blocks.AIR.defaultBlockState(),postLike.defaultBlockState()}) {
            BlockState result=(BlockState)mount.invoke(lamp,lamp.defaultBlockState(),world,pos,above);
            boolean expected=!(above.getBlock() instanceof com.destan.trafficengine.block.data.ITrafficPostLike)&&above.isFaceSturdy(world,pos.above(),Direction.DOWN);
            check(result.getValue(FluorescentTubeLampBlock.CEILING_MOUNTED)==expected,"Uses native underside support; poles remain pole mounts");
        }
        LevelAccessor level=(LevelAccessor)Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(),new Class[]{LevelAccessor.class},(proxy,method,values)->{
            return switch(method.getName()) {
                case "getBlockState" -> world.getBlockState((BlockPos)values[0]);
                case "getFluidState" -> world.getFluidState((BlockPos)values[0]);
                case "scheduleTick" -> {world.waterTicks++;yield null;}
                default -> throw new AssertionError("Unexpected world call: "+method.getName());
            };
        });
        var state=lamp.defaultBlockState().setValue(StreetLampBaseBlock.WATERLOGGED,true);
        var mounted=lamp.updateShape(state,Direction.UP,Blocks.STONE.defaultBlockState(),level,pos,pos.above());
        check(mounted.getValue(FluorescentTubeLampBlock.CEILING_MOUNTED),"Ceiling neighbor update mounts the lamp");
        int reads=world.reads;
        check(lamp.updateShape(mounted,Direction.EAST,Blocks.STONE.defaultBlockState(),level,pos,pos.east())==mounted,"Horizontal changes leave flush mount stable");
        check(world.reads==reads,"Horizontal events do not rescan support");
        world.blocks.put(pos.west(),lamp.defaultBlockState());
        var restored=lamp.updateShape(mounted,Direction.UP,Blocks.AIR.defaultBlockState(),level,pos,pos.above());
        check(!restored.getValue(FluorescentTubeLampBlock.CEILING_MOUNTED)&&restored.getValue(StreetLightBlock.WEST),"Ceiling removal restores existing neighbor connections");
        check(world.waterTicks==3,"Both mount modes retain water tick scheduling");
        System.out.println("PASS FluorescentCeiling: "+checks+" cached geometry, support transitions and water/light preservation assertions");
    }
}
