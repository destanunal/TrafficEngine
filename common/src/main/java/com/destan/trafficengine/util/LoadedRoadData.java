package com.destan.trafficengine.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

/** Read existing road data without creating block entities or waiting for chunks. */
public final class LoadedRoadData {
    private LoadedRoadData() {}
    public static LevelChunk chunk(Level world, BlockPos pos) {
        if (world instanceof ServerLevel server) return server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        // ClientChunkCache returns only its current array entry with create=false.
        if (!world.isClientSide) return null;
        var chunk=world.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false);
        return chunk instanceof LevelChunk full?full:null;
    }
    public static BlockEntity blockEntity(BlockGetter world, BlockPos pos) {
        BlockEntity entity;
        if (world instanceof Level level) {
            LevelChunk chunk=chunk(level,pos);entity=chunk==null?null:chunk.getBlockEntities().get(pos);
        } else if (world instanceof LevelChunk chunk) entity=chunk.getBlockEntities().get(pos);
        else entity=world.getBlockEntity(pos); // Render-region snapshots already hold immutable chunk data.
        return entity!=null&&!entity.isRemoved()?entity:null;
    }
    public static BlockState blockState(BlockGetter world, BlockPos pos) {
        if (world instanceof Level level) {
            LevelChunk chunk=chunk(level,pos);return chunk==null?Blocks.AIR.defaultBlockState():chunk.getBlockState(pos);
        }
        return world.getBlockState(pos);
    }
}
