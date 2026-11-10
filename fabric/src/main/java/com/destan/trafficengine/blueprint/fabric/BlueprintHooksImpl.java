package com.destan.trafficengine.blueprint.fabric;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
public final class BlueprintHooksImpl {
 public static boolean place(ServerPlayer player,BlockPos pos,BlockState state) { return !dev.architectury.event.events.common.BlockEvent.PLACE.invoker().placeBlock(player.level(),pos,state,player).isFalse() && player.level().setBlock(pos,state,2|16); }
 public static boolean canRemove(ServerPlayer player,BlockPos pos,BlockState state){return net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(player.level(),player,pos,state,player.level().getBlockEntity(pos));}
}
