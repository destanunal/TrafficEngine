package com.destan.trafficengine.client.compat;

import com.destan.trafficengine.block.data.RoadBlock;
import com.destan.trafficengine.block.entity.ColoredBlockEntity;
import com.destan.trafficengine.util.LoadedRoadData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import java.lang.reflect.Method;
import java.util.Optional;

/** Optional JourneyMap accessor. Cache API lookup, never construct entities on its map worker. */
public final class JourneyMapRoadData {
    private static final ClassValue<Optional<Method>> GET_CHUNK=new ClassValue<>() {
        @Override protected Optional<Method> computeValue(Class<?> type) {
            try {return Optional.of(type.getMethod("getChunk"));}
            catch (ReflectiveOperationException ignored) {return Optional.empty();}
        }
    };
    private JourneyMapRoadData() {}
    public static ColoredBlockEntity road(Object metadata,BlockPos pos) {
        if(metadata==null)return null;
        Method accessor=GET_CHUNK.get(metadata.getClass()).orElse(null);
        if(accessor==null)return null;
        try {
            // JourneyMap 6's getChunk reads a weak reference; it does not load terrain.
            Object source=accessor.invoke(metadata);
            if(source instanceof LevelChunk chunk&&chunk.getBlockState(pos).getBlock() instanceof RoadBlock
                    &&LoadedRoadData.blockEntity(chunk,pos) instanceof ColoredBlockEntity entity)return entity;
        } catch(ReflectiveOperationException ignored) {
            // A discarded chunk or a different optional API falls back to JourneyMap's colour.
        }
        return null;
    }
}
