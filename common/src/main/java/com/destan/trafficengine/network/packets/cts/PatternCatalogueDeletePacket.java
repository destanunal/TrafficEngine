package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.client.screen.menu.TrafficSignWorkbenchMenu;
import com.destan.trafficengine.item.PatternCatalogueItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class PatternCatalogueDeletePacket {
    
    public static class Request extends NetworkPacketData {
        private static final String NBT_DATA = "Data";

        private int index;

        public Request(PacketStatus status) {
            super(status);
        }

        public Request(int index) {
            super(PacketStatus.OK);
            this.index = index;
        }

        @Override
        protected void write(CompoundTag nbt) {
            nbt.putInt(NBT_DATA, index);
        }

        @Override
        protected void read(CompoundTag nbt) {
            this.index = nbt.getInt(NBT_DATA);
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
        ServerPlayer sender = (ServerPlayer)context.getPlayer();
        if (sender.containerMenu instanceof TrafficSignWorkbenchMenu menu) {
            final ItemStack stack = menu.patternSlot.getItem();
            if (!(stack.getItem() instanceof PatternCatalogueItem))
                return new Response();

            PatternCatalogueItem.removePatternAt(stack, packet.index);
            menu.patternSlot.set(stack);
            menu.patternSlot.setChanged();
            menu.broadcastChanges();
        }
        return new Response();
    }
}
