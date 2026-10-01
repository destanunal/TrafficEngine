package com.destan.trafficengine.client.ber;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
public record RenderContext<T extends BlockEntity>(T blockEntity,PoseStack poseStack,MultiBufferSource multiBufferSource,int packedLight,int packedOverlay,float partialTick) {}
