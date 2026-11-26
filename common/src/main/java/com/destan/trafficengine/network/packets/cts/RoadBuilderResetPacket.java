package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.item.RoadConstructionTool;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

public class RoadBuilderResetPacket extends NetworkPacketData {
    
    public static final int START = 1, END = 2;
    private int endpoint;

    public RoadBuilderResetPacket(PacketStatus status) {
        super(status);
    }

    public RoadBuilderResetPacket() {
        this(0);
    }

    public RoadBuilderResetPacket(int endpoint) {
        super(PacketStatus.OK);
        this.endpoint = endpoint;
    }

    @Override
    protected void write(CompoundTag nbt) {
        nbt.putInt("Endpoint", endpoint);
    }

    @Override
    protected void read(CompoundTag nbt) {
        endpoint = nbt.getInt("Endpoint");
    }
    
    public static void handle(RoadBuilderResetPacket packet, NetworkPacketContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer sender) || sender.isSpectator()) return;
        ItemStack stack = sender.getMainHandItem();
        if (!(stack.getItem() instanceof RoadConstructionTool)) stack = sender.getOffhandItem();
        if (!(stack.getItem() instanceof RoadConstructionTool)) return;
        switch (packet.endpoint) {
            case 0 -> RoadConstructionTool.reset(stack);
            case START -> RoadConstructionTool.clearSelection(stack, true, false);
            case END -> RoadConstructionTool.clearSelection(stack, false, true);
            default -> { return; }
        }
        sender.getInventory().setChanged();
    }
}
