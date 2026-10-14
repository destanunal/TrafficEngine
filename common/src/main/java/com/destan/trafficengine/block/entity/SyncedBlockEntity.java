package com.destan.trafficengine.block.entity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
public abstract class SyncedBlockEntity extends BlockEntity {
    protected SyncedBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state) {super(type,pos,state);}
    @Override public CompoundTag getUpdateTag() {return saveWithoutMetadata();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {return ClientboundBlockEntityDataPacket.create(this);}
    public void notifyUpdate() {setChanged(); if (level instanceof ServerLevel server) server.getChunkSource().blockChanged(worldPosition);}
}
