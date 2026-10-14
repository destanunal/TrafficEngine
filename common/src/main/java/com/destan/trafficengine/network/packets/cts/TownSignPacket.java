package com.destan.trafficengine.network.packets.cts;

import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.network.NetworkPacketContext;
import com.destan.trafficengine.network.NetworkPacketData;
import com.destan.trafficengine.util.NbtPositions;
import com.destan.trafficengine.block.TownSignBlock;
import com.destan.trafficengine.block.data.TownSignVariant;
import com.destan.trafficengine.block.entity.TownSignBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

public class TownSignPacket extends NetworkPacketData {

    private static final String NBT_MESSAGES = "Messages";
    private static final String NBT_VARIANT = "Variant";
    private static final String NBT_POS = "Pos";
    private static final String NBT_SIDE = "Side";

    private String[] messages;
    private TownSignVariant variant;
    private BlockPos pos;    
    private TownSignBlock.ETownSignSide side;

    public TownSignPacket(PacketStatus status) {
        super(status);
    }

    public TownSignPacket(BlockPos pos, String[] messages, TownSignVariant variant, TownSignBlock.ETownSignSide side) {
        super(PacketStatus.OK);
        this.pos = pos;
        this.variant = variant;
        this.messages = messages;
        this.side = side;
    }

    @Override
    protected void write(CompoundTag nbt) {
        ListTag msgs = new ListTag();
        for (String msg : messages) {
            msgs.add(StringTag.valueOf(msg));
        }
        nbt.put(NBT_MESSAGES, msgs);
        nbt.putInt(NBT_VARIANT, variant.getIndex());
        NbtPositions.putNbtPos(nbt, NBT_POS, pos);
        nbt.putInt(NBT_SIDE, side.getIndex());
    }

    @Override
    protected void read(CompoundTag nbt) {
        this.messages = nbt.getList(NBT_MESSAGES, Tag.TAG_STRING).stream().map(x -> ((StringTag)x).getAsString()).toArray(String[]::new);
        this.variant = TownSignVariant.getVariantByIndex(nbt.getInt(NBT_VARIANT));
        this.pos = NbtPositions.getNbtBlockPos(nbt, NBT_POS);
        this.side = TownSignBlock.ETownSignSide.getSideByIndex(nbt.getInt(NBT_SIDE));
    }
    
    public static void handle(TownSignPacket packet, NetworkPacketContext context) {        
        ServerPlayer sender = (ServerPlayer)context.getPlayer();
        if (sender.level().getBlockState(packet.pos).getBlock() instanceof TownSignBlock && sender.level().getBlockEntity(packet.pos) instanceof TownSignBlockEntity blockEntity) {
            switch (packet.side) {
                case BACK:
                    blockEntity.setBackTexts(packet.messages);
                    break;
                default:
                case FRONT:
                    blockEntity.setTexts(packet.messages);
                    break;
            }
            BlockState state = sender.level().getBlockState(packet.pos);
            sender.level().setBlockAndUpdate(packet.pos, state.setValue(TownSignBlock.VARIANT, packet.variant));
        }
    }
}
