package com.destan.trafficengine.mixin;

import java.util.function.Predicate;

import net.minecraft.client.renderer.block.model.multipart.Selector;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ModelBakery asks each multipart selector for the same predicate once per
 * block state. Patterned slopes have many states, so this otherwise retains
 * millions of equivalent predicate lambdas in their baked models.
 */
@Mixin(Selector.class)
public abstract class MultipartSelectorPredicateCacheMixin {
    @Unique
    private volatile StateDefinition<Block, BlockState> trafficengine$predicateDefinition;

    @Unique
    private volatile Predicate<BlockState> trafficengine$predicate;

    @Inject(method = "getPredicate", at = @At("HEAD"), cancellable = true)
    private void trafficengine$reusePredicate(StateDefinition<Block, BlockState> definition,
            CallbackInfoReturnable<Predicate<BlockState>> callback) {
        Predicate<BlockState> predicate = trafficengine$predicate;
        if (predicate != null && trafficengine$predicateDefinition == definition) {
            callback.setReturnValue(predicate);
        }
    }

    @Inject(method = "getPredicate", at = @At("RETURN"))
    private void trafficengine$rememberPredicate(StateDefinition<Block, BlockState> definition,
            CallbackInfoReturnable<Predicate<BlockState>> callback) {
        trafficengine$predicateDefinition = definition;
        trafficengine$predicate = callback.getReturnValue();
    }
}
