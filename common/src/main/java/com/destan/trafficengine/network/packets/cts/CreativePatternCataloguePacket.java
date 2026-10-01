package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.data.NamedTrafficSignTextureReference;
import com.destan.trafficengine.item.CreativePatternCatalogueItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public class CreativePatternCataloguePacket extends NetworkPacketData {

    private static final String NBT_DATA = "Data";
    private static final String NBT_TAB = "Tab"; // YENİ: Sunucuya sekme bilgisini gönderir

    private NamedTrafficSignTextureReference data;
    private String tab; // YENİ

    public CreativePatternCataloguePacket(PacketStatus status) {
        super(status);
    }

    public CreativePatternCataloguePacket(NamedTrafficSignTextureReference data, String tab) {
        super(PacketStatus.OK);
        this.data = data;
        this.tab = tab;
    }

    @Override
    protected void write(CompoundTag nbt) {
        if (data != null) {
            nbt.put(NBT_DATA, data.toNbt());
        }
        if (tab != null) {
            nbt.putString(NBT_TAB, tab);
        }
    }

    @Override
    protected void read(CompoundTag nbt) {
        if (nbt.contains(NBT_DATA)) {
            this.data = NamedTrafficSignTextureReference.fromNbt(nbt.getCompound(NBT_DATA));
        }
        if (nbt.contains(NBT_TAB)) {
            this.tab = nbt.getString(NBT_TAB);
        }
    }

    public static void handle(CreativePatternCataloguePacket packet, NetworkPacketContext context) {
        ServerPlayer sender = (ServerPlayer)context.getPlayer();

        var stack = sender.getMainHandItem().getItem() instanceof CreativePatternCatalogueItem
            ? sender.getMainHandItem() : sender.getOffhandItem();
        if (!(stack.getItem() instanceof CreativePatternCatalogueItem)) return;
        if (packet.data != null) {
            CreativePatternCatalogueItem.setCustomImage(stack, packet.data);
            CreativePatternCatalogueItem.setSelectedIndex(stack, -1);
        } else {
            CreativePatternCatalogueItem.clearCustomImage(stack);
        }
        if (packet.tab != null) CreativePatternCatalogueItem.setSelectedTab(stack, packet.tab);
        sender.getInventory().setChanged();
    }
}
