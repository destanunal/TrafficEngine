package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.data.TrafficSignTextureData;
import net.minecraft.nbt.CompoundTag;

public class CreateNewTrafficSignTexturePacket {

    public static class Request extends NetworkPacketData {
        private static final String NBT_DATA = "Data";

        private TrafficSignTextureData data;    

        public Request(PacketStatus status) {
            super(status);
        }

        public Request(TrafficSignTextureData data) {
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
    }

    public static class Response extends NetworkPacketData {

        public Response(PacketStatus status) {
            super(status);
        }

        public Response() {
            super(PacketStatus.OK);
        }

        @Override
        protected void write(CompoundTag nbt) {
        }

        @Override
        protected void read(CompoundTag nbt) {
        }
    }

    public static Response handle(Request packet, NetworkPacketContext context) {
        packet.data.save();
        return new Response();
    }
    
}
