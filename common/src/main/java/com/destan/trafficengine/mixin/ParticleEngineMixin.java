package com.destan.trafficengine.mixin;

import com.destan.trafficengine.block.BikeLaneSeparatorBlock;
import com.destan.trafficengine.block.ConcreteBarrierBlock;
import com.destan.trafficengine.block.GuardrailBlock;
import com.destan.trafficengine.block.RetractableBarrierBlock;
import com.destan.trafficengine.block.RoadBarrierFenceBlock;
import com.destan.trafficengine.block.TrafficLightBlock;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.destan.trafficengine.block.TrafficSignBlock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
    @Shadow private ClientLevel level;

    @Unique private boolean trafficengine$reduceBarrierParticles;
    @Unique private int trafficengine$barrierParticleIndex;

    @WrapMethod(method = "destroy")
    private void trafficengine$reduceBarrierDestruction(BlockPos pos, BlockState state, Operation<Void> original) {
        boolean previous = trafficengine$reduceBarrierParticles;
        int previousIndex = trafficengine$barrierParticleIndex;
        trafficengine$reduceBarrierParticles = state.getBlock() instanceof RetractableBarrierBlock
            || state.getBlock() instanceof RoadBarrierFenceBlock
            || state.getBlock() instanceof ConcreteBarrierBlock
            || state.getBlock() instanceof GuardrailBlock;
        trafficengine$barrierParticleIndex = 0;
        try {
            original.call(pos, state);
        } finally {
            trafficengine$reduceBarrierParticles = previous;
            trafficengine$barrierParticleIndex = previousIndex;
        }
    }

    // Keep seven out of twenty vanilla particles, distributed evenly through the effect.
    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void trafficengine$skipSomeBarrierParticles(Particle particle, CallbackInfo ci) {
        if (trafficengine$reduceBarrierParticles && (trafficengine$barrierParticleIndex++ % 20) * 7 % 20 >= 7) ci.cancel();
    }

    /** Use the straight variant for break particles without changing selection or collision. */
    @ModifyVariable(method = "destroy", at = @At("STORE"), ordinal = 0)
    private VoxelShape trafficengine$useStraightDestructionShape(VoxelShape shape, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof TrafficLightBlock && state.hasProperty(TrafficLightBlock.DIAGONAL)
                && state.getValue(TrafficLightBlock.DIAGONAL)) {
            return state.setValue(TrafficLightBlock.DIAGONAL, false).getShape(level, pos);
        }
        if (state.getBlock() instanceof TrafficSignBlock
                && (state.getValue(TrafficSignBlock.DIAGONAL) || state.getValue(TrafficSignBlock.POST_MOUNTED))) {
            return state.setValue(TrafficSignBlock.DIAGONAL, false)
                .setValue(TrafficSignBlock.POST_MOUNTED, false).getShape(level, pos);
        }
        if (state.getBlock() instanceof BikeLaneSeparatorBlock && state.getValue(BikeLaneSeparatorBlock.DIAGONAL)) {
            return state.setValue(BikeLaneSeparatorBlock.DIAGONAL, false).getShape(level, pos);
        }
        return shape;
    }
}
