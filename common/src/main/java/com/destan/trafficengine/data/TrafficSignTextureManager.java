package com.destan.trafficengine.data;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.data.NamedTrafficSignTextureReference.BuildInTrafficSignCodec;
import dev.architectury.utils.GameInstance;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.storage.LevelResource;

public class TrafficSignTextureManager {

    public static final String FILE_EXTENSION = "nbt";
    public static final String PATH = "data/" + TrafficEngine.MOD_ID + "_signs";

    public static LevelResource getResource(String filename) {
        return new LevelResource(String.format("%s/%s.%s", PATH, filename, FILE_EXTENSION));
    }
    
    public static TrafficSignTextureData load(String id) {

        if (id.startsWith(BuildInTrafficSignCodec.PREFIX)) {
            BuildInTrafficSignCodec codec = BuildInTrafficSignCodec.decode(id);
            return new TrafficSignTextureData(codec.shape(), new byte[0], codec.width(), codec.height(), System.currentTimeMillis(), new UUID(0, 0));
        }

        File file = new File(GameInstance.getServer().getWorldPath(getResource(id.toString())).toString());    
        if (file.exists()) {
            try {
                return TrafficSignTextureData.deserializeNbt(NbtIo.readCompressed(file));
            } catch (IOException e) {
                TrafficEngine.LOGGER.error("The traffic sign texture with id " + id + " could not be loaded.", e);
            }
        } else {
            TrafficEngine.LOGGER.error("A traffic sign texture with id " + id + " does not exist.");
        }
        return TrafficSignTextureData.empty();
    }
}
