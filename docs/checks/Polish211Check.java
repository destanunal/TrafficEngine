import com.destan.trafficengine.block.data.*;
import com.destan.trafficengine.client.lighting.*;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.world.phys.shapes.*;
import java.nio.file.*;
import java.util.*;

/** Native model/shape checks; no game launch or packaging. */
public final class Polish211Check {
    static int checks,hardwareFaces;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static void close(double a,double b,String why){check(Math.abs(a-b)<2e-5,why+": "+a+" != "+b);}
    static JsonObject read(Path p)throws Exception{return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
    static double[] xyz(JsonArray a){return new double[]{a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()};}
    static double[] modelPoint(double[] p,JsonObject rotation,Direction facing) {
        double x=p[0],y=p[1],z=p[2];
        if(rotation!=null) {
            double[] o=xyz(rotation.getAsJsonArray("origin"));double angle=Math.toRadians(rotation.get("angle").getAsDouble()),c=Math.cos(angle),s=Math.sin(angle);
            if(rotation.get("axis").getAsString().equals("x")) {double dy=y-o[1],dz=z-o[2];y=o[1]+c*dy-s*dz;z=o[2]+s*dy+c*dz;}
            else {double dx=x-o[0],dz=z-o[2];x=o[0]+c*dx+s*dz;z=o[2]-s*dx+c*dz;}
        }
        double dx=x-8,dz=z-8;
        switch(facing){case EAST->{x=8-dz;z=8+dx;}case SOUTH->{x=8-dx;z=8-dz;}case WEST->{x=8+dz;z=8-dx;}default->{}}
        return new double[]{x/16,y/16,z/16};
    }
    static void outline(Path models)throws Exception {
        for(TrafficSignShape shape:TrafficSignShape.values())for(Direction facing:Direction.Plane.HORIZONTAL)for(boolean tilted:new boolean[]{false,true}) {
            if(shape==TrafficSignShape.MISC) {
                float[] dynamic=TrafficSignOutlineGeometry.plate(shape,facing,tilted);
                check(dynamic.length==108 && dynamic==TrafficSignOutlineGeometry.plate(shape,facing,tilted),"Dynamic-image signs also use twelve cached outline edges");
                continue; // dynamic image has no authored plate asset
            }
            String path=(tilted?"sign_post_mounted/straight/":"sign/diagonal/")+shape.getSerializedName()+".json";
            JsonArray elements=read(models.resolve(path)).getAsJsonArray("elements");
            double[] expected={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            for(JsonElement raw:elements) {
                JsonObject plate=raw.getAsJsonObject();
                double[] a=xyz(plate.getAsJsonArray("from")),b=xyz(plate.getAsJsonArray("to"));
                if(!tilted && Math.abs(a[2]-6.5)>1e-6)continue; // exclude carrier and clamps
                for(int i=0;i<8;i++) {
                    double[] p=modelPoint(new double[]{(i&1)==0?a[0]:b[0],(i&2)==0?a[1]:b[1],(i&4)==0?a[2]:b[2]},plate.getAsJsonObject("rotation"),facing);
                    for(int axis=0;axis<3;axis++){expected[axis]=Math.min(expected[axis],p[axis]);expected[axis+3]=Math.max(expected[axis+3],p[axis]);}
                }
            }
            float[] edges=TrafficSignOutlineGeometry.plate(shape,facing,tilted);
            check(edges==TrafficSignOutlineGeometry.plate(shape,facing,tilted),"Outline is cached");check(edges.length==108,"Exactly twelve plate edges");
            double[] actual={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            for(int i=0;i<edges.length;i+=9)for(int endpoint=0;endpoint<2;endpoint++)for(int axis=0;axis<3;axis++){
                float value=edges[i+endpoint*3+axis];actual[axis]=Math.min(actual[axis],value);actual[axis+3]=Math.max(actual[axis+3],value);
            }
            for(int axis=0;axis<6;axis++)close(actual[axis],expected[axis],"Outline fits actual baked plate "+path+" "+facing+" axis "+axis);
        }
        var fresh=TrafficSignShape.class.getDeclaredMethod("createVoxelShape",Direction.class);fresh.setAccessible(true);
        for(TrafficSignShape shape:TrafficSignShape.values())for(Direction facing:Direction.Plane.HORIZONTAL){
            var cached=shape.getVoxelShape(facing);check(cached==shape.getVoxelShape(facing),"Shared static shape");
            check(!Shapes.joinIsNotEmpty(cached,(VoxelShape)fresh.invoke(shape,facing),net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),"Picking/collision volume is unchanged");
        }
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        if(bean.isThreadAllocatedMemorySupported()){
            bean.setThreadAllocatedMemoryEnabled(true);
            for(int i=0;i<10000;i++)TrafficSignShape.SQUARE.getVoxelShape(Direction.NORTH);
            long thread=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(thread);int count=0;
            for(int i=0;i<100000;i++)if(TrafficSignShape.SQUARE.getVoxelShape(Direction.NORTH)!=null)count++;
            long bytes=bean.getThreadAllocatedBytes(thread)-before;check(count==100000,"Shape lookup count");
            System.out.println("Cached shape lookups: "+count+"; measured thread allocation: "+bytes+" bytes (not an FPS measurement).");
        }
    }
    static void hardware(Path models)throws Exception {
        try(var files=Files.list(models)){
            for(Path p:files.filter(x->x.toString().contains("direction_sign")&&x.toString().endsWith(".json")).toList()){
                JsonObject model=read(p);
                for(JsonElement raw:model.getAsJsonArray("elements")){
                    JsonObject e=raw.getAsJsonObject();String name=e.has("name")?e.get("name").getAsString():"";
                    if(name.contains("panel"))continue;
                    if(!e.getAsJsonObject("faces").entrySet().stream().allMatch(x->x.getValue().getAsJsonObject().getAsJsonObject("trafficengine_data")!=null))continue;
                    double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
                    for(var entry:e.getAsJsonObject("faces").entrySet()){
                        JsonArray v=entry.getValue().getAsJsonObject().getAsJsonObject("trafficengine_data").getAsJsonArray("vertices");
                        for(int i=0;i<12;i++) {int a=i%3;double value=v.get(i).getAsDouble();min[a]=Math.min(min[a],value);max[a]=Math.max(max[a],value);}
                    }
                    double[] coordinates={min[1],max[1],min[2],max[2],min[0],max[0]};
                    for(var entry:e.getAsJsonObject("faces").entrySet()) {
                        JsonObject f=entry.getValue().getAsJsonObject();Direction side=Direction.byName(entry.getKey());
                        JsonArray v=f.getAsJsonObject("trafficengine_data").getAsJsonArray("vertices"),uv=f.getAsJsonArray("uv");
                        BlockFaceUV texture=new BlockFaceUV(new float[]{uv.get(0).getAsFloat(),uv.get(1).getAsFloat(),uv.get(2).getAsFloat(),uv.get(3).getAsFloat()},f.has("rotation")?f.get("rotation").getAsInt():0);
                        for(int i=0;i<4;i++) {
                            var info=FaceInfo.fromFacing(side).getVertexInfo(i);
                            close(v.get(i*3).getAsDouble(),coordinates[info.xFace],"Pole U corner X "+p.getFileName()+" "+name+" "+side);
                            close(v.get(i*3+1).getAsDouble(),coordinates[info.yFace],"Pole V corner Y");
                            close(v.get(i*3+2).getAsDouble(),coordinates[info.zFace],"Pole corner Z");
                            int j=(i+1)%4;double distance=0;
                            for(int a=0;a<3;a++){double d=v.get(i*3+a).getAsDouble()-v.get(j*3+a).getAsDouble();distance+=d*d;}
                            double du=texture.getU(i)-texture.getU(j),dv=texture.getV(i)-texture.getV(j);
                            close(Math.sqrt(distance),Math.sqrt(du*du+dv*dv),"Physical pole UV scale");
                        }
                        hardwareFaces++;
                    }
                }
            }
        }
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        Path models=Path.of("src/main/resources/assets/trafficengine/models/block");outline(models);hardware(models);
        System.out.println("PASS "+checks+" checks; twelve-edge exact outlines; unchanged picking volumes; "+hardwareFaces+" faces match native pole UV corner order and physical texture scale.");
    }
}
