package com.destan.trafficengine.data;

import java.io.Closeable;
import java.util.Base64;
import java.util.UUID;

import com.mojang.blaze3d.platform.NativeImage;

import com.destan.trafficengine.data.Identifiable;
import com.destan.trafficengine.network.NetworkDirection;
import net.minecraft.network.chat.Component;
import com.destan.trafficengine.block.data.TrafficSignShape;
import com.destan.trafficengine.network.packets.cts.CreateNewTrafficSignTexturePacket;
import com.destan.trafficengine.registry.ModNetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.nbt.CompoundTag;

@Deprecated
public class TrafficSignData implements Closeable, Identifiable {

    private static final String NBT_WIDTH = "width";
    private static final String NBT_HEIGHT = "height";
    private static final String NBT_SHAPE = "shape";
    private static final String NBT_NAME = "name";
    private static final String NBT_PIXEL_DATA = "pixelData";

    private final String ID;

    private final int width;
    private final int height;
    private final TrafficSignShape shape;
    private String name = ""; 

    private String texture;
  

    public TrafficSignData(int width, int height, TrafficSignShape shape) {
        this.width = width;
        this.height = height;
        this.shape = shape;

        ID = String.valueOf(System.nanoTime());
    }

    @Override
    public String getId() {
        return ID;
    }

    public String getTexture() {
        return texture;
    }

    public TrafficSignShape getShape() {
        return shape;
    }

    public String getName() {
        return name == null || name.isEmpty() ? Component.translatable("gui.trafficengine.trafficsignworkbench.pattern.name_unknown").getString(): name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void clearImage(NativeImage texture) {
        texture.fillRect(0, 0, width, height, 0);
    }

    public void setFromBase64(String base64) {
        texture = base64;
    }


    /* DATA STORAGE */

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(NBT_WIDTH, width);
        tag.putInt(NBT_HEIGHT, height);
        tag.putInt(NBT_SHAPE, shape.getIndex());
        tag.putString(NBT_NAME, name);
        tag.putString(NBT_PIXEL_DATA, texture);
        return tag;
    }

    public static TrafficSignData fromNbt(CompoundTag tag) {
        TrafficSignData data = new TrafficSignData(tag.getInt(NBT_WIDTH), tag.getInt(NBT_HEIGHT), TrafficSignShape.getShapeByIndex(tag.getInt(NBT_SHAPE)));
        data.setName(tag.getString(NBT_NAME));
        data.setFromBase64(tag.getString(NBT_PIXEL_DATA));
        return data;
    }
    
    @Override
    public void close() {
    }


    public static NamedTrafficSignTextureReference migrate(CompoundTag nbt) {
        TrafficSignData src = TrafficSignData.fromNbt(nbt);
        TrafficSignTextureData data = new TrafficSignTextureData(src.getShape(), Base64.getDecoder().decode(src.getTexture()), (short)src.getWidth(), (short)src.getHeight(), System.currentTimeMillis(), new UUID(0, 0));
        if (Platform.getEnvironment() == Env.CLIENT) {
            ModNetworkManager.CREATE_NEW_TRAFFIC_SIGN_TEXTURE.send(NetworkDirection.toServer(), new CreateNewTrafficSignTexturePacket.Request(data), (response) -> {}, () -> {});
        } else {
            data.save();
        }
        String name = src.getName();
        src.close();
        return NamedTrafficSignTextureReference.of(data, name);
    }
}
