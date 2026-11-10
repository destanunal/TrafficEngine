package com.destan.trafficengine.blueprint.forge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
public final class BlueprintHooksImpl {
 public static boolean place(ServerPlayer player,BlockPos pos,BlockState state) { var snapshot=net.minecraftforge.common.util.BlockSnapshot.create(player.level().dimension(),player.level(),pos);
   return player.level().setBlock(pos,state,2|16) && !net.minecraftforge.event.ForgeEventFactory.onBlockPlace(player,snapshot,net.minecraft.core.Direction.UP); }
 public static boolean canRemove(ServerPlayer player,BlockPos pos,BlockState state){return !net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.BreakEvent(player.level(),pos,state,player));}
}
