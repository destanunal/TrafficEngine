package com.destan.trafficengine.mixin;

import com.destan.trafficengine.block.TrafficLightBlock;
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
        return shape;
    }
}
