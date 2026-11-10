package com.destan.trafficengine.blueprint;

import com.destan.trafficengine.block.*;
import com.destan.trafficengine.util.ItemData;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.*;

/** Server-authored plans only. One complete copy per server tick; bounded global queue. */
public final class BlueprintPlacement {
    private BlueprintPlacement(){}
    private static final ArrayDeque<Job> JOBS=new ArrayDeque<>();
    private static final ThreadLocal<Boolean> WRITING=ThreadLocal.withInitial(()->false);
    public static boolean writing(){return WRITING.get();}
    static void beginWriting(){WRITING.set(true);}
    static void endWriting(){WRITING.remove();}
    public static void init(){
        TickEvent.SERVER_POST.register(server->{
            if(BlueprintUndo.tick())return;
            Job job=JOBS.poll();if(job!=null){if(job.tick())JOBS.add(job);else job.finish();}
        });
        LifecycleEvent.SERVER_STOPPED.register(server->{JOBS.clear();BlueprintUndo.clear();});
    }
    public static void capture(ServerPlayer player,ItemStack tool){
        CompoundTag tag=BlueprintToolItem.data(tool);
        if(!player.mayBuild()||!tag.contains("First")||!tag.contains("Second")||!tag.getString("SelectionDimension").equals(player.level().dimension().location().toString())){BlueprintToolItem.message(player,"select_first");return;}
        BlockPos a=BlockPos.of(tag.getLong("First")),b=BlockPos.of(tag.getLong("Second"));
        BlockPos min=new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()));
        BlockPos size=new BlockPos(Math.abs(a.getX()-b.getX())+1,Math.abs(a.getY()-b.getY())+1,Math.abs(a.getZ()-b.getZ())+1);
        if(!BlueprintPlan.validSize(size)){BlueprintToolItem.message(player,"too_large");return;}
        List<BlueprintPlan.Cell> cells=new ArrayList<>();
        for(BlockPos p:BlockPos.betweenClosed(min,min.offset(size).offset(-1,-1,-1))){
            if(!permitted(player,p,tool,128)){BlueprintToolItem.message(player,"blocked");return;}
            BlockState state=player.level().getBlockState(p);if(state.isAir())continue;
            var entity=player.level().getBlockEntity(p);
            if(state.getDestroySpeed(player.level(),p)<0||state.hasBlockEntity()&&entity==null||entity!=null&&!BlueprintPlan.allowedData(entity)
                    ||BlueprintPlan.cost(state)>0&&BlueprintPlan.material(state)==Items.AIR){BlueprintToolItem.message(player,"unsupported");return;}
            // Water is not an inventory material. Never manufacture it from saved waterlogging.
            if(state.hasProperty(BlockStateProperties.WATERLOGGED))state=state.setValue(BlockStateProperties.WATERLOGGED,false);
            if(state.hasProperty(BlockStateProperties.POWERED))state=state.setValue(BlockStateProperties.POWERED,false);
            CompoundTag data=entity==null?null:entity.saveWithoutMetadata(player.registryAccess());
            if(data!=null){data.remove("x");data.remove("y");data.remove("z");data.remove("id");}
            cells.add(new BlueprintPlan.Cell(p.subtract(min),state,data));
            if(cells.size()>BlueprintPlan.MAX_BLOCKS){BlueprintToolItem.message(player,"too_large");return;}
        }
        if(cells.isEmpty()){BlueprintToolItem.message(player,"empty");return;}
        if(!complete(cells)){BlueprintToolItem.message(player,"incomplete");return;}
        String clean="Blueprint";
        BlueprintPlan plan=new BlueprintPlan(clean,size,min,player.level().dimension().location().toString(),cells);
        tag.put("Plan"+BlueprintToolItem.slot(tag),plan.save());tag.putInt("Revision",tag.getInt("Revision")+1);tag.putBoolean("Placing",true);
        if(tag.sizeInBytes()>BlueprintPlan.MAX_BYTES){BlueprintToolItem.message(player,"too_large");return;}
        ItemData.set(tool,tag);BlueprintToolItem.message(player,"ready",cells.size());
    }
    /** Require all free helper cells and their paid master to occur in the same plan. */
    public static boolean complete(List<BlueprintPlan.Cell> cells){
        Map<BlockPos,BlockState> states=new HashMap<>();for(var c:cells)states.put(c.pos(),c.state());
        for(var c:cells){BlockState s=c.state();BlockPos p=c.pos();
            if(s.getBlock() instanceof CurvedTrafficPoleBlock){
                BlockPos origin=CurvedTrafficPoleBlock.masterPos(p,s);
                for(int i=0;i<CurvedTrafficPoleBlock.partCount();i++){
                    var other=states.get(CurvedTrafficPoleBlock.partPos(origin,s.getValue(CurvedTrafficPoleBlock.FACING),i));
                    if(other==null||!other.is(s.getBlock())||other.getValue(CurvedTrafficPoleBlock.PART)!=i||other.getValue(CurvedTrafficPoleBlock.FACING)!=s.getValue(CurvedTrafficPoleBlock.FACING))return false;
                }
            }
            if(s.getBlock() instanceof LedDeviceBlock led)for(BlockPos part:led.blueprintParts(p,s))if(!(states.getOrDefault(part,Blocks.AIR.defaultBlockState()).getBlock() instanceof LedDevicePartBlock))return false;
            if(s.getBlock() instanceof LedDevicePartBlock){BlockPos root=LedDevicePartBlock.masterPos(p,s);BlockState master=states.get(root);if(master==null||!(master.getBlock() instanceof LedDeviceBlock led)||!led.blueprintParts(root,master).contains(p))return false;}
            if(s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)){
                boolean upper=s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER;var other=states.get(upper?p.below():p.above());
                if(other==null||!other.is(s.getBlock())||other.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF))return false;
            }
            if(s.hasProperty(BlockStateProperties.BED_PART)){
                var direction=s.getValue(BlockStateProperties.HORIZONTAL_FACING);boolean head=s.getValue(BlockStateProperties.BED_PART)==BedPart.HEAD;
                var other=states.get(p.relative(head?direction.getOpposite():direction));if(other==null||!other.is(s.getBlock())||other.getValue(BlockStateProperties.BED_PART)==s.getValue(BlockStateProperties.BED_PART))return false;
            }
        }return true;
    }
    public static boolean permitted(ServerPlayer player,BlockPos pos,ItemStack tool,int reach){
        var world=player.level();return player.mayBuild()&&!player.isSpectator()&&world.isLoaded(pos)&&world.isInWorldBounds(pos)
            &&world.getWorldBorder().isWithinBounds(pos)&&player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=reach*(double)reach
            &&world.mayInteract(player,pos)&&player.mayUseItemAt(pos,Direction.UP,tool);
    }
    public static void start(ServerPlayer player,ItemStack tool,BlockPos anchor){
        if(player.getCooldowns().isOnCooldown(tool.getItem()))return;
        if(JOBS.size()>=8||BlueprintUndo.busy(player.getUUID())||JOBS.stream().anyMatch(j->j.player==player)){BlueprintToolItem.message(player,"busy");return;}
        CompoundTag tag=BlueprintToolItem.data(tool);BlueprintPlan plan=BlueprintToolItem.selected(tag);int copies=BlueprintToolItem.copies(tag);
        if(plan==null||plan.cells().size()*copies>BlueprintPlan.MAX_TOTAL||!complete(plan.cells())){BlueprintToolItem.message(player,"too_large");return;}
        if(!permitted(player,anchor,tool,10)){BlueprintToolItem.message(player,"blocked");return;}
        Job job=new Job(player,tool,plan,anchor,tag.getInt("Rotation")&3,copies,BlueprintToolItem.spacing(tag),BlueprintToolItem.direction(tag.getInt("Direction")),BlueprintToolItem.mode(tag));
        Set<BlockPos> seen=new HashSet<>();Map<Item,Integer> bill=new LinkedHashMap<>();
        for(int copy=0;copy<copies;copy++){
            var work=job.prepare(copy,false);if(work==null){BlueprintToolItem.message(player,"blocked");return;}
            for(var cell:job.cells)if(!seen.add(cell.pos().offset(job.anchor(copy)))){BlueprintToolItem.message(player,"blocked");return;}
            BlueprintPlan.materials(work.stream().map(Work::cell).toList(),1).forEach((item,count)->bill.merge(item,count,Integer::sum));
        }
        if(!player.isCreative()&&!enough(player,bill)){BlueprintToolItem.message(player,"materials_missing");return;}
        JOBS.add(job);player.getCooldowns().addCooldown(tool.getItem(),20);BlueprintToolItem.message(player,"started",copies);
    }
    public static void undo(ServerPlayer player,ItemStack tool){
        var jobs=JOBS.iterator();
        while(jobs.hasNext()){Job job=jobs.next();if(job.player==player){jobs.remove();job.finish();}}
        BlueprintUndo.start(player,tool);
    }
    private static boolean enough(ServerPlayer player,Map<Item,Integer> bill){
        for(var entry:bill.entrySet()){int count=0;for(ItemStack s:player.getInventory().items)if(s.is(entry.getKey())&&s.getCount()>0)count+=s.getCount();if(count<entry.getValue())return false;}return true;
    }
    private static List<ItemStack> consume(ServerPlayer player,Map<Item,Integer> bill){
        List<ItemStack> consumed=new ArrayList<>();
        for(var entry:bill.entrySet()){int remaining=entry.getValue();for(ItemStack s:player.getInventory().items)if(s.is(entry.getKey())){int used=Math.min(remaining,s.getCount());if(used>0)consumed.add(s.split(used));remaining-=used;if(remaining==0)break;}}
        player.getInventory().setChanged();player.containerMenu.broadcastChanges();return consumed;
    }
    private record Work(BlueprintPlan.Cell cell,BlueprintUndo.Cell before){}
    /** Pure policy: 0 stops at occupied cells, 1 skips them, 2 replaces them. */
    public static boolean skipped(int mode,BlockState existing,BlockState desired){return mode==1&&!existing.isAir()||existing==desired&&!existing.hasBlockEntity();}
    private static final class Job {
        final ServerPlayer player;final ServerLevel world;final ItemStack tool;final BlueprintPlan plan;final BlockPos origin;final int turns,copies,spacing,mode;final Direction direction;
        final List<BlueprintUndo.Copy> undoCopies=new ArrayList<>();boolean undoable=true;int undoBytes;
        final List<BlueprintPlan.Cell> cells;final Map<BlockPos,BlockPos> saved;int index;
        Job(ServerPlayer player,ItemStack tool,BlueprintPlan plan,BlockPos anchor,int turns,int copies,int spacing,Direction direction,int mode){
            this.player=player;world=(ServerLevel)player.level();this.tool=tool;this.plan=plan;origin=anchor;this.turns=turns;this.copies=copies;this.direction=direction;this.mode=mode;
            var layout=plan.layout(turns);cells=layout.cells();this.spacing=BlueprintPlan.repeatStep(cells,direction,spacing);saved=layout.destinations();
        }
        void finish(){if(!undoCopies.isEmpty()&&undoable)BlueprintUndo.remember(player.getUUID(),world.dimension().location().toString(),undoCopies);}
        BlockPos anchor(int copy){return origin.relative(direction,spacing*copy);}
        List<Work> prepare(int copy,boolean hooks){
            BlockPos anchor=anchor(copy);List<Work> work=new ArrayList<>();List<BlueprintPlan.Cell> previous=new ArrayList<>(),selected=new ArrayList<>();int bytes=0;
            for(var c:cells){
                BlockPos p=c.pos().offset(anchor);if(!permitted(player,p,tool,128))return null;
                BlockState old=world.getBlockState(p);
                if(mode==0&&!old.isAir())return null;
                if(skipped(mode,old,c.state()))continue;
                var entity=world.getBlockEntity(p);
                if(!old.isAir()&&(old.getDestroySpeed(world,p)<0||entity!=null&&!BlueprintPlan.allowedData(entity)||old.hasBlockEntity()&&entity==null))return null;
                if(!world.isUnobstructed(c.state(),p,CollisionContext.empty())||hooks&&!old.isAir()&&!BlueprintHooks.canRemove(player,p,old))return null;
                CompoundTag data=BlueprintUndo.snapshot(world,p);bytes+=64+(data==null?0:data.sizeInBytes());
                if(bytes>BlueprintUndo.MAX_HISTORY_BYTES)return null;
                work.add(new Work(c,new BlueprintUndo.Cell(p,old,data)));selected.add(c);
                if(!old.isAir())previous.add(new BlueprintPlan.Cell(c.pos(),old,data));
            }
            // Never leave half a door, display or curved-pole assembly behind.
            return complete(selected)&&complete(previous)?work:null;
        }
        boolean tick(){
            if(player.isRemoved()||!player.isAlive()||player.level()!=world||world.getServer().getPlayerList().getPlayer(player.getUUID())!=player||player.getMainHandItem()!=tool&&player.getOffhandItem()!=tool){BlueprintToolItem.message(player,"stopped",index);return false;}
            var work=prepare(index,true);if(work==null){BlueprintToolItem.message(player,"stopped",index);return false;}
            BlockPos anchor=anchor(index);var bill=BlueprintPlan.materials(work.stream().map(Work::cell).toList(),1);
            if(!player.isCreative()&&!enough(player,bill)){BlueprintToolItem.message(player,"materials_missing");return false;}
            List<BlueprintUndo.Cell> before=work.stream().map(Work::before).toList();
            List<BlockPos> written=new ArrayList<>(work.size());boolean success=false;
            WRITING.set(true);
            try{
                for(var w:work){var c=w.cell();BlockPos p=w.before().pos();
                    if(!BlueprintUndo.unchanged(w.before(),world.getBlockState(p),BlueprintUndo.snapshot(world,p)))throw new IllegalStateException("Target changed");
                    written.add(p);
                    if(world.getBlockState(p)!=c.state()&&!BlueprintHooks.place(player,p,c.state()))throw new IllegalStateException("Placement rejected");
                }
                for(var w:work){var c=w.cell();if(c.data()!=null){var entity=world.getBlockEntity(w.before().pos());if(entity==null)throw new IllegalStateException("Missing block entity");entity.loadWithComponents(BlueprintPlan.remap(c.data(),plan,saved,anchor,turns,world.dimension().location().toString()),world.registryAccess());entity.setChanged();}}
                if(!player.isCreative()&&!enough(player,bill))throw new IllegalStateException("Inventory changed during placement");
                success=true;
            }catch(RuntimeException failure){
                com.destan.trafficengine.TrafficEngine.LOGGER.warn("Blueprint placement aborted: {}",failure.getClass().getSimpleName());
                // Restore only touched cells; rejected hooks must not erase untouched targets.
                Set<BlockPos> touched=new HashSet<>(written);BlueprintUndo.restore(world,before.stream().filter(c->touched.contains(c.pos())).toList());
            }finally{WRITING.remove();}
            if(!success){BlueprintToolItem.message(player,"stopped",index);return false;}
            List<ItemStack> refund=player.isCreative()||bill.isEmpty()?List.of():consume(player,bill);
            if(!written.isEmpty()){
                if(undoCopies.isEmpty())BlueprintUndo.invalidate(player.getUUID());
                for(var w:work){BlockPos p=w.before().pos();world.getBlockState(p).updateNeighbourShapes(world,p,2);world.updateNeighborsAt(p,world.getBlockState(p).getBlock());world.sendBlockUpdated(p,w.before().state(),world.getBlockState(p),3);}
                if(undoable){
                    BlueprintUndo.Copy undo=BlueprintUndo.capture(world,written,refund,before);
                    if(undo==null||undoBytes+undo.bytes()>BlueprintUndo.MAX_HISTORY_BYTES){undoable=false;undoCopies.clear();}
                    else{undoCopies.add(undo);undoBytes+=undo.bytes();}
                }
            }
            index++;if(index==copies){BlueprintToolItem.message(player,"finished",copies);return false;}return true;
        }
    }

}
