package com.destan.trafficengine.blueprint;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
/** Loader-native placement cancellation hooks, including server claim integrations. */
public final class BlueprintHooks {
 private BlueprintHooks(){}
 @ExpectPlatform public static boolean place(ServerPlayer player,BlockPos pos,BlockState state){throw new AssertionError();}
 @ExpectPlatform public static boolean canRemove(ServerPlayer player,BlockPos pos,BlockState state){throw new AssertionError();}
}
