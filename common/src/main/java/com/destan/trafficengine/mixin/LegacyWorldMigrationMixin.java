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
public class LegacyWorldMigrationMixin {

    // Oyun dÃ¼nyadaki hafÄ±zalarÄ± (BlockEntity) yÃ¼klerken en baÅŸta araya giriyoruz
    @Inject(method = "loadStatic", at = @At("HEAD"), cancellable = true)
    private static void migrateLegacyBlockEntities(BlockPos pos, BlockState state, CompoundTag tag, CallbackInfoReturnable<BlockEntity> cir) {
        if (tag != null && tag.contains("id")) {
            String id = tag.getString("id");
            // EÄŸer veri eski moda aitse, anÄ±nda yeni moda Ã§evir
            if (id.startsWith("trafficcraft:")) {
                id = "trafficengine:" + id.substring("trafficcraft:".length());
                tag.putString("id", id);
            }
            if (id.startsWith("trafficengine:")) {
                var key = net.minecraft.resources.ResourceLocation.tryParse(id);
                var type = key == null ? java.util.Optional.<net.minecraft.world.level.block.entity.BlockEntityType<?>>empty()
                    : net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(key);
                if (type.isPresent() && !type.get().isValid(state)) cir.setReturnValue(null);
            }
        }
    }
}
