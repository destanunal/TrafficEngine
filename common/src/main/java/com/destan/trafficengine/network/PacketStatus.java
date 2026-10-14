package com.destan.trafficengine.network;
import net.minecraft.nbt.CompoundTag;
public record PacketStatus(byte flag,int code,String message) {
    public static final byte FLAG_OK=0, FLAG_DONE=1, FLAG_CANCEL=2, FLAG_ERROR=Byte.MIN_VALUE;
    public static final PacketStatus OK=new PacketStatus(FLAG_OK,-1,""), EMPTY=new PacketStatus(FLAG_ERROR,-1,"Not initialized");
    public static PacketStatus error(Throwable error) {return new PacketStatus(FLAG_ERROR,-1,error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());}
    public boolean noIssues() {return flag==FLAG_OK || flag==FLAG_DONE;}
    public CompoundTag toNbt() {CompoundTag t=new CompoundTag();t.putByte("Flag",flag);t.putInt("Code",code);t.putString("Message",message);return t;}
    public static PacketStatus fromNbt(CompoundTag t) {return new PacketStatus(t.getByte("Flag"),t.getInt("Code"),t.getString("Message"));}
}
