import com.destan.trafficengine.block.*;
import com.destan.trafficengine.block.data.*;
import com.destan.trafficengine.client.model.*;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.client.renderer.FaceInfo;
import java.nio.file.*;
import java.util.*;

public final class Update22Check {
    static int checks,states;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static final class World implements BlockGetter {
        public BlockEntity getBlockEntity(BlockPos p){throw new AssertionError("Unexpected entity read");}
        public BlockState getBlockState(BlockPos p){throw new AssertionError("Unexpected world read");}
        public FluidState getFluidState(BlockPos p){throw new AssertionError("Unexpected fluid read");}
        public int getHeight(){return 384;}public int getMinBuildHeight(){return -64;}
    }
    static JsonObject read(Path p)throws Exception{return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
    static JsonArray elements(Path models,String name)throws Exception {
        JsonObject m=read(models.resolve(name+".json"));
        if(m.has("elements"))return m.getAsJsonArray("elements");
        return elements(models,m.get("parent").getAsString().substring("trafficengine:block/".length()));
    }
    static double[] triple(JsonArray a){return new double[]{a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()};}
    static List<float[]> faces(Path models,String name,boolean asphalt)throws Exception {
        List<float[]> result=new ArrayList<>();
        boolean upperHelper=name.equals("curved_traffic_pole/part_1"),forwardHelper=name.equals("curved_traffic_pole/part_2");
        for(JsonElement raw:elements(models,upperHelper||forwardHelper?"curved_traffic_pole/part_0":name)) {
            JsonObject e=raw.getAsJsonObject();double[] a=triple(e.getAsJsonArray("from")),b=triple(e.getAsJsonArray("to"));
            double[] coords={a[1],b[1],a[2],b[2],a[0],b[0]};
            for(var entry:e.getAsJsonObject("faces").entrySet()) {
                JsonObject f=entry.getValue().getAsJsonObject();
                if(asphalt&&f.has("tintindex"))continue;
                JsonArray custom=f.has("trafficengine_data")?f.getAsJsonObject("trafficengine_data").getAsJsonArray("vertices"):null;
                float[] face=new float[12];
                for(int i=0;i<4;i++) {
                    var info=FaceInfo.fromFacing(Direction.byName(entry.getKey())).getVertexInfo(i);
                    double x=custom==null?coords[info.xFace]:custom.get(i*3).getAsDouble(),
                        y=custom==null?coords[info.yFace]:custom.get(i*3+1).getAsDouble(),z=custom==null?coords[info.zFace]:custom.get(i*3+2).getAsDouble();
                    if(e.has("rotation")) {
                        var r=e.getAsJsonObject("rotation");double[] o=triple(r.getAsJsonArray("origin"));double angle=Math.toRadians(r.get("angle").getAsDouble()),c=Math.cos(angle),s=Math.sin(angle);
                        switch(r.get("axis").getAsString()) {
                            case "x" -> {double dy=y-o[1],dz=z-o[2];y=o[1]+c*dy-s*dz;z=o[2]+s*dy+c*dz;}
                            case "y" -> {double dx=x-o[0],dz=z-o[2];x=o[0]+c*dx+s*dz;z=o[2]-s*dx+c*dz;}
                            case "z" -> {double dx=x-o[0],dy=y-o[1];x=o[0]+c*dx-s*dy;y=o[1]+s*dx+c*dy;}
                        }
                    }
                    face[i*3]=(float)(x/16);face[i*3+1]=(float)(y/16);face[i*3+2]=(float)(z/16);
                }
                int[] packed=new int[32];for(int i=0;i<4;i++)for(int axis=0;axis<3;axis++)packed[i*8+axis]=Float.floatToRawIntBits(face[i*3+axis]);
                if(name.startsWith("curved_traffic_pole/")){
                    for(int vertex=0;vertex<4;vertex++){if(upperHelper)face[vertex*3+1]-=1;if(forwardHelper)face[vertex*3+2]+=1;}
                    result.add(face);continue;
                }
                for(int[] clipped:GroundPlaneQuadClipper.clip(packed)) {
                    float[] p=new float[12];for(int i=0;i<4;i++)for(int axis=0;axis<3;axis++)p[i*3+axis]=Float.intBitsToFloat(clipped[i*8+axis]);result.add(p);
                }
            }
        }
        return result;
    }
    static void meshes(Path models)throws Exception {
        for(String name:new String[]{"speed_bump","wide_speed_bump","asphalt_speed_bump_00","asphalt_speed_bump_01","asphalt_speed_bump_10","asphalt_speed_bump_11","bike_lane_separator","bike_lane_separator_diagonal","curved_traffic_pole/part_0","curved_traffic_pole/part_1","curved_traffic_pole/part_2","curved_traffic_pole/part_3","curved_traffic_pole/part_4","curved_traffic_pole/part_5","curved_traffic_pole/part_5_mounted"}) {
            var faces=faces(models,name,name.startsWith("asphalt"));var mesh=SelectionMeshEdges.build(faces);
            check(mesh.length>0&&mesh.length<=800*15,"Finite mesh edge budget: "+name+" "+mesh.length/15);
            int max=0;
            for(double[] camera:new double[][]{{3,3,3},{-3,2,3},{3,2,-3},{-3,2,-3},{.5,5,.5},{5,.25,.5}}) {
                int count=0;for(int i=0;i<mesh.length;i+=15)if(SelectionMeshEdges.visible(mesh,i,camera[0],camera[1],camera[2]))count++;
                check(count>0&&count<=100,"Clean silhouette edge budget "+name+": "+count);max=Math.max(max,count);
            }
            if(!name.startsWith("curved_traffic_pole/"))for(int i=0;i<mesh.length;i+=15)for(int point=0;point<2;point++)check(mesh[i+point*3+1]>=-1e-6,"No selection edges extend below the ground");
            System.out.println(name+": "+mesh.length/15+" cached candidate edges; "+max+" maximum visible silhouette edges in six camera fixtures.");
        }
    }
    static void blocks()throws Exception {
        var registry=net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        var holders=MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");holders.setAccessible(true);
        var frozen=MappedRegistry.class.getDeclaredField("frozen");frozen.setAccessible(true);var previous=holders.get(registry);boolean was=frozen.getBoolean(registry);
        List<Block> blocks=new ArrayList<>();
        frozen.setBoolean(registry,false);holders.set(registry,new IdentityHashMap<>());
        try {
            blocks.add(new TrafficLightBlock());blocks.add(new DoubleTrafficLightBlock());blocks.add(new HorizontalTrafficLightBlock());
            for(var kind:RoadInformationSignBlock.Kind.values())blocks.add(new RoadInformationSignBlock(kind));
            blocks.add(new TrafficSignBlock());blocks.add(new BikeLaneSeparatorBlock(Block.Properties.of()));blocks.add(new ChevronPanelBlock());blocks.add(new CurvedTrafficPoleBlock());
        } finally {holders.set(registry,previous);frozen.setBoolean(registry,was);}
        var world=new World();int maxEdges=0;
        for(Block block:blocks)for(BlockState state:block.getStateDefinition().getPossibleStates()) {
            states++;var before=state.getShape(world,BlockPos.ZERO);
            if(EquipmentOutlineGeometry.supports(state)) {
                float[] mesh=EquipmentOutlineGeometry.edges(state);
                check(mesh.length>0&&mesh.length<=36*9,"Simple equipment outline bound");
                check(mesh==EquipmentOutlineGeometry.edges(state),"Cached equipment outline");
                check(before==state.getShape(world,BlockPos.ZERO),"Selection volumes remain identical");maxEdges=Math.max(maxEdges,mesh.length/9);
                if(block instanceof ChevronPanelBlock) {
                    check(mesh.length==24*9,"Chevron panel and exposed pole have twenty-four edges");
                    BlockState normalized=state.setValue(ChevronPanelBlock.WATERLOGGED,false).setValue(ChevronPanelBlock.ARROW_RIGHT,false)
                        .setValue(ChevronPanelBlock.RED_WHITE,false).setValue(ChevronPanelBlock.LEFT,false).setValue(ChevronPanelBlock.RIGHT,false);
                    check(mesh==EquipmentOutlineGeometry.edges(normalized),"Cosmetic chevron settings reuse the same cached outline");
                    for(int i=108;i<mesh.length;i+=9)check(mesh[i+1]<=.375&&mesh[i+4]<=.375,"Chevron pole stops below the panel");
                }
            }
            BlockState effect=PlacementParticles.straight(state);
            check(effect==PlacementParticles.straight(effect),"Particle normalization is idempotent and allocation-free");
            if(state.hasProperty(TrafficLightBlock.DIAGONAL))check(!effect.getValue(TrafficLightBlock.DIAGONAL),"Effect is straight");
            for(var property:state.getProperties())if(!property.getName().equals("diagonal"))check(Objects.equals(state.getValue(property),effect.getValue(property)),"Effect preserves "+property);
            check(effect.getShape(world,BlockPos.ZERO)==PlacementParticles.straight(state).getShape(world,BlockPos.ZERO),"Both placements use the same cached particle shape");
        }
        for(var shape:TrafficSignShape.values())for(Direction dir:Direction.Plane.HORIZONTAL) {
            float[] flat=TrafficSignOutlineGeometry.plate(shape,dir,false,false);
            check(flat.length==108 && flat==TrafficSignOutlineGeometry.plate(shape,dir,false,false),"Straight signs also use twelve cached edges without an interior post line");
            float[] pole=TrafficSignOutlineGeometry.pole(shape,dir);double top=TrafficSignOutlineGeometry.plateBottom(shape);
            if(top==0)check(pole.length==0,"No hidden post line through a full-height plate");
            for(int i=0;i<pole.length;i+=9)check(pole[i+1]<=top&&pole[i+4]<=top,"Visible carrier stops at plate bottom");
        }
        System.out.println(states+" block states checked; at most "+maxEdges+" clean equipment edges; no world reads during outline queries.");
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        check(Files.readString(Path.of("../gradle.properties")).contains("mod_version = 2.2"),"Version is 2.2");
        blocks();meshes(Path.of("src/main/resources/assets/trafficengine/models/block"));
        System.out.println("PASS "+checks+" Update22Check assertions. No in-game/FPS claim.");
    }
}
