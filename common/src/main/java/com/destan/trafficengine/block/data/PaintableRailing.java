package com.destan.trafficengine.block.data;

import com.destan.trafficengine.block.entity.RailingPaintBlockEntity;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.item.BrushItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public interface PaintableRailing extends IPaintableBlock, EntityBlock {
    static void normalizePaintVariant(Level world, BlockPos pos, BlockState state) {
        if (state.getValue(com.destan.trafficengine.block.PedestrianRailingBlock.GRAY)
                && world.getBlockEntity(pos) instanceof RailingPaintBlockEntity paint
                && paint.getColor() != PaintColor.NONE)
            world.setBlockAndUpdate(pos,state.setValue(com.destan.trafficengine.block.PedestrianRailingBlock.GRAY,false));
    }
    @Override default BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RailingPaintBlockEntity(pos, state);
    }
    @Override default InteractionResult onSetColor(UseOnContext context) {
        Level world = context.getLevel(); Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (player == null || !player.mayBuild() || !world.mayInteract(player, pos)) return InteractionResult.FAIL;
        if (world.isClientSide) return InteractionResult.CONSUME;
        if (!(world.getBlockEntity(pos) instanceof RailingPaintBlockEntity paint)) return InteractionResult.FAIL;
        paint.setColor(BrushItem.getColor(context.getItemInHand()));
        world.playSound(null, pos, net.minecraft.sounds.SoundEvents.SLIME_BLOCK_PLACE,
            net.minecraft.sounds.SoundSource.BLOCKS, .8F, 2F);
        return InteractionResult.CONSUME;
    }
    @Override default void onRemoveColor(BlockState state, Level world, BlockPos pos, Player player) {
        if (player.getMainHandItem().getItem() instanceof BrushItem && !world.isClientSide && player.mayBuild() && world.mayInteract(player, pos)
                && world.getBlockEntity(pos) instanceof RailingPaintBlockEntity paint)
            paint.setColor(PaintColor.NONE);
    }
}
