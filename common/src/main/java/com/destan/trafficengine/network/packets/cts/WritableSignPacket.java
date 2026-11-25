package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.block.WritableTrafficSign;
import com.destan.trafficengine.block.entity.HouseNumberSignBlockEntity;
import com.destan.trafficengine.block.entity.StreetSignBlockEntity;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.data.WritableSignText;
import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.util.NbtPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

public class WritableSignPacket extends NetworkPacketData {

    private static final String NBT_MESSAGES = "Messages";
    private static final String NBT_POS = "Pos";
    private static final String NBT_PAINT = "Paint";

    private String[] messages;
    private PaintColor paint;
    private BlockPos pos;

    public WritableSignPacket(PacketStatus status) {
        super(status);
    }

    public WritableSignPacket(BlockPos pos, String[] messages) {
        this(pos, messages, null);
    }

    public WritableSignPacket(BlockPos pos, String[] messages, PaintColor paint) {
        super(PacketStatus.OK);
        this.pos = pos;
        this.messages = messages;
        this.paint = paint;
    }

    @Override
    protected void write(CompoundTag nbt) {
        if (messages != null) WritableSignText.writeLines(nbt, NBT_MESSAGES, messages);
        if (paint != null) nbt.putInt(NBT_PAINT, paint.getIndex());
        NbtPositions.putNbtPos(nbt, NBT_POS, pos);
    }

    @Override
    protected void read(CompoundTag nbt) {
        this.messages = nbt.contains(NBT_MESSAGES, Tag.TAG_LIST) ? WritableSignText.readLines(nbt, NBT_MESSAGES, 1) : null;
        this.paint = null;
        if (nbt.contains(NBT_PAINT, Tag.TAG_INT)) {
            int index = nbt.getInt(NBT_PAINT);
            PaintColor decoded = PaintColor.getByIndex(index);
            if (decoded.getIndex() == index) this.paint = decoded;
        }
        this.pos = NbtPositions.getNbtBlockPos(nbt, NBT_POS);
    }

    public static void handle(WritableSignPacket packet, NetworkPacketContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player) || !WritableTrafficSign.mayEdit(player, packet.pos)) return;
        var sign = player.level().getBlockEntity(packet.pos);
        if (sign instanceof HouseNumberSignBlockEntity number && packet.messages != null) number.setTexts(packet.messages);
        else if (sign instanceof StreetSignBlockEntity street) street.setSignData(packet.messages, packet.paint);
    }
}
