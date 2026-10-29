import com.destan.trafficengine.block.*;
import com.destan.trafficengine.block.data.*;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.*;
import java.nio.file.*;
import java.util.*;

/** Native terrain-paint regression, actual particle-retention handler and authored-geometry envelopes. */
public final class DoubleRail22Check {
    static int checks,vertices;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static final class Terrain implements BlockGetter {
        Map<BlockPos,BlockState> blocks=new HashMap<>();
        public BlockState getBlockState(BlockPos p){return blocks.getOrDefault(p,Blocks.AIR.defaultBlockState());}
        public BlockEntity getBlockEntity(BlockPos p){throw new AssertionError("Unexpected entity lookup");}
        public FluidState getFluidState(BlockPos p){throw new AssertionError("Unexpected fluid lookup");}
        public int getHeight(){return 384;}public int getMinBuildHeight(){return -64;}
    }
    static Block[] blocks()throws Exception {
        var registry=net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        var holders=MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");holders.setAccessible(true);
        var frozen=MappedRegistry.class.getDeclaredField("frozen");frozen.setAccessible(true);
        Object previous=holders.get(registry);boolean was=frozen.getBoolean(registry);
        try{frozen.setBoolean(registry,false);holders.set(registry,new IdentityHashMap<>());
            return new Block[]{new SlopedPedestrianRailingBlock(),new AsphaltSlope(RoadType.ASPHALT),new PaintedAsphaltSlope(RoadType.ASPHALT,null),new DoubleSidedTrafficSignBlock(),new TrafficSignBlock()};
        }finally{holders.set(registry,previous);frozen.setBoolean(registry,was);}
    }
    static void terrain(Block[] b){
        Terrain world=new Terrain();BlockPos pos=BlockPos.ZERO;
        for(Direction facing:Direction.Plane.HORIZONTAL)for(int base=1;base<=8;base++)for(int front=1;front<=8;front++){
            var normal=b[1].defaultBlockState().setValue(AsphaltSlope.LAYERS,base);
            var painted=b[2].defaultBlockState().setValue(PaintedAsphaltSlope.LAYERS,base);
            check(RailingGeometry.sinkFor(normal)==RailingGeometry.sinkFor(painted),"Painting preserves support height");
            check(!RailingGeometry.layerHeightChanged(normal,painted)&&!RailingGeometry.layerHeightChanged(painted,normal),"Paint/unpaint does not invalidate terrain");
            world.blocks.clear();world.blocks.put(pos.below(),normal);
            world.blocks.put(pos.relative(facing).below(),b[1].defaultBlockState().setValue(AsphaltSlope.LAYERS,front));
            var before=SlopedPedestrianRailingBlock.terrainProfile(b[0].defaultBlockState().setValue(SlopedPedestrianRailingBlock.FACING,facing),world,pos);
            var pick=before.getShape(world,pos);var collision=before.getCollisionShape(world,pos);
            world.blocks.put(pos.below(),painted);world.blocks.put(pos.relative(facing).below(),b[2].defaultBlockState().setValue(PaintedAsphaltSlope.LAYERS,front));
            var after=SlopedPedestrianRailingBlock.terrainProfile(before,world,pos);
            check(before==after,"Painting both supports preserves sink, rise, direction and connections");
            check(pick==after.getShape(world,pos)&&collision==after.getCollisionShape(world,pos),"Painting preserves cached picking/collision");
            if(base<8)check(RailingGeometry.layerHeightChanged(painted,painted.setValue(PaintedAsphaltSlope.LAYERS,base+1)),"Real height edits still invalidate terrain");
        }
        check(PaintedAsphaltSlope.class.isAssignableFrom(ConnectedRoadSlopeBlock.class),"Automatic-marking slopes inherit the corrected height path");
    }
    static void particles(Block[] b)throws Exception {
        var instance=new com.destan.trafficengine.mixin.ParticleEngineMixin();
        var type=instance.getClass();
        var reduce=type.getDeclaredMethod("trafficengine$reduceDestruction",BlockPos.class,BlockState.class,com.llamalad7.mixinextras.injector.wrapoperation.Operation.class);reduce.setAccessible(true);
        var skip=type.getDeclaredMethod("trafficengine$skipSomeBreakParticles",net.minecraft.client.particle.Particle.class,org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);skip.setAccessible(true);
        for(int blockIndex:new int[]{3,4}) {
            var ref=b[blockIndex].builtInRegistryHolder();var tags=ref.getClass().getDeclaredField("tags");tags.setAccessible(true);tags.set(ref,Set.of());
            for(boolean diagonal:new boolean[]{false,true}) {
                int[] retained={0};
                com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original=arguments->{
                    check(!((BlockState)arguments[1]).getValue(TrafficSignBlock.DIAGONAL),"Particles use normal-state geometry");
                    for(int i=0;i<256;i++)try{
                        var ci=new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("add",true);skip.invoke(instance,null,ci);
                        if(!ci.isCancelled())retained[0]++;
                    }catch(Exception e){throw new RuntimeException(e);}return null;
                };
                reduce.invoke(instance,BlockPos.ZERO,b[blockIndex].defaultBlockState().setValue(TrafficSignBlock.DIAGONAL,diagonal),original);
                check(retained[0]==(blockIndex==3?128:256),"Double signs retain exactly half; single signs retain their count");
            }
        }
    }
    static JsonObject read(Path p)throws Exception{return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
    static void envelope(Block block)throws Exception {
        Path assets=Path.of("src/main/resources/assets/trafficengine");JsonArray multipart=read(assets.resolve("blockstates/sloped_pedestrian_railing.json")).getAsJsonArray("multipart");
        Map<String,JsonObject> models=new HashMap<>();Terrain world=new Terrain();
        for(BlockState state:block.getStateDefinition().getPossibleStates()) {
            var pick=state.getShape(world,BlockPos.ZERO);var collision=state.getCollisionShape(world,BlockPos.ZERO);
            float[] edges=EquipmentOutlineGeometry.edges(state);
            check(edges.length==(state.getValue(SlopedPedestrianRailingBlock.BACK)?216:108),"Only twelve exterior panel edges plus an optional back connection");
            check(edges==EquipmentOutlineGeometry.edges(state),"Incline outline is cached");
            check(pick==state.getShape(world,BlockPos.ZERO)&&collision==state.getCollisionShape(world,BlockPos.ZERO),"Outline never alters picking/collision");
            if(state.getValue(SlopedPedestrianRailingBlock.FACING)!=Direction.NORTH||!state.getValue(SlopedPedestrianRailingBlock.GRAY)||state.getValue(SlopedPedestrianRailingBlock.WATERLOGGED))continue;
            int r=state.getValue(SlopedPedestrianRailingBlock.RISE);boolean down=state.getValue(SlopedPedestrianRailingBlock.DESCENDING);if(down)r=Math.min(r,8-state.getValue(SlopedPedestrianRailingBlock.SINK));
            double slope=r*(down?-2:2)/16D;
            double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},hi={-Double.POSITIVE_INFINITY,-Double.POSITIVE_INFINITY,-Double.POSITIVE_INFINITY};
            for(int i=0;i<108;i+=9)for(int end=0;end<2;end++){
                double[] p={edges[i+end*3],edges[i+end*3+1],edges[i+end*3+2]};p[1]-=(.5-p[2])*slope;
                for(int axis=0;axis<3;axis++){lo[axis]=Math.min(lo[axis],p[axis]);hi[axis]=Math.max(hi[axis],p[axis]);}
            }
            for(JsonElement raw:multipart){
                JsonObject part=raw.getAsJsonObject(),when=part.getAsJsonObject("when");boolean matches=true;
                for(var condition:when.entrySet()) {
                    var prop=state.getBlock().getStateDefinition().getProperty(condition.getKey());
                    if(prop==null||!state.getValue(prop).toString().equals(condition.getValue().getAsString())){matches=false;break;}
                }if(!matches)continue;
                String model=part.getAsJsonObject("apply").get("model").getAsString().replace("trafficengine:","");
                JsonObject mesh=models.get(model);if(mesh==null){mesh=read(assets.resolve("models/"+model+".json"));models.put(model,mesh);}
                for(JsonElement element:mesh.getAsJsonArray("elements")){
                    JsonObject e=element.getAsJsonObject();JsonArray a=e.getAsJsonArray("from"),z=e.getAsJsonArray("to");
                    for(int corner=0;corner<8;corner++){
                        double[] p=new double[3];for(int axis=0;axis<3;axis++)p[axis]=((corner&(1<<axis))==0?a:z).get(axis).getAsDouble()/16;
                        JsonObject apply=part.getAsJsonObject("apply");double angle=Math.toRadians(apply.has("y")?apply.get("y").getAsDouble():0),dx=p[0]-.5,dz=p[2]-.5;
                        p[0]=.5+Math.cos(angle)*dx-Math.sin(angle)*dz;p[2]=.5+Math.sin(angle)*dx+Math.cos(angle)*dz;
                        if(p[2]>9D/16+1e-6 && state.getValue(SlopedPedestrianRailingBlock.BACK)) {
                            double drop=state.getValue(SlopedPedestrianRailingBlock.SINK)*2D/16;
                            check(p[0]>=7.35/16-1e-6&&p[0]<=8.65/16+1e-6&&p[1]>=2.5/16-drop-1e-6&&p[1]<=1-drop+1e-6&&p[2]<=1+1e-6,"Back arm stays inside its straight connection outline");
                        } else {
                            p[1]-=(.5-p[2])*slope;
                            for(int axis=0;axis<3;axis++)check(p[axis]>=lo[axis]-1e-6&&p[axis]<=hi[axis]+1e-6,"Authored railing stays inside slanted frame: "+model+" axis "+axis+" "+state);
                        }
                        vertices++;
                    }
                }
            }
        }
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        Block[] b=blocks();terrain(b);particles(b);envelope(b[0]);
        System.out.println("PASS "+checks+" checks; "+vertices+" authored railing vertices enclosed; paint/unpaint preserves terrain/picking/collision; actual particle handler halves both double-sign orientations.");
    }
}
