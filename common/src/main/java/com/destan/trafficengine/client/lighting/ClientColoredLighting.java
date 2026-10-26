package com.destan.trafficengine.client.lighting;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.destan.trafficengine.block.data.IColorBlockEntity;
import com.destan.trafficengine.block.data.TrafficLightColor;
import com.destan.trafficengine.block.entity.LedDeviceBlockEntity;
import com.destan.trafficengine.block.entity.StreetLampBlockEntity;
import com.destan.trafficengine.block.entity.TrafficLightBlockEntity;
import com.destan.trafficengine.data.PaintColor;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Client-owned RGB volumes; only changed sources are propagated. Published maps are immutable. */
public final class ClientColoredLighting {
    private static final boolean ENABLED = !Boolean.getBoolean("trafficengine.disableColoredLighting");
    private static final int MAX_SOURCES = 2048;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final class Source {
        final BlockEntity entity;
        int rgb=-1;
        boolean dirty=true;
        Source(BlockEntity entity) { this.entity=entity; }
    }
    private static final Map<BlockEntity,Source> SOURCES=new ConcurrentHashMap<>();
    private static final ColoredLightIndex COLORS = new ColoredLightIndex();
    private static ClientLevel world;
    private static volatile int skyDarken=-1;
    private ClientColoredLighting() {}

