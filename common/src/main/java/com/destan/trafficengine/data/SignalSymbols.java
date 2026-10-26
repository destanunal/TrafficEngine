package com.destan.trafficengine.data;

import com.destan.trafficengine.block.data.TrafficLightIcon;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Four immutable symbol overrides. Null icons inherit the original whole-signal settings. */
public final class SignalSymbols {
    public static final int SLOTS = 4;
    public static final SignalSymbols DEFAULT = new SignalSymbols(new TrafficLightIcon[SLOTS],
        new PixelPattern[]{PixelPattern.EMPTY,PixelPattern.EMPTY,PixelPattern.EMPTY,PixelPattern.EMPTY});
    private final TrafficLightIcon[] icons;
    private final PixelPattern[] masks;
    private SignalSymbols(TrafficLightIcon[] icons, PixelPattern[] masks) { this.icons=icons;this.masks=masks; }
    public boolean inherits(int slot) { return slot<0 || slot>=SLOTS || icons[slot]==null; }
    public TrafficLightIcon icon(int slot, TrafficLightIcon fallback) { return inherits(slot)?fallback:icons[slot]; }
    public PixelPattern mask(int slot, PixelPattern fallback) { return inherits(slot)?fallback:masks[slot]; }
    public SignalSymbols with(int slot, TrafficLightIcon icon, PixelPattern mask) {
        if(slot<0 || slot>=SLOTS)return this;
        TrafficLightIcon[] nextIcons=icons.clone();PixelPattern[] nextMasks=masks.clone();
        nextIcons[slot]=icon;nextMasks[slot]=icon==null?PixelPattern.EMPTY:mask==null?PixelPattern.EMPTY:mask;
        return new SignalSymbols(nextIcons,nextMasks);
    }
    public void write(CompoundTag tag) {
        ListTag entries=new ListTag();
        for(int i=0;i<SLOTS;i++)if(!inherits(i)) {
            CompoundTag entry=new CompoundTag();entry.putByte("slot",(byte)i);entry.putByte("icon",icons[i].getIndex());
            if(!masks[i].isEmpty())entry.putLongArray("pixels",masks[i].bits());entries.add(entry);
        }
        tag.put("SignalSymbols",entries);
    }
    public static SignalSymbols read(CompoundTag tag) {
        SignalSymbols symbols=DEFAULT;ListTag entries=tag.getList("SignalSymbols",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(SLOTS,entries.size());i++) {
            CompoundTag e=entries.getCompound(i);int slot=e.getByte("slot");
            if(slot>=0 && slot<SLOTS)symbols=symbols.with(slot,TrafficLightIcon.getIconByIndex(e.getByte("icon")),PixelPattern.of(e.getLongArray("pixels")));
        }
        return symbols;
    }
    @Override public boolean equals(Object other) { return other instanceof SignalSymbols s && Arrays.equals(icons,s.icons)&&Arrays.equals(masks,s.masks); }
    @Override public int hashCode() { return 31*Arrays.hashCode(icons)+Arrays.hashCode(masks); }
}
