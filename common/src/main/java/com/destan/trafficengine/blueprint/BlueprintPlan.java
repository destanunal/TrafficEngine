package com.destan.trafficengine.blueprint;

import com.destan.trafficengine.block.*;
import com.destan.trafficengine.block.entity.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import java.util.*;

/** Immutable, bounded palette. No world references, entities, inventory data or ticking state. */
public record BlueprintPlan(String name, BlockPos size, BlockPos source, String dimension, List<Cell> cells) {
    public static final int MAX_EDGE=32, MAX_VOLUME=4096, MAX_BLOCKS=512, MAX_TOTAL=4096, SLOTS=1, MAX_BYTES=196608, MAX_COPIES=32, MAX_SPACING=32;
    public record Cell(BlockPos pos, BlockState state, CompoundTag data) {}
    public BlueprintPlan { cells=List.copyOf(cells); }
    public static BlockPos rotate(BlockPos p,BlockPos size,int turns) {
        return switch(turns&3) {
            case 1 -> new BlockPos(size.getZ()-1-p.getZ(),p.getY(),p.getX());
            case 2 -> new BlockPos(size.getX()-1-p.getX(),p.getY(),size.getZ()-1-p.getZ());
            case 3 -> new BlockPos(p.getZ(),p.getY(),size.getX()-1-p.getX());
            default -> p;
        };
    }
    /** Spacing means empty block cells between occupied footprints, after rotation. */
    public static int repeatStep(List<Cell> cells,Direction direction,int gap){
        if(cells.isEmpty())throw new IllegalArgumentException("Occupied footprint required");
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        for(Cell cell:cells){int coordinate=direction.getAxis().choose(cell.pos.getX(),cell.pos.getY(),cell.pos.getZ());min=Math.min(min,coordinate);max=Math.max(max,coordinate);}
        return max-min+1+net.minecraft.util.Mth.clamp(gap,0,MAX_SPACING);
    }
    public static Rotation rotation(int turns){return Rotation.values()[turns&3];}
    public record Layout(List<Cell> cells,Map<BlockPos,BlockPos> destinations){}
    public List<Cell> rotated(int turns) { return layout(turns).cells; }
    public Layout layout(int turns) {
        Map<BlockPos,Cell> out=new LinkedHashMap<>();Map<BlockPos,BlockPos> destinations=new HashMap<>();
        for(Cell c:cells){BlockPos target=rotate(c.pos,size,turns);destinations.put(c.pos,target);out.put(target,new Cell(target,rotateState(c.state,turns),c.data));}
        // Displays use a positive-axis width anchor. A quarter turn may move the master
        // to the opposite edge while keeping the exact same occupied footprint.
        for(Cell c:cells)if(c.state.getBlock() instanceof LedDeviceBlock led&&!led.blueprintParts(c.pos,c.state).isEmpty()){
            Set<BlockPos> footprint=new HashSet<>();footprint.add(rotate(c.pos,size,turns));for(BlockPos p:led.blueprintParts(c.pos,c.state))footprint.add(rotate(p,size,turns));
            BlockState state=rotateState(c.state,turns);BlockPos master=null;
            for(BlockPos candidate:footprint){Set<BlockPos> expected=new HashSet<>(led.blueprintParts(candidate,state));expected.add(candidate);if(expected.equals(footprint)){master=candidate;break;}}
            if(master==null)throw new IllegalArgumentException("Unrepresentable display rotation");
            for(BlockPos p:footprint)out.remove(p);destinations.put(c.pos,master);out.put(master,new Cell(master,state,c.data));
            Direction facing=state.getValue(LedDeviceBlock.FACING),width=facing.getAxis()==Direction.Axis.Z?Direction.EAST:Direction.SOUTH;
            for(BlockPos part:led.blueprintParts(master,state)){
                int y=part.getY()-master.getY(),offset=(part.getX()-master.getX())*width.getStepX()+(part.getZ()-master.getZ())*width.getStepZ();
                BlockState helper=cells.stream().filter(partCell->partCell.state.getBlock() instanceof LedDevicePartBlock).findFirst().orElseThrow().state.getBlock().defaultBlockState()
                    .setValue(LedDevicePartBlock.FACING,facing).setValue(LedDevicePartBlock.UPPER,y!=0).setValue(LedDevicePartBlock.ROW,0)
                    .setValue(LedDevicePartBlock.VERTICAL_OFFSET,y+1).setValue(LedDevicePartBlock.SIDE,offset!=0).setValue(LedDevicePartBlock.OFFSET,offset+1)
                    .setValue(LedDevicePartBlock.CENTERED,state.getValue(LedDeviceBlock.POST_MOUNTED)&&!state.getValue(LedDeviceBlock.WALL_MOUNTED)||led.getDeviceType()==com.destan.trafficengine.block.data.LedDeviceType.LARGE_TRAFFIC_DISPLAY)
                    .setValue(LedDevicePartBlock.HANGING,state.getValue(LedDeviceBlock.POST_ABOVE)).setValue(LedDevicePartBlock.WALL_MOUNTED,state.getValue(LedDeviceBlock.WALL_MOUNTED));
                out.put(part,new Cell(part,helper,null));
            }
        }
        return new Layout(List.copyOf(out.values()),Map.copyOf(destinations));
    }
    public static BlockState rotateState(BlockState state,int turns){
        Rotation r=rotation(turns);BlockState out=state.rotate(r);
        if(state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))out=out.setValue(BlockStateProperties.HORIZONTAL_FACING,r.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
        if(state.hasProperty(BlockStateProperties.FACING))out=out.setValue(BlockStateProperties.FACING,r.rotate(state.getValue(BlockStateProperties.FACING)));
        if(state.getBlock() instanceof TrafficSignPostBlock){
            for(Direction d:Direction.Plane.HORIZONTAL){
                var from=net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(d);
                var to=net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(r.rotate(d));out=out.setValue(to,state.getValue(from));
            }
        }return out;
    }
    public CompoundTag save() {
        CompoundTag tag=new CompoundTag();tag.putString("Name",name);tag.putLong("Size",size.asLong());tag.putLong("Source",source.asLong());tag.putString("Dimension",dimension);
        Map<BlockState,Integer> palette=new LinkedHashMap<>();ListTag entries=new ListTag(),states=new ListTag();
        for(Cell c:cells){int id=palette.computeIfAbsent(c.state,k->palette.size());CompoundTag e=new CompoundTag();e.putLong("P",c.pos.asLong());e.putInt("S",id);if(c.data!=null)e.put("Data",c.data.copy());entries.add(e);}
        for(BlockState state:palette.keySet())states.add(NbtUtils.writeBlockState(state));
        tag.put("Palette",states);tag.put("Cells",entries);return tag;
    }
    public static BlueprintPlan load(CompoundTag tag) {
        BlockPos size=BlockPos.of(tag.getLong("Size"));
        if(!validSize(size))return null;
        ListTag states=tag.getList("Palette",Tag.TAG_COMPOUND),entries=tag.getList("Cells",Tag.TAG_COMPOUND);
        if(entries.isEmpty()||entries.size()>MAX_BLOCKS||states.isEmpty()||states.size()>MAX_BLOCKS)return null;
        List<BlockState> palette=new ArrayList<>();
        for(int i=0;i<states.size();i++)palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),states.getCompound(i)));
        List<Cell> cells=new ArrayList<>();Set<BlockPos> seen=new HashSet<>();
        for(int i=0;i<entries.size();i++) {
            CompoundTag e=entries.getCompound(i);BlockPos p=BlockPos.of(e.getLong("P"));int id=e.getInt("S");
            if(!inside(p,size)||!seen.add(p)||id<0||id>=palette.size()||palette.get(id).isAir())return null;
            cells.add(new Cell(p,palette.get(id),e.contains("Data",Tag.TAG_COMPOUND)?e.getCompound("Data").copy():null));
        }
        String name=tag.getString("Name");return new BlueprintPlan(name.substring(0,Math.min(32,name.length())),size,BlockPos.of(tag.getLong("Source")),tag.getString("Dimension"),cells);
    }
    public static boolean validSize(BlockPos size) {
        return size.getX()>0&&size.getY()>0&&size.getZ()>0&&size.getX()<=MAX_EDGE&&size.getY()<=MAX_EDGE&&size.getZ()<=MAX_EDGE
                &&(long)size.getX()*size.getY()*size.getZ()<=MAX_VOLUME;
    }
    public static Item material(BlockState state) {
        Item item=state.getBlock().asItem();
        if(item==Items.AIR && state.getBlock() instanceof com.destan.trafficengine.block.data.RoadBlock road)
            return (state.getBlock() instanceof PaintedAsphaltSlope ? road.getDefaultRoadType().getSlope() : road.getDefaultRoadType().getBlock()).asItem();
        return item;
    }
    public static boolean inside(BlockPos p,BlockPos size){return p.getX()>=0&&p.getY()>=0&&p.getZ()>=0&&p.getX()<size.getX()&&p.getY()<size.getY()&&p.getZ()<size.getZ();}
    public static boolean allowedData(BlockEntity e) {
        return e instanceof TrafficSignBlockEntity || e instanceof WritableTrafficSignBlockEntity
            || e instanceof RoadInformationSignBlockEntity || e instanceof RailingPaintBlockEntity
            || e instanceof ColoredBlockEntity || e instanceof StreetLampBlockEntity
            || e instanceof TrafficLightBlockEntity || e instanceof TrafficLightControllerBlockEntity
            || e instanceof TrafficLightRequestButtonBlockEntity || e instanceof LedDeviceBlockEntity;
    }
    /** One item per actual placed assembly, never one per invisible carrier cell. */
    public static int cost(BlockState state) {
        if(state.getBlock() instanceof CurvedTrafficPoleBlock)return state.getValue(CurvedTrafficPoleBlock.PART)==0?1:0;
        if(state.getBlock() instanceof LedDevicePartBlock)return 0;
        if(state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)&&state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER)return 0;
        if(state.hasProperty(BlockStateProperties.BED_PART)&&state.getValue(BlockStateProperties.BED_PART)==BedPart.HEAD)return 0;
        if(state.hasProperty(BlockStateProperties.SLAB_TYPE)&&state.getValue(BlockStateProperties.SLAB_TYPE)==SlabType.DOUBLE)return 2;
        if(state.getBlock() instanceof AsphaltSlope || state.getBlock() instanceof PaintedAsphaltSlope)return state.getValue(AsphaltSlope.LAYERS);
        if(state.getBlock() instanceof SnowLayerBlock)return state.getValue(SnowLayerBlock.LAYERS);
        return 1;
    }
    public Map<Item,Integer> materials(int copies) {return materials(cells,copies);}
    public static Map<Item,Integer> materials(List<Cell> cells,int copies) {
        Map<Item,Integer> out=new LinkedHashMap<>();for(Cell c:cells){int cost=cost(c.state);if(cost>0)out.merge(material(c.state),cost*copies,Integer::sum);}return out;
    }
    /** Remap only internal WorldLocation records. External links are deliberately detached. */
    public static CompoundTag remap(CompoundTag input,BlueprintPlan plan,Map<BlockPos,BlockPos> saved,BlockPos anchor,int turns,String dimension) {
        CompoundTag tag=input.copy();remapChildren(tag,plan,saved,anchor,turns,dimension);return tag;
    }
    private static boolean location(CompoundTag t){return t.contains("dimension",Tag.TAG_STRING)&&t.contains("x",Tag.TAG_ANY_NUMERIC)&&t.contains("y",Tag.TAG_ANY_NUMERIC)&&t.contains("z",Tag.TAG_ANY_NUMERIC);}
    private static boolean mapLocation(CompoundTag t,BlueprintPlan plan,Map<BlockPos,BlockPos> saved,BlockPos anchor,int turns,String dim){
        if(!t.getString("dimension").equals(plan.dimension))return false;
        BlockPos relative=new BlockPos((int)t.getDouble("x"),(int)t.getDouble("y"),(int)t.getDouble("z")).subtract(plan.source);
        if(!saved.containsKey(relative))return false;
        BlockPos p=saved.get(relative).offset(anchor);t.putDouble("x",p.getX());t.putDouble("y",p.getY());t.putDouble("z",p.getZ());t.putString("dimension",dim);return true;
    }
    private static void remapChildren(CompoundTag tag,BlueprintPlan p,Map<BlockPos,BlockPos> saved,BlockPos anchor,int turns,String dim){
        for(String key:new ArrayList<>(tag.getAllKeys())){
            Tag child=tag.get(key);
            if(child instanceof CompoundTag c){if(location(c)){if(!mapLocation(c,p,saved,anchor,turns,dim))tag.remove(key);}else remapChildren(c,p,saved,anchor,turns,dim);}
            else if(child instanceof ListTag list)for(int i=list.size()-1;i>=0;i--)if(list.get(i) instanceof CompoundTag c){if(location(c)){if(!mapLocation(c,p,saved,anchor,turns,dim))list.remove(i);}else remapChildren(c,p,saved,anchor,turns,dim);}
        }
    }
}
