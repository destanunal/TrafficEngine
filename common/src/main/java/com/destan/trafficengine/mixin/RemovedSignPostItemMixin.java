package com.destan.trafficengine.mixin;

import com.destan.trafficengine.util.SignPostMigration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class RemovedSignPostItemMixin {
    @Inject(method="of", at=@At("HEAD"))
    private static void migrate(CompoundTag tag, CallbackInfoReturnable<ItemStack> callback) {
        SignPostMigration.item(tag);
    }
}
