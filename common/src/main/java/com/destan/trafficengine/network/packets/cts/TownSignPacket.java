package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.block.TownSignBlock;
import com.destan.trafficengine.block.WritableTrafficSign;
import com.destan.trafficengine.block.data.TownSignVariant;
import com.destan.trafficengine.block.entity.TownSignBlockEntity;
import com.destan.trafficengine.data.WritableSignText;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.util.NbtPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

public class TownSignPacket extends NetworkPacketData {
    private BlockPos pos;
    private String[] front;
    private String[] back;
    private TownSignVariant variant;

    public TownSignPacket(PacketStatus status) {
        super(status);
    }

    public TownSignPacket(BlockPos pos, String[] front, String[] back, TownSignVariant variant) {
        super(PacketStatus.OK);
        this.pos = pos;
        this.front = front;
        this.back = back;
        this.variant = variant;
    }

    public TownSignPacket(BlockPos pos, String[] messages, TownSignVariant variant, TownSignBlock.ETownSignSide side) {
        this(pos, side == TownSignBlock.ETownSignSide.FRONT ? messages : null,
            side == TownSignBlock.ETownSignSide.BACK ? messages : null, variant);
    }

    @Override
    protected void write(CompoundTag tag) {
        NbtPositions.putNbtPos(tag, "Pos", pos);
        if (front != null) WritableSignText.writeLines(tag, "Front", front);
        if (back != null) WritableSignText.writeLines(tag, "Back", back);
        if (variant != null) tag.putInt("Variant", variant.getIndex());
    }

    @Override
    protected void read(CompoundTag tag) {
        pos = NbtPositions.getNbtBlockPos(tag, "Pos");
        front = tag.contains("Front", Tag.TAG_LIST) ? WritableSignText.readLines(tag, "Front", WritableSignText.FRONT_LINES) : null;
        back = tag.contains("Back", Tag.TAG_LIST) ? WritableSignText.readLines(tag, "Back", WritableSignText.BACK_LINES) : null;
        variant = tag.contains("Variant", Tag.TAG_ANY_NUMERIC) ? TownSignVariant.getVariantByIndex(tag.getInt("Variant")) : null;
        if (front == null && back == null && tag.contains("Messages", Tag.TAG_LIST)) {
            if (TownSignBlock.ETownSignSide.getSideByIndex(tag.getInt("Side")) == TownSignBlock.ETownSignSide.BACK) {
                back = WritableSignText.readLines(tag, "Messages", WritableSignText.BACK_LINES);
            } else {
                front = WritableSignText.readLines(tag, "Messages", WritableSignText.FRONT_LINES);
            }
        }
    }

    public static void handle(TownSignPacket packet, NetworkPacketContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player) || !WritableTrafficSign.mayEdit(player, packet.pos)
                || !(player.level().getBlockEntity(packet.pos) instanceof TownSignBlockEntity sign)) return;
        sign.setFaceTexts(packet.front, packet.back);
        var state = sign.getBlockState();
        if (packet.variant != null && state.getValue(TownSignBlock.VARIANT) != packet.variant) {
            player.level().setBlockAndUpdate(packet.pos, state.setValue(TownSignBlock.VARIANT, packet.variant));
        }
    }
}
