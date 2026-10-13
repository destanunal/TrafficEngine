package com.destan.trafficengine.network;
import net.minecraft.server.level.ServerPlayer;
public interface NetworkDirection {
    NetworkDirection.C2S C2S=new C2S(); NetworkDirection.S2C S2C=new S2C(null);
    record C2S() implements NetworkDirection {}
    record S2C(ServerPlayer player) implements NetworkDirection {}
    static C2S toServer() {return C2S;}
    static S2C toPlayer(ServerPlayer player) {return new S2C(player);}
}
