import com.destan.trafficengine.block.*;
import com.destan.trafficengine.client.model.TrafficLensGeometry;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.client.renderer.block.model.BlockModel;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Native no-window regression harness. The tiny generated Level only substitutes world I/O. */
public class Update201Check implements Opcodes {
    public static int checks,reads,writes,sounds,events;
    static final Map<BlockPos,BlockState> blocks=new HashMap<>();
    static final Set<BlockPos> powered=new HashSet<>();
    static int loadedTop=5000;
    static PedestrianRailingGateBlock gate;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static BlockState read(BlockPos p){check(loaded(p),"No unloaded block access");reads++;return blocks.getOrDefault(p,Blocks.AIR.defaultBlockState());}
    public static boolean loaded(BlockPos p){return p.getY()<=loadedTop;}
    public static boolean power(BlockPos p){return powered.contains(p);}
    public static boolean write(Level world,BlockPos p,BlockState s){
        writes++;check(writes<1100,"No recursive notification storm");blocks.put(p.immutable(),s);
        for(Direction d:Direction.Plane.VERTICAL){BlockPos n=p.relative(d);BlockState other=read(n);if(other.getBlock()==gate)gate.neighborChanged(other,world,n,gate,p,false);}
        return true;
    }
    static final class Loader extends ClassLoader {
        Class<?> make(byte[] bytes){return defineClass("Traffic201FakeLevel",bytes,0,bytes.length);}
    }
    static Level world()throws Exception{
        ClassWriter c=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.visit(V17,ACC_PUBLIC,"Traffic201FakeLevel",null,"net/minecraft/world/level/Level",null);
        Set<String> seen=new HashSet<>();
        for(Method m:Level.class.getMethods()){
            String n=m.getName(),desc=org.objectweb.asm.Type.getMethodDescriptor(m);
            if(!Set.of("getBlockState","hasChunkAt","getMinBuildHeight","getMaxBuildHeight","getHeight","hasNeighborSignal","setBlockAndUpdate","playSound","gameEvent").contains(n)||Modifier.isFinal(m.getModifiers())||!seen.add(n+desc))continue;
            if(n.equals("hasChunkAt")&&!desc.equals("(Lnet/minecraft/core/BlockPos;)Z"))continue;
            MethodVisitor v=c.visitMethod(ACC_PUBLIC,n,desc,null,null);v.visitCode();
            if(n.equals("getBlockState")||n.equals("hasChunkAt")||n.equals("hasNeighborSignal")){
                v.visitVarInsn(ALOAD,1);v.visitMethodInsn(INVOKESTATIC,"Update201Check",n.equals("getBlockState")?"read":n.equals("hasChunkAt")?"loaded":"power",desc,false);v.visitInsn(n.equals("getBlockState")?ARETURN:IRETURN);
            }else if(n.equals("setBlockAndUpdate")){
                v.visitVarInsn(ALOAD,0);v.visitVarInsn(ALOAD,1);v.visitVarInsn(ALOAD,2);v.visitMethodInsn(INVOKESTATIC,"Update201Check","write","(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",false);v.visitInsn(IRETURN);
            }else if(n.equals("playSound")||n.equals("gameEvent")){
                String f=n.equals("playSound")?"sounds":"events";v.visitFieldInsn(GETSTATIC,"Update201Check",f,"I");v.visitInsn(ICONST_1);v.visitInsn(IADD);v.visitFieldInsn(PUTSTATIC,"Update201Check",f,"I");v.visitInsn(RETURN);
            }else{v.visitLdcInsn(n.equals("getMinBuildHeight")?0:5000);v.visitInsn(IRETURN);}
            v.visitMaxs(0,0);v.visitEnd();
        }
        c.visitEnd();Class<?> fake=new Loader().make(c.toByteArray());
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        return (Level)((sun.misc.Unsafe)f.get(null)).allocateInstance(fake);
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var registry=net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        Field holders=MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders"),frozen=MappedRegistry.class.getDeclaredField("frozen");holders.setAccessible(true);frozen.setAccessible(true);
        Object previous=holders.get(registry);boolean wasFrozen=frozen.getBoolean(registry);HorizontalTrafficLightBlock light;CurvedTrafficPoleBlock pole;TrafficLightRequestButtonBlock request;
        frozen.setBoolean(registry,false);holders.set(registry,new IdentityHashMap<>());
        try{gate=new PedestrianRailingGateBlock();light=new HorizontalTrafficLightBlock();pole=new CurvedTrafficPoleBlock();request=new TrafficLightRequestButtonBlock();}finally{holders.set(registry,previous);frozen.setBoolean(registry,wasFrozen);}
        Level world=world();BlockPos pos=new BlockPos(0,20,0);var base=gate.defaultBlockState();
        for(int y=20;y<=22;y++)blocks.put(new BlockPos(0,y,0),base);
        for(int y=20;y<=22;y++){
            BlockPos p=new BlockPos(0,y,0);BlockState s=blocks.get(p);
            s=gate.updateShape(s,Direction.UP,read(p.above()),world,p,p.above());s=gate.updateShape(s,Direction.DOWN,read(p.below()),world,p,p.below());blocks.put(p,s);
            check(s.getValue(PedestrianRailingGateBlock.UP)==(y<22),"Upper gate connection");check(s.getValue(PedestrianRailingGateBlock.DOWN)==(y>20),"Lower gate connection");
        }
        Method bounds=gate.getClass().getDeclaredMethod("stackBounds",LevelReader.class,BlockPos.class,BlockState.class);bounds.setAccessible(true);
        long range=(long)bounds.invoke(null,world,pos,read(pos));check((int)(range>>32)==20&&(int)range==22,"Column bounds from lower segment");
        powered.add(pos.above(2));writes=sounds=events=0;
        gate.neighborChanged(read(pos),world,pos,Blocks.REDSTONE_BLOCK,pos.east(),false);
        check(writes==3&&sounds==1&&events==1,"Power any segment: exactly three state writes and one sound/event");
        for(BlockState s:blocks.values())check(s.getValue(PedestrianRailingGateBlock.OPEN)&&s.getValue(PedestrianRailingGateBlock.POWERED),"Whole stack opens");
        writes=sounds=events=0;gate.neighborChanged(read(pos),world,pos,Blocks.STONE,pos.east(),false);check(writes==0&&sounds==0&&events==0,"Unchanged power does not publish state updates");
        powered.clear();gate.neighborChanged(read(pos.above()),world,pos.above(),Blocks.AIR,pos.east(),false);
        check(writes==3&&sounds==1&&events==1,"Removing power closes whole stack once");
        Method apply=gate.getClass().getDeclaredMethod("applyColumn",Level.class,BlockPos.class,long.class,net.minecraft.world.level.block.state.properties.BooleanProperty.class,boolean.class,boolean.class);apply.setAccessible(true);
        writes=0;apply.invoke(null,world,pos,range,PedestrianRailingGateBlock.OPEN,true,false);check(writes==3,"Manual toggle affects whole column");
        writes=0;apply.invoke(null,world,pos,range,PedestrianRailingGateBlock.GRAY,false,false);check(writes==3,"Wrench color affects joined column");
        for(BlockState s:blocks.values())check(s.getValue(PedestrianRailingGateBlock.OPEN)&&!s.getValue(PedestrianRailingGateBlock.GRAY),"Manual state and color preserved");
        blocks.put(pos.above(),base.setValue(PedestrianRailingGateBlock.FACING,Direction.EAST));range=(long)bounds.invoke(null,world,pos,read(pos));check((int)range==20,"Differently oriented gates stay separate");
        blocks.put(pos.above(),base);loadedTop=20;range=(long)bounds.invoke(null,world,pos,read(pos));check((int)range==20,"Does not read an unloaded neighbor");loadedTop=5000;
        blocks.clear();for(int y=0;y<2000;y++)blocks.put(new BlockPos(0,y,0),base);range=(long)bounds.invoke(null,world,new BlockPos(0,0,0),base);check((int)range==1023,"Work is bounded for pathological stacks");
        blocks.clear();reads=0;Set<VoxelShape> lightShapes=Collections.newSetFromMap(new IdentityHashMap<>()),gateShapes=Collections.newSetFromMap(new IdentityHashMap<>());
        for(BlockState s:light.getStateDefinition().getPossibleStates()){
            VoxelShape a=s.getShape(world,pos);check(a==s.getShape(world,pos),"Horizontal shapes cached");lightShapes.add(a);check(a==s.cycle(TrafficLightBlock.DIAGONAL).getShape(world,pos),"Legacy diagonal horizontal states use cardinal collision");
            check(!s.getCollisionShape(world,pos).isEmpty(),"Every mount variant has collision");
        }
        for(BlockState s:gate.getStateDefinition().getPossibleStates()){
            VoxelShape a=s.getShape(world,pos);check(a==s.getShape(world,pos),"Joined gate shapes cached");gateShapes.add(a);
            check(s.getCollisionShape(world,pos).isEmpty()==s.getValue(PedestrianRailingGateBlock.OPEN),"Open tall gates permit passage");
        }
        for(BlockState s:request.getStateDefinition().getPossibleStates()){
            VoxelShape shape=s.getShape(world,pos);
            check(shape==s.getShape(world,pos),"Request outline remains cached");
            for(double y:new double[]{3.2,12.2}){
                VoxelShape strap=Block.box(6.78,y,6.78,9.22,y+.6,9.22);
                check(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(strap,shape,net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST),"Request outline contains both complete mounting straps in every direction");
            }
        }
        check(lightShapes.size()==48,"Finite 48 horizontal/mount outlines");check(gateShapes.size()==16,"Finite 16 joined gate outlines");check(reads==0,"Warm shape queries never inspect world");
        for(int value=1;value<=999;value++){
            String digits=""+value;int ones=0;for(char d:digits.toCharArray())if(d=='1')ones++;
            float scale=TrafficLensGeometry.countdownScale(digits.length(),ones),w=(digits.length()-ones)*1.6F+ones*.4F+(digits.length()-1)*.8F;
            check(Math.hypot(w*scale/2,3.2*scale/2)<=1.826,"Every 1-999 countdown fits circular lens with margin");
        }
        int models=0;for(String file:Files.readAllLines(Path.of(args[0]))){BlockModel model=BlockModel.fromString(Files.readString(Path.of(file)));check(model!=null,"Native model parser: "+file);models++;}
        System.out.printf("PASS: %,d native checks; %d models; 48 horizontal and 16 gate cached outlines; Redstone/manual column updates bounded, no redundant writes; every countdown 1-999 fits. No FPS measurement.%n",checks,models);
    }
}
