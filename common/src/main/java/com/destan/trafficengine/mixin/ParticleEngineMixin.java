package com.destan.trafficengine.mixin;

import com.destan.trafficengine.block.BikeLaneSeparatorBlock;
import com.destan.trafficengine.block.BoomBarrierBlock;
import com.destan.trafficengine.block.BoomBarrierPartBlock;
import com.destan.trafficengine.block.AsphaltSpeedBumpBlock;
import com.destan.trafficengine.block.TrafficIslandBollardBlock;
import com.destan.trafficengine.registry.ModBlockTags;
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

    @Unique private int trafficengine$breakParticleNumerator = 1;
    @Unique private int trafficengine$breakParticleDenominator = 1;
    @Unique private int trafficengine$breakParticleIndex;

    @WrapMethod(method = "destroy")
    private void trafficengine$reduceDestruction(BlockPos pos, BlockState state, Operation<Void> original) {
        int previousNumerator = trafficengine$breakParticleNumerator;
        int previousDenominator = trafficengine$breakParticleDenominator;
        int previousIndex = trafficengine$breakParticleIndex;
        trafficengine$breakParticleNumerator = 1;
        trafficengine$breakParticleDenominator = 1;
        if (state.getBlock() instanceof RetractableBarrierBlock
                || state.getBlock() instanceof RoadBarrierFenceBlock
                || state.getBlock() instanceof ConcreteBarrierBlock
                || state.getBlock() instanceof GuardrailBlock) {
            trafficengine$breakParticleNumerator = 7;
            trafficengine$breakParticleDenominator = 20;
        } else if (state.getBlock() instanceof BoomBarrierBlock) {
            trafficengine$breakParticleNumerator = 9;
            trafficengine$breakParticleDenominator = 32;
        } else if (state.getBlock() instanceof TrafficIslandBollardBlock
                || state.getBlock() instanceof BoomBarrierPartBlock) {
            trafficengine$breakParticleDenominator = 2;
        } else if (state.is(ModBlockTags.REDUCED_BREAK_PARTICLES)) {
            trafficengine$breakParticleNumerator = 3;
            trafficengine$breakParticleDenominator = 5;
        }
        trafficengine$breakParticleIndex = 0;
        try {
            original.call(pos, state);
        } finally {
            trafficengine$breakParticleNumerator = previousNumerator;
            trafficengine$breakParticleDenominator = previousDenominator;
            trafficengine$breakParticleIndex = previousIndex;
        }
    }

    // Distribute the retained particles evenly for each configured reduction.
    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void trafficengine$skipSomeBreakParticles(Particle particle, CallbackInfo ci) {
        if (trafficengine$breakParticleDenominator == 1) return;
        int index = trafficengine$breakParticleIndex++ % trafficengine$breakParticleDenominator;
        if (index * trafficengine$breakParticleNumerator % trafficengine$breakParticleDenominator
                >= trafficengine$breakParticleNumerator) ci.cancel();
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
        if (state.getBlock() instanceof BikeLaneSeparatorBlock) {
            return BikeLaneSeparatorBlock.destructionShape(state);
        }
        if (state.getBlock() instanceof AsphaltSpeedBumpBlock) {
            return AsphaltSpeedBumpBlock.destructionShape(state);
        }
        return shape;
    }
}
