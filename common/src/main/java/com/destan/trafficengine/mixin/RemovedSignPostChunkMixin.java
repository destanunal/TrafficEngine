package com.destan.trafficengine.mixin;

import com.destan.trafficengine.util.SignPostMigration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
public class RemovedSignPostChunkMixin {
    @Inject(method="read", at=@At("HEAD"))
    private static void migrate(ServerLevel level, PoiManager poi, net.minecraft.world.level.chunk.storage.RegionStorageInfo storage, ChunkPos pos, CompoundTag tag,
            CallbackInfoReturnable<ProtoChunk> callback) {
        SignPostMigration.chunk(tag);
    }
}
