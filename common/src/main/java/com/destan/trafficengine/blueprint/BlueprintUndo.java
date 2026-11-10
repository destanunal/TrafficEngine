package com.destan.trafficengine.blueprint;

import com.destan.trafficengine.util.ItemData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** One recent operation per player. Bounded RAM, no idle world scans or retained worlds. */
public final class BlueprintUndo {
    private BlueprintUndo(){}
    public static final int MAX_PLAYERS=8,MAX_HISTORY_BYTES=524288,MAX_CACHE_BYTES=2097152;
    private static final long LIFETIME_NANOS=10L*60*1_000_000_000;
    public record Cell(BlockPos pos,BlockState state,CompoundTag data){}
    public record Copy(List<Cell> cells,List<ItemStack> refund,int bytes,List<Cell> before){
        public Copy(List<Cell> cells,List<ItemStack> refund,int bytes){this(cells,refund,bytes,List.of());}
        public Copy{cells=List.copyOf(cells);refund=List.copyOf(refund);before=List.copyOf(before);}
    }
    private record History(String dimension,List<Copy> copies,int bytes,long expires){}
    private record Pending(ServerPlayer player,ItemStack tool,History history,int remaining){}
    private static final LinkedHashMap<UUID,History> HISTORY=new LinkedHashMap<>();
    private static final ArrayDeque<Pending> PENDING=new ArrayDeque<>();
    private static int cachedBytes;
    public static void clear(){HISTORY.clear();PENDING.clear();cachedBytes=0;}
    private static void forget(UUID id){History old=HISTORY.remove(id);if(old!=null)cachedBytes-=old.bytes;}
    public static void invalidate(UUID id){forget(id);}
    public static boolean busy(UUID id){return PENDING.stream().anyMatch(p->p.player.getUUID().equals(id));}
    public static CompoundTag snapshot(ServerLevel world,BlockPos pos){
        var entity=world.getBlockEntity(pos);return entity==null?null:entity.saveWithoutMetadata(world.registryAccess());
    }
    public static Copy capture(ServerLevel world,List<BlockPos> positions,List<ItemStack> refund){
        List<Cell> cells=new ArrayList<>(positions.size());int bytes=0;
        for(BlockPos pos:positions){CompoundTag data=snapshot(world,pos);bytes+=64+(data==null?0:data.sizeInBytes());
            if(bytes>MAX_HISTORY_BYTES)return null;
            cells.add(new Cell(pos,world.getBlockState(pos),data));}
        // Item custom data is already size-bounded at blueprint capture, but charge it too.
        for(ItemStack stack:refund){CompoundTag data=ItemData.get(stack);bytes+=64+(data==null?0:data.sizeInBytes());}
        return bytes>MAX_HISTORY_BYTES?null:new Copy(cells,refund,bytes);
    }
    public static Copy capture(ServerLevel world,List<BlockPos> positions,List<ItemStack> refund,List<Cell> before){
        Copy placed=capture(world,positions,refund);if(placed==null)return null;
        List<Cell> replaced=before.stream().filter(c->!c.state().isAir()).toList();
        int bytes=placed.bytes();for(Cell c:replaced)bytes+=64+(c.data()==null?0:c.data().sizeInBytes());
        return bytes>MAX_HISTORY_BYTES?null:new Copy(placed.cells(),refund,bytes,replaced);
    }
    public static void restore(ServerLevel world,List<Cell> cells){
        for(Cell c:cells){world.removeBlockEntity(c.pos());world.setBlock(c.pos(),Blocks.AIR.defaultBlockState(),2|16);}
        for(Cell c:cells)if(!c.state().isAir())world.setBlock(c.pos(),c.state(),2|16);
        for(Cell c:cells)if(world.getBlockState(c.pos())!=c.state())throw new IllegalStateException("Restore rejected");
        for(Cell c:cells)if(c.data()!=null){var entity=world.getBlockEntity(c.pos());if(entity==null)throw new IllegalStateException("Missing restored block entity");{entity.loadWithComponents(c.data().copy(),world.registryAccess());entity.setChanged();}}
    }
    public static void remember(UUID player,String dimension,List<Copy> copies){
        forget(player);int bytes=copies.stream().mapToInt(Copy::bytes).sum();
        if(copies.isEmpty()||bytes>MAX_HISTORY_BYTES)return;
        long now=System.nanoTime();
        var entries=HISTORY.entrySet().iterator();
        while(entries.hasNext()){var e=entries.next();if(e.getValue().expires<=now){cachedBytes-=e.getValue().bytes;entries.remove();}}
        while(HISTORY.size()>=MAX_PLAYERS||cachedBytes+bytes>MAX_CACHE_BYTES)forget(HISTORY.keySet().iterator().next());
        HISTORY.put(player,new History(dimension,List.copyOf(copies),bytes,now+LIFETIME_NANOS));cachedBytes+=bytes;
    }
    public static boolean unchanged(Cell cell,BlockState state,CompoundTag data){return cell.state==state&&Objects.equals(cell.data,data);}
    private static boolean valid(ServerPlayer player,ItemStack tool,Copy copy,boolean hooks){
        ServerLevel world=(ServerLevel)player.level();
        for(Cell c:copy.cells){
            if(!BlueprintPlacement.permitted(player,c.pos,tool,128)||!unchanged(c,world.getBlockState(c.pos),snapshot(world,c.pos))
                    ||hooks&&!BlueprintHooks.canRemove(player,c.pos,c.state))return false;
        }return true;
    }
    public static void start(ServerPlayer player,ItemStack tool){
        History history=HISTORY.get(player.getUUID());
        if(history==null||history.expires<=System.nanoTime()){forget(player.getUUID());BlueprintToolItem.message(player,"nothing_to_undo");return;}
        if(PENDING.size()>=8||busy(player.getUUID())){BlueprintToolItem.message(player,"busy");return;}
        if(!history.dimension.equals(player.level().dimension().location().toString())){BlueprintToolItem.message(player,"undo_blocked");return;}
        // Validate the whole operation before touching anything. Changed blocks and data survive.
        for(Copy copy:history.copies)if(!valid(player,tool,copy,false)){BlueprintToolItem.message(player,"undo_blocked");return;}
        forget(player.getUUID());PENDING.add(new Pending(player,tool,history,history.copies.size()));
    }
    /** Shares the construction budget: at most one complete copy (512 cells) per tick. */
    public static boolean tick(){
        Pending pending=PENDING.poll();if(pending==null)return false;
        ServerPlayer player=pending.player;History history=pending.history;Copy copy=history.copies.get(pending.remaining-1);
        if(player.isRemoved()||!player.isAlive()||!history.dimension.equals(player.level().dimension().location().toString())
                ||player.getMainHandItem()!=pending.tool&&player.getOffhandItem()!=pending.tool||!valid(player,pending.tool,copy,true)||!valid(player,pending.tool,copy,false)){
            remember(player.getUUID(),history.dimension,history.copies.subList(0,pending.remaining));BlueprintToolItem.message(player,"undo_blocked");return true;
        }
        ServerLevel world=(ServerLevel)player.level();
        // Suppress multi-block teardown, particles and drops; refund exactly what construction took.
        boolean removed=false;
        BlueprintPlacement.beginWriting();
        try{
            for(int i=copy.cells.size()-1;i>=0;i--){
                Cell c=copy.cells.get(i);world.removeBlockEntity(c.pos);
                if(!world.setBlock(c.pos,Blocks.AIR.defaultBlockState(),2|16))throw new IllegalStateException("Removal rejected");
            }
            restore(world,copy.before);
            removed=true;
        }catch(RuntimeException failure){
            // Restore the whole copy before any material refund if teardown fails.
            restore(world,copy.cells);
            com.destan.trafficengine.TrafficEngine.LOGGER.warn("Blueprint undo aborted: {}",failure.getClass().getSimpleName());
        }finally{BlueprintPlacement.endWriting();}
        if(!removed){remember(player.getUUID(),history.dimension,history.copies.subList(0,pending.remaining));BlueprintToolItem.message(player,"undo_blocked");return true;}
        for(Cell c:copy.cells){world.getBlockState(c.pos).updateNeighbourShapes(world,c.pos,2);world.updateNeighborsAt(c.pos,c.state.getBlock());}
        for(ItemStack original:copy.refund){ItemStack refund=original.copy();if(!player.getInventory().add(refund))player.drop(refund,false);}
        player.getInventory().setChanged();player.containerMenu.broadcastChanges();
        if(pending.remaining>1)PENDING.add(new Pending(player,pending.tool,history,pending.remaining-1));
        else BlueprintToolItem.message(player,"undone");
        return true;
    }
}
