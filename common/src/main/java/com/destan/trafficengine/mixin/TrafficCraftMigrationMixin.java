package com.destan.trafficengine.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntity.class)
public class TrafficCraftMigrationMixin {

    // Oyun dünyadaki hafızaları (BlockEntity) yüklerken en başta araya giriyoruz
    @Inject(method = "loadStatic", at = @At("HEAD"))
    private static void migrateTrafficCraftBlockEntities(BlockPos pos, BlockState state, CompoundTag tag, CallbackInfoReturnable<BlockEntity> cir) {
        if (tag != null && tag.contains("id")) {
            String id = tag.getString("id");
            // Eğer veri eski moda aitse, anında yeni moda çevir
            if (id.startsWith("trafficcraft:")) {
                tag.putString("id", id.replace("trafficcraft:", "trafficengine:"));
            }
        }
    }
}