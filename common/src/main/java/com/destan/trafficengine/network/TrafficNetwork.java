package com.destan.trafficengine.network;
import java.util.function.*;
public final class TrafficNetwork {
    public TrafficNetwork() {}
    public <D extends NetworkDirection,T extends NetworkPacketData> NetworkPacketType.Send<D,T> registerSendOnlyPacket(String id,D direction,BiConsumer<T,NetworkPacketContext> handler,Function<PacketStatus,T> factory) {return new NetworkPacketType.Send<>(id,direction,handler,factory);}
    public <D extends NetworkDirection,Q extends NetworkPacketData,R extends NetworkPacketData> NetworkPacketType.SendAndReceive<D,Q,R> registerSendAndReceivePacket(String id,D direction,BiFunction<Q,NetworkPacketContext,R> handler,Function<PacketStatus,Q> requestFactory,Function<PacketStatus,R> responseFactory) {return new NetworkPacketType.SendAndReceive<>(id,direction,handler,requestFactory,responseFactory);}
}