    public static void register(BlockEntity entity) {
        if (!ENABLED) return;
        if (entity instanceof TrafficLightBlockEntity signal && signal.getSignalFace()!=0) return;
        if (!(entity.getLevel() instanceof ClientLevel level)) return;
        if (!(entity instanceof StreetLampBlockEntity || entity instanceof TrafficLightBlockEntity
                || entity instanceof LedDeviceBlockEntity || entity instanceof IColorBlockEntity && entity.getBlockState().getLightEmission()>0)) return;
        if (world!=level) { SOURCES.clear(); COLORS.clear(); world=level; skyDarken=-1; }
        if (SOURCES.size() < MAX_SOURCES || SOURCES.containsKey(entity)) SOURCES.computeIfAbsent(entity,Source::new);
    }
    public static void blockChanged(BlockPos pos) {
        if (!ENABLED) return;
        for (Source source:SOURCES.values()) if (source.entity.getBlockPos().distManhattan(pos)<=16) source.dirty=true;
    }
    private static int emission(BlockEntity entity) {
        int rgb=singleEmission(entity);
        return entity instanceof com.destan.trafficengine.block.entity.DoubleTrafficLightBlockEntity light
            ?RgbLightMath.merge(rgb,singleEmission(light.face(1))):rgb;
    }
    private static int singleEmission(BlockEntity entity) {
        if (entity instanceof TrafficLightBlockEntity light) {
            if (entity.getBlockState().getBlock() instanceof com.destan.trafficengine.block.WarningLightBlock) {
                if (!light.isWarningActive() || entity.getLevel() == null) return 0;
                int mask = light.getFlashSequence().mask(entity.getLevel().getGameTime());
                int count = entity.getBlockState().getValue(com.destan.trafficengine.block.TrafficLightBlock.MODEL).getLightsCount();
                int rgb = 0;
                for (int slot = 0; slot < Math.min(2, count); slot++) if ((mask & 1 << slot) != 0)
                    rgb = RgbLightMath.merge(rgb, RgbLightMath.emission(light.getWarningColor(slot), 4));
                return rgb;
            }
            if (light.isScheduleBlinkOff()) return 0;
            int color=0;
            for (TrafficLightColor active:light.getEnabledColors()) {
                int rgb=active.isSimilar(TrafficLightColor.RED)?0xFF283A:active.isSimilar(TrafficLightColor.YELLOW)?0xFFD830:active.isSimilar(TrafficLightColor.GREEN)?0x32EB62:0;
                color=RgbLightMath.merge(color,RgbLightMath.emission(rgb,3));
            }
            return color;
        }
        if (entity instanceof LedDeviceBlockEntity led) {
            if (!led.isLit()) return 0;
            boolean display=((com.destan.trafficengine.block.LedDeviceBlock)entity.getBlockState().getBlock()).getDeviceType().isTrafficDisplay();
            if (display) return led.getMessage().isBlank()?0:RgbLightMath.emission(led.getLedColor(),4);
            long pixels=led.getPixels(); int r=0,g=0,b=0,count=0;
            for(int i=0;i<64;i++)if((pixels&(1L<<i))!=0) { int c=led.getPixelColor(i);r+=c>>16&255;g+=c>>8&255;b+=c&255;count++; }
            return count==0?0:RgbLightMath.emission((r/count<<16)|(g/count<<8)|b/count,5);
        }
        if (entity instanceof IColorBlockEntity colored) {
            int strength=entity.getBlockState().getLightEmission();
            PaintColor paint=colored.getColor();
            return strength==0 || paint==PaintColor.NONE || paint==PaintColor.WHITE ? 0 : RgbLightMath.emission(paint.getTextureColor().getAsARGB(),strength);
        }
        return 0;
    }
    public static Long2IntOpenHashMap propagate(BlockAndTintGetter level,BlockPos start,int rgb) {
        Long2IntOpenHashMap result=new Long2IntOpenHashMap();
        if(rgb==0)return result;
        LongArrayFIFOQueue queue=new LongArrayFIFOQueue();
        long first=start.asLong();result.put(first,rgb);queue.enqueue(first);
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        while(!queue.isEmpty()) {
            long current=queue.dequeueLong();int next=RgbLightMath.decay(result.get(current));if(next==0)continue;
            int x=BlockPos.getX(current),y=BlockPos.getY(current),z=BlockPos.getZ(current);
            for(Direction side:DIRECTIONS) {
                pos.set(x+side.getStepX(),y+side.getStepY(),z+side.getStepZ());
                if (level instanceof ClientLevel client && !client.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                long key=pos.asLong();
                // Breadth-first traversal reaches each cell at its strongest level first.
                if(result.containsKey(key))continue;
                result.put(key,next);
                if(!level.getBlockState(pos).isSolidRender(level,pos))queue.enqueue(key);
            }
        }
        return result;
    }
    private static void invalidate(BlockPos pos,int radius) {
        Minecraft.getInstance().levelRenderer.setBlocksDirty(pos.getX()-radius,pos.getY()-radius,pos.getZ()-radius,pos.getX()+radius,pos.getY()+radius,pos.getZ()+radius);
    }
    public static void tick(Minecraft mc) {
        if (!ENABLED) return;
        if(mc.level!=world) { SOURCES.clear();COLORS.clear();world=mc.level;skyDarken=-1;return; }
        if(world==null)return;
        if(SOURCES.isEmpty()){skyDarken=world.getSkyDarken();return;}
        boolean publish=false;int rebuilt=0;Map<BlockPos,Integer> dirty=null;
        for(Iterator<Source> it=SOURCES.values().iterator();it.hasNext();) {
            Source source=it.next();BlockPos pos=source.entity.getBlockPos();
            if(source.entity.isRemoved() || source.entity.getLevel()!=world || !world.isLoaded(pos)) { it.remove();COLORS.remove(source);if(dirty==null)dirty=new HashMap<>();dirty.merge(pos,RgbLightMath.maximum(Math.max(0,source.rgb))+1,Math::max);publish=true;continue; }
            int rgb=emission(source.entity);
            if((source.dirty || source.rgb!=rgb) && rebuilt<8) {
                if(dirty==null)dirty=new HashMap<>();
                dirty.merge(pos,Math.max(RgbLightMath.maximum(Math.max(0,source.rgb)),RgbLightMath.maximum(rgb))+1,Math::max);
                COLORS.replace(source,propagate(world,pos,rgb));source.rgb=rgb;source.dirty=false;rebuilt++;publish=true;
            }
        }
        if(publish) {
            COLORS.publish();
            dirty.forEach(ClientColoredLighting::invalidate);
        }
        int darken=world.getSkyDarken();
        if(darken!=skyDarken) { skyDarken=darken;for(Source source:SOURCES.values())if(source.rgb>0)invalidate(source.entity.getBlockPos(),RgbLightMath.maximum(source.rgb)+1); }
    }
    public record Tint(float red,float green,float blue,int brightness) {}
    public static int[] promoteLight(int[] light,int brightness) {
        int minimum=brightness<<4;
        for(int value:light) if((value&0xF0)<minimum) {
            int[] promoted=light.clone();
            for(int i=0;i<promoted.length;i++) if((promoted[i]&0xF0)<minimum) promoted[i]=(promoted[i]&~0xF0)|minimum;
            return promoted;
        }
        return light;
    }
    public static Tint tint(BlockPos pos,Direction face,int[] light) {
        return tint(pos,face,light[0],light[1],light[2],light[3]);
    }
    public static Tint tint(BlockPos pos,Direction face,int a,int b,int c,int d) {
        if (!ENABLED) return null;
        int rgb=COLORS.get(pos.getX()+face.getStepX(),pos.getY()+face.getStepY(),pos.getZ()+face.getStepZ());if(rgb==0)return null;
        int block=Math.max(Math.max(a>>4&15,b>>4&15),Math.max(c>>4&15,d>>4&15));
        int sky=Math.max(Math.max(a>>20&15,b>>20&15),Math.max(c>>20&15,d>>20&15));
        sky=Math.max(0,sky-Math.max(0,skyDarken));
        return new Tint(RgbLightMath.factor(RgbLightMath.red(rgb),rgb,block,sky),RgbLightMath.factor(RgbLightMath.green(rgb),rgb,block,sky),RgbLightMath.factor(RgbLightMath.blue(rgb),rgb,block,sky),RgbLightMath.maximum(rgb));
    }

}
