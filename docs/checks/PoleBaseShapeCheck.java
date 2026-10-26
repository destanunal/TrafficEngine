import com.destan.trafficengine.block.TrafficPoleBaseBlock;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.*;

/** Native collision/selection checks against the JSON model used in the world and inventory. */
public final class PoleBaseShapeCheck {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static double n(JsonArray a,int i){return a.get(i).getAsDouble();}
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();Update20RegressionCheck.headlessIcons();
        var base=(TrafficPoleBaseBlock)Update20RegressionCheck.equipment()[3];
        Path assets=Path.of(args[0]).resolve("common/src/main/resources/assets/trafficengine");
        var variants=JsonParser.parseString(Files.readString(assets.resolve("blockstates/traffic_pole_base.json"))).getAsJsonObject().getAsJsonObject("variants");
        var item=JsonParser.parseString(Files.readString(assets.resolve("models/item/traffic_pole_base.json"))).getAsJsonObject();
        Set<VoxelShape> cached=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var state:base.getStateDefinition().getPossibleStates()){
            int sink=state.getValue(TrafficPoleBaseBlock.SINK);
            String selector="connected="+state.getValue(TrafficPoleBaseBlock.CONNECTED)+",sink="+sink;
            String model=variants.getAsJsonObject(selector).get("model").getAsString();
            if(sink==0)check(model.equals(item.get("parent").getAsString()),"Standalone world model matches inventory pole model");
            var json=JsonParser.parseString(Files.readString(assets.resolve("models/"+model.split(":")[1]+".json"))).getAsJsonObject();
            VoxelShape expected=Shapes.empty();
            for(var element:json.getAsJsonArray("elements")){
                var e=element.getAsJsonObject();var from=e.getAsJsonArray("from");var to=e.getAsJsonArray("to");
                expected=Shapes.or(expected,Block.box(n(from,0),n(from,1),n(from,2),n(to,0),n(to,1),n(to,2)));
                if(e.get("name").getAsString().equals("foundation"))
                    check(n(to,0)-n(from,0)==7&&n(to,2)-n(from,2)==7&&n(to,1)-n(from,1)==5,"Concrete footprint and height are slightly reduced at every embedding depth");
            }
            var actual=base.getShape(state,null,BlockPos.ZERO,CollisionContext.empty());cached.add(actual);
            check(!Shapes.joinIsNotEmpty(actual,expected,BooleanOp.NOT_SAME),"Native selection shape matches the visible model");
            check(actual==base.getCollisionShape(state,null,BlockPos.ZERO,CollisionContext.empty()),"Collision reuses selection shape");
            check(actual==base.getShape(state,null,BlockPos.ZERO,CollisionContext.empty()),"Queries reuse cached geometry");
            check(actual.max(Direction.Axis.Y)==1&&actual.min(Direction.Axis.Y)==-sink/8.0,"Pole height and buried depth remain unchanged");
        }
        check(cached.size()==9,"Nine bounded shapes shared by all 36 states");
        System.out.println("PASS: "+checks+" pole base assertions: inventory/world parity, reduced concrete dimensions, native collision/model equivalence, all embedding depths and nine cached shapes.");
    }
}
