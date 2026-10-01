package com.destan.trafficengine.network;
import net.minecraft.nbt.CompoundTag;
public abstract class NetworkPacketData {
    private PacketStatus status;
    protected NetworkPacketData(PacketStatus status) {this.status=status;}
    public PacketStatus getStatus() {return status;}
    public CompoundTag serializeNbt() {CompoundTag tag=new CompoundTag();tag.put("Status",status.toNbt());CompoundTag data=new CompoundTag();if(status.noIssues())write(data);tag.put("Data",data);return tag;}
    public void deserializeNbt(CompoundTag tag) {status=PacketStatus.fromNbt(tag.getCompound("Status"));if(status.noIssues())read(tag.getCompound("Data"));}
    protected abstract void write(CompoundTag tag);
    protected abstract void read(CompoundTag tag);
}
