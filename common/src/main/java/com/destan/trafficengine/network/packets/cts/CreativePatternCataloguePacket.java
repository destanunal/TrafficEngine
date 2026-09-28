package com.destan.trafficengine.network.packets.cts;

import de.mrjulsen.mcdragonlib.data.DLStatus;
import de.mrjulsen.mcdragonlib.network.NetworkPacketContext;
import de.mrjulsen.mcdragonlib.network.NetworkPacketData;
import com.destan.trafficengine.data.NamedTrafficSignTextureReference;
import com.destan.trafficengine.item.CreativePatternCatalogueItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public class CreativePatternCataloguePacket extends NetworkPacketData {

    private static final String NBT_DATA = "Data";
    private static final String NBT_TAB = "Tab"; // YENİ: Sunucuya sekme bilgisini gönderir

    private NamedTrafficSignTextureReference data;
    private String tab; // YENİ

    public CreativePatternCataloguePacket(DLStatus status) {
        super(status);
    }

    public CreativePatternCataloguePacket(NamedTrafficSignTextureReference data, String tab) {
        super(DLStatus.OK);
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

        if (sender.getMainHandItem().getItem() instanceof CreativePatternCatalogueItem) {
            if (packet.data != null) CreativePatternCatalogueItem.setCustomImage(sender.getMainHandItem(), packet.data);
            if (packet.tab != null) CreativePatternCatalogueItem.setSelectedTab(sender.getMainHandItem(), packet.tab);
            CreativePatternCatalogueItem.setSelectedIndex(sender.getMainHandItem(), -1);

        } else if (sender.getOffhandItem().getItem() instanceof CreativePatternCatalogueItem) {
            if (packet.data != null) CreativePatternCatalogueItem.setCustomImage(sender.getOffhandItem(), packet.data);
            if (packet.tab != null) CreativePatternCatalogueItem.setSelectedTab(sender.getOffhandItem(), packet.tab);

            // KOPYALA-YAPIŞTIR HATASI DÜZELTİLDİ: getMainHandItem yerine getOffhandItem olmalıydı!
            CreativePatternCatalogueItem.setSelectedIndex(sender.getOffhandItem(), -1);
        }
        sender.getInventory().setChanged();
    }
}