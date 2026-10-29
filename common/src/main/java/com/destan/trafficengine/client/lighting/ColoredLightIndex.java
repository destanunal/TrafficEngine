package com.destan.trafficengine.client.lighting;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.shorts.Short2ShortOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/** Main-thread updates publish immutable sections for chunk-rendering workers. */
public final class ColoredLightIndex {
    private final Map<Object, Long2ObjectOpenHashMap<Short2ShortOpenHashMap>> volumes = new IdentityHashMap<>();
    private final Long2ObjectOpenHashMap<Set<Object>> contributors = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet dirtySections = new LongOpenHashSet();
    private volatile Long2ObjectOpenHashMap<Short2ShortOpenHashMap> snapshot = new Long2ObjectOpenHashMap<>();

    public void replace(Object source, Long2IntOpenHashMap cells) {
        remove(source);
        if (cells.isEmpty()) return;
        Long2ObjectOpenHashMap<Short2ShortOpenHashMap> sections = new Long2ObjectOpenHashMap<>();
        for (var iterator = cells.long2IntEntrySet().fastIterator(); iterator.hasNext();) {
            var entry = iterator.next();
            long pos = entry.getLongKey();
            int x = BlockPos.getX(pos), y = BlockPos.getY(pos), z = BlockPos.getZ(pos);
            long section = SectionPos.asLong(x >> 4, y >> 4, z >> 4);
            sections.computeIfAbsent(section, key -> new Short2ShortOpenHashMap())
                    .put(local(x, y, z), (short) entry.getIntValue());
        }
        volumes.put(source, sections);
        for (long section : sections.keySet()) {
            contributors.computeIfAbsent(section, key -> Collections.newSetFromMap(new IdentityHashMap<>(2))).add(source);
            dirtySections.add(section);
        }
    }

    public void remove(Object source) {
        var previous = volumes.remove(source);
        if (previous == null) return;
        for (long section : previous.keySet()) {
            var sources = contributors.get(section);
            sources.remove(source);
            if (sources.isEmpty()) contributors.remove(section);
            dirtySections.add(section);
        }
    }

    public boolean publish() {
        if (dirtySections.isEmpty()) return false;
        var updated = new Long2ObjectOpenHashMap<>(snapshot);
        for (long section : dirtySections) {
            var sources = contributors.get(section);
            if (sources == null) {
                updated.remove(section);
                continue;
            }
            // A source's section maps are immutable after replace(). Publishing
            // the sole contributor directly avoids a second retained hash table.
            if (sources.size()==1) {
                Object only=sources.iterator().next();
                updated.put(section,volumes.get(only).get(section));
                continue;
            }
            var merged = new Short2ShortOpenHashMap();
            for (Object source : sources) {
                var cells = volumes.get(source).get(section);
                for (var iterator = cells.short2ShortEntrySet().fastIterator(); iterator.hasNext();) {
                    var entry = iterator.next();
                    short key = entry.getShortKey();
                    merged.put(key, (short) RgbLightMath.merge(merged.get(key), entry.getShortValue()));
                }
            }
            updated.put(section, merged);
        }
        snapshot = updated;
        dirtySections.clear();
        return true;
    }

    public int get(int x, int y, int z) {
        var section = snapshot.get(SectionPos.asLong(x >> 4, y >> 4, z >> 4));
        return section == null ? 0 : section.get(local(x, y, z));
    }

    public void clear() {
        volumes.clear();
        contributors.clear();
        dirtySections.clear();
        snapshot = new Long2ObjectOpenHashMap<>();
    }

    private static short local(int x, int y, int z) {
        return (short) ((x & 15) << 8 | (y & 15) << 4 | (z & 15));
    }
}
