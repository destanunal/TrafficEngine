package com.destan.trafficengine.network;

import java.util.*;
import java.util.function.*;
import com.destan.trafficengine.TrafficEngine;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Small typed channels on the existing Architectury transport. */
public final class NetworkPacketType {
    private static <T extends NetworkPacketData> T decode(CompoundTag tag,Function<PacketStatus,T> factory) {T data=factory.apply(PacketStatus.OK);data.deserializeNbt(tag);return data;}
    private static void dispatch(NetworkDirection direction,ResourceLocation id,FriendlyByteBuf buffer) {
        if (direction instanceof NetworkDirection.C2S) NetworkManager.sendToServer(id,buffer);
        else if (direction instanceof NetworkDirection.S2C s && s.player()!=null) NetworkManager.sendToPlayer(s.player(),id,buffer);
    }
    public static final class Send<D extends NetworkDirection,T extends NetworkPacketData> {
        private final ResourceLocation id;
        public Send(String name,D direction,BiConsumer<T,NetworkPacketContext> handler,Function<PacketStatus,T> factory) {
            id=new ResourceLocation(TrafficEngine.MOD_ID,name);
            if (direction instanceof NetworkDirection.C2S || Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT) {
                NetworkManager.registerReceiver(direction instanceof NetworkDirection.C2S?NetworkManager.c2s():NetworkManager.s2c(),id,(buffer,context)->{
                    CompoundTag tag=buffer.readNbt(); if(tag==null)return;
                    context.queue(()->{try{handler.accept(decode(tag,factory),new NetworkPacketContext(context.getPlayer()));}catch(RuntimeException ex){TrafficEngine.LOGGER.warn("Rejected packet {}",id,ex);}});
                });
            }
        }
        public void send(D direction,T packet) {FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());buffer.writeNbt(packet.serializeNbt());dispatch(direction,id,buffer);}
    }
    public static final class SendAndReceive<D extends NetworkDirection,Q extends NetworkPacketData,R extends NetworkPacketData> {
        private final ResourceLocation requestId,responseId;
        private final Function<PacketStatus,R> responseFactory;
        private final Map<UUID,Pending<R>> pending=new HashMap<>();
        private record Pending<R>(long deadline,Consumer<R> success,Runnable failure) {}
        public SendAndReceive(String name,D direction,BiFunction<Q,NetworkPacketContext,R> handler,Function<PacketStatus,Q> requestFactory,Function<PacketStatus,R> responseFactory) {
            this.responseFactory=responseFactory;
            requestId=new ResourceLocation(TrafficEngine.MOD_ID,name);responseId=new ResourceLocation(TrafficEngine.MOD_ID,name+"_response");
            NetworkManager.registerReceiver(NetworkManager.c2s(),requestId,(buffer,context)->{
                UUID correlation=buffer.readUUID();CompoundTag tag=buffer.readNbt();if(tag==null)return;
                context.queue(()->{
                    if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                    R response;
                    try {response=handler.apply(decode(tag,requestFactory),new NetworkPacketContext(player));}
                    catch (RuntimeException ex) {TrafficEngine.LOGGER.warn("Rejected request {}",requestId,ex);response=responseFactory.apply(PacketStatus.error(ex));}
                    FriendlyByteBuf reply=new FriendlyByteBuf(Unpooled.buffer());reply.writeUUID(correlation);reply.writeNbt(response.serializeNbt());NetworkManager.sendToPlayer(player,responseId,reply);
                });
            });
            if (Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT) {
                NetworkManager.registerReceiver(NetworkManager.s2c(),responseId,(buffer,context)->{
                    UUID correlation=buffer.readUUID();CompoundTag tag=buffer.readNbt();if(tag==null)return;
                    context.queue(()->{Pending<R> callback=pending.remove(correlation);if(callback==null)return;R response=decode(tag,responseFactory);if(response.getStatus().noIssues())callback.success().accept(response);else callback.failure().run();});
                });
                dev.architectury.event.events.client.ClientTickEvent.CLIENT_POST.register(client -> expire());
                dev.architectury.event.events.client.ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {List<Runnable> failures=pending.values().stream().map(Pending::failure).toList();pending.clear();failures.forEach(Runnable::run);});
            }
        }
        private void expire() {if(pending.isEmpty())return;long now=System.nanoTime();Iterator<Pending<R>> iterator=pending.values().iterator();List<Runnable> failures=new ArrayList<>();while(iterator.hasNext()){Pending<R> p=iterator.next();if(now>=p.deadline()){iterator.remove();failures.add(p.failure());}}failures.forEach(Runnable::run);}
        public void send(D direction,Q request,Consumer<R> success,Runnable failure) {
            expire();if(pending.size()>=256){failure.run();return;}
            UUID id=UUID.randomUUID();pending.put(id,new Pending<>(System.nanoTime()+30_000_000_000L,success,failure));
            try{FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());buffer.writeUUID(id);buffer.writeNbt(request.serializeNbt());dispatch(direction,requestId,buffer);}catch(RuntimeException ex){pending.remove(id);failure.run();}
        }
    }
}
