package com.destan.trafficengine.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntity.class)
public class LegacyWorldMigrationMixin {

    // Oyun dÃ¼nyadaki hafÄ±zalarÄ± (BlockEntity) yÃ¼klerken en baÅŸta araya giriyoruz
    @Inject(method = "loadStatic", at = @At("HEAD"))
    private static void migrateLegacyBlockEntities(BlockPos pos, BlockState state, CompoundTag tag, HolderLookup.Provider registries, CallbackInfoReturnable<BlockEntity> cir) {
        if (tag != null && tag.contains("id")) {
            String id = tag.getString("id");
            // EÄŸer veri eski moda aitse, anÄ±nda yeni moda Ã§evir
            if (id.startsWith("trafficcraft:")) {
                tag.putString("id", id.replace("trafficcraft:", "trafficengine:"));
            }
        }
    }
}
