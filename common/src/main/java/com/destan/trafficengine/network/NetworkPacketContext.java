package com.destan.trafficengine.network;
import net.minecraft.world.entity.player.Player;
public record NetworkPacketContext(Player player) {public Player getPlayer() {return player;}}
