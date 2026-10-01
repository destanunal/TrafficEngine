package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.data.TrafficSignTextureData;
import com.destan.trafficengine.data.TrafficSignTextureManager;
import net.minecraft.nbt.CompoundTag;

public class GetTrafficSignTexturePacket {

    private static final String NBT_DATA = "Data";

    public static class Request extends NetworkPacketData {        

        private String name;

        public Request(PacketStatus status) {
            super(status);
        }

        public Request(String name) {
            super(PacketStatus.OK);
            this.name = name;
        }

        @Override
        protected void write(CompoundTag nbt) {
            nbt.putString(NBT_DATA, name);
        }

        @Override
        protected void read(CompoundTag nbt) {
            this.name = nbt.getString(NBT_DATA);
        }
    }
    
    public static class Response extends NetworkPacketData {        

        private TrafficSignTextureData data;

        public Response(PacketStatus status) {
            super(status);
        }

        public Response(TrafficSignTextureData data) {
            super(PacketStatus.OK);
            this.data = data;
        }

        @Override
        protected void write(CompoundTag nbt) {
            nbt.put(NBT_DATA, data.serializeNbt());
        }

        @Override
        protected void read(CompoundTag nbt) {
            this.data = TrafficSignTextureData.deserializeNbt(nbt.getCompound(NBT_DATA));
        }

        public TrafficSignTextureData getData() {
            return data;
        }
    }

    public static Response handle(Request packet, NetworkPacketContext context) {
        return new Response(TrafficSignTextureManager.load(packet.name));
    }
    
}
