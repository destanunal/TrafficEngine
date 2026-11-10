package com.destan.trafficengine.client;

import com.destan.trafficengine.blueprint.*;
import com.destan.trafficengine.block.data.EquipmentOutlineGeometry;
import com.destan.trafficengine.util.ItemData;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import java.util.*;

/** One cached wire mesh per state, drawn into the existing selection buffer. No render-time world reads. */
public final class BlueprintPreview {
    private BlueprintPreview(){}
    private record Ghost(BlockPos relative,float[] edges,BlockState state){}
    private static final Map<BlockState,float[]> MESHES=new HashMap<>();
    private static List<Ghost> ghosts=List.of();private static boolean[] valid=new boolean[0];
    private static Object identity,worldIdentity;private static BlockPos anchor;private static int ticks;private static boolean placing;
    private static int placementMode;
    private static boolean dirty;private static int minX,minY,minZ,maxX,maxY,maxZ;private static long[] chunks=new long[0];private static long chunkSignature=Long.MIN_VALUE;
    public static void clear(){identity=null;worldIdentity=null;anchor=null;ghosts=List.of();valid=new boolean[0];chunks=new long[0];dirty=false;ticks=0;MESHES.clear();}
    public static void invalidate(BlockPos pos){
        if(placing&&anchor!=null&&pos.getX()>=anchor.getX()+minX&&pos.getX()<=anchor.getX()+maxX&&pos.getY()>=anchor.getY()+minY&&pos.getY()<=anchor.getY()+maxY&&pos.getZ()>=anchor.getZ()+minZ&&pos.getZ()<=anchor.getZ()+maxZ)dirty=true;
    }
    public static void tick(Minecraft mc){
        if(mc.level==null||mc.player==null){if(worldIdentity!=null)clear();return;}
        ItemStack stack=mc.player.getMainHandItem();if(!(stack.getItem() instanceof BlueprintToolItem))stack=mc.player.getOffhandItem();
        if(!(stack.getItem() instanceof BlueprintToolItem)){if(identity!=null)clear();return;}
        Object next=ItemData.identity(stack);
        if(worldIdentity!=mc.level){clear();worldIdentity=mc.level;}
        if(next!=identity){identity=next;rebuild(BlueprintToolItem.data(stack));ticks=0;}
        if(++ticks%4!=0)return;
        if(placing){
            BlockPos nextAnchor=mc.hitResult instanceof BlockHitResult hit&&hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?(placementMode==2?hit.getBlockPos():hit.getBlockPos().relative(hit.getDirection())):null;
            if(!Objects.equals(anchor,nextAnchor)){
                anchor=nextAnchor;dirty=true;chunkSignature=Long.MIN_VALUE;
                if(anchor!=null){Set<Long> unique=new HashSet<>();for(Ghost g:ghosts){long x=(anchor.getX()+g.relative.getX())>>4,z=(anchor.getZ()+g.relative.getZ())>>4;unique.add((x<<32)|(z&0xffffffffL));}chunks=unique.stream().mapToLong(Long::longValue).toArray();}
            }
        }
        if(anchor==null)return;
        if(placing&&(dirty||ticks%20==0)){
            long signature=1;for(long chunk:chunks)signature=signature*31+(mc.level.getChunkSource().hasChunk((int)(chunk>>32),(int)chunk)?1:0);
            if(signature!=chunkSignature){chunkSignature=signature;dirty=true;}
        }
        if(!dirty)return;dirty=false;
        BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos();Set<Long> occupied=new HashSet<>();
        for(int i=0;i<ghosts.size();i++){
            Ghost ghost=ghosts.get(i);p.setWithOffset(anchor,ghost.relative);
            valid[i]=!placing||occupied.add(p.asLong())&&mc.level.hasChunkAt(p)&&mc.level.isInWorldBounds(p)&&mc.level.getWorldBorder().isWithinBounds(p)&&(placementMode!=0||mc.level.getBlockState(p).isAir())
                &&mc.player.distanceToSqr(p.getX()+.5,p.getY()+.5,p.getZ()+.5)<=128*128;
        }
    }
    private static void rebuild(CompoundTag tag){
        ghosts=List.of();valid=new boolean[0];anchor=null;MESHES.clear();placing=tag.getBoolean("Placing");placementMode=BlueprintToolItem.mode(tag);dirty=true;chunks=new long[0];chunkSignature=Long.MIN_VALUE;
        if(!placing){
            if(!tag.contains("First"))return;Minecraft mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().toString().equals(tag.getString("SelectionDimension")))return;
            BlockPos first=BlockPos.of(tag.getLong("First")),second=tag.contains("Second")?BlockPos.of(tag.getLong("Second")):first;
            anchor=new BlockPos(Math.min(first.getX(),second.getX()),Math.min(first.getY(),second.getY()),Math.min(first.getZ(),second.getZ()));
            double x=Math.min(BlueprintPlan.MAX_EDGE,Math.abs(first.getX()-second.getX())+1),y=Math.min(BlueprintPlan.MAX_EDGE,Math.abs(first.getY()-second.getY())+1),z=Math.min(BlueprintPlan.MAX_EDGE,Math.abs(first.getZ()-second.getZ())+1);
            ghosts=List.of(new Ghost(BlockPos.ZERO,shapeEdges(Shapes.box(0,0,0,x,y,z)),null));valid=new boolean[]{true};return;
        }
        BlueprintPlan plan=BlueprintToolItem.selected(tag);if(plan==null)return;
        int copies=BlueprintToolItem.copies(tag);if(copies*plan.cells().size()>BlueprintPlan.MAX_TOTAL)return;
        var cells=plan.rotated(tag.getInt("Rotation")&3);Direction direction=BlueprintToolItem.direction(tag.getInt("Direction"));int spacing=BlueprintPlan.repeatStep(cells,direction,BlueprintToolItem.spacing(tag));
        List<Ghost> out=new ArrayList<>();
        for(int copy=0;copy<copies;copy++)for(var cell:cells){
            float[] mesh=MESHES.computeIfAbsent(cell.state(),BlueprintPreview::mesh);
            out.add(new Ghost(cell.pos().relative(direction,spacing*copy),mesh,cell.state()));
        }
        ghosts=List.copyOf(out);valid=new boolean[out.size()];
        minX=minY=minZ=Integer.MAX_VALUE;maxX=maxY=maxZ=Integer.MIN_VALUE;
        for(Ghost g:ghosts){minX=Math.min(minX,g.relative.getX());minY=Math.min(minY,g.relative.getY());minZ=Math.min(minZ,g.relative.getZ());maxX=Math.max(maxX,g.relative.getX());maxY=Math.max(maxY,g.relative.getY());maxZ=Math.max(maxZ,g.relative.getZ());}
    }
    private static float[] mesh(BlockState state){
        if(EquipmentOutlineGeometry.supports(state))return EquipmentOutlineGeometry.edges(state);
        try{return shapeEdges(state.getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO));}
        catch(RuntimeException unsupported){return shapeEdges(Shapes.block());}
    }
    public static float[] geometry(BlockState state){return mesh(state);}
    private static float[] shapeEdges(net.minecraft.world.phys.shapes.VoxelShape shape){
        List<float[]> edges=new ArrayList<>();shape.forAllEdges((x,y,z,a,b,c)->{
            if(edges.size()>=64)return;double dx=a-x,dy=b-y,dz=c-z,length=Math.sqrt(dx*dx+dy*dy+dz*dz);if(length==0)return;
            edges.add(new float[]{(float)x,(float)y,(float)z,(float)a,(float)b,(float)c,(float)(dx/length),(float)(dy/length),(float)(dz/length)});
        });float[] mesh=new float[edges.size()*9];for(int i=0;i<edges.size();i++)System.arraycopy(edges.get(i),0,mesh,i*9,9);return mesh;
    }
    public static void render(PoseStack pose,VertexConsumer lines,double cameraX,double cameraY,double cameraZ){
        if(anchor==null||ghosts.isEmpty()||Minecraft.getInstance().screen!=null)return;
        var matrix=pose.last();double x=anchor.getX()-cameraX,y=anchor.getY()-cameraY,z=anchor.getZ()-cameraZ;
        for(int n=0;n<ghosts.size();n++){
            Ghost ghost=ghosts.get(n);float[] edges=ghost.edges;boolean ok=valid[n];
            for(int i=0;i<edges.length;i+=9)for(int endpoint=0;endpoint<2;endpoint++){
                int p=i+endpoint*3;
                lines.addVertex(matrix.pose(),(float)(x+ghost.relative.getX()+edges[p]),(float)(y+ghost.relative.getY()+edges[p+1]),(float)(z+ghost.relative.getZ()+edges[p+2]))
                    .setColor(ok?.15F:1F,ok?.9F:.15F,ok?1F:.15F,.65F).setNormal(matrix,edges[i+6],edges[i+7],edges[i+8]);
            }
        }
    }
}
