package com.destan.trafficengine.block.entity;

import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import de.mrjulsen.mcdragonlib.block.DLSyncedBlockEntity;
import de.mrjulsen.mcdragonlib.block.IBlockEntityExtension;
import de.mrjulsen.mcdragonlib.network.NetworkDirection;
import de.mrjulsen.mcdragonlib.util.DLUtils;
import com.destan.trafficengine.block.TrafficSignBlock;
import com.destan.trafficengine.data.NamedTrafficSignTextureReference;
import com.destan.trafficengine.data.TrafficSignClientTexture;
import com.destan.trafficengine.data.TrafficSignTextureData;
import com.destan.trafficengine.network.packets.stc.TrafficSignTextureResetPacket;
import com.destan.trafficengine.registry.ModBlockEntities;
import com.destan.trafficengine.registry.ModNetworkManager;
import dev.architectury.utils.GameInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class TrafficSignBlockEntity extends DLSyncedBlockEntity implements IBlockEntityExtension {

    private static final String NBT_LEGACY_TEXTURE = "texture";
    private static final String NBT_TEXTURE = "SignTexture";
    // Only used by DoubleSidedTrafficSignBlock. Normal signs never write this key.
    private static final String NBT_TEXTURE_BACK = "SignTextureBack";

    private String textureId;
    private TrafficSignClientTexture texture;

    private String backTextureId;
    private TrafficSignClientTexture backTexture;


    protected TrafficSignBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public TrafficSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRAFFIC_SIGN_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void load(CompoundTag compound) {
        super.load(compound);
        String newTextureId = null;
        if (compound.contains(NBT_LEGACY_TEXTURE)) {
            migrate(compound.getString(NBT_LEGACY_TEXTURE));
        } else if (compound.contains(NBT_TEXTURE)) {
            newTextureId = compound.getString(NBT_TEXTURE);
        }
        String newBackTextureId = compound.contains(NBT_TEXTURE_BACK) ? compound.getString(NBT_TEXTURE_BACK) : null;
        if (!Objects.equals(textureId, newTextureId) || !Objects.equals(backTextureId, newBackTextureId)) {
            textureId = newTextureId;
            backTextureId = newBackTextureId;
            resetTexture();
        }
    }

    private void migrate(String base64) {
        new Thread(() -> {
            while (getLevel() == null) {
                try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException e) { }
            }
            if (getLevel().isClientSide) return;

            GameInstance.getServer().execute(() -> {
                BlockState state = getLevel().getBlockState(getBlockPos());
                TrafficSignTextureData data = new TrafficSignTextureData(state.getValue(TrafficSignBlock.SHAPE), java.util.Base64.getDecoder().decode(base64), (short)32, (short)32, System.currentTimeMillis(), new UUID(0, 0));
                data.save();
                setTextureId(data.getHash().toString());
            });
        }, "Traffic Sign Migration").start();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        if (textureId != null) {
            tag.putString(NBT_TEXTURE, getTextureId());
        }
        if (backTextureId != null) {
            tag.putString(NBT_TEXTURE_BACK, backTextureId);
        }
        super.saveAdditional(tag);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        resetTexture();
    }

    /* FRONT */

    public String getTextureId() {
        return textureId;
    }

    public TrafficSignClientTexture getClientTexture() {
        if (texture == null) {
            if (getTextureId() == null || getTextureId().equals("empty")) {
                return TrafficSignClientTexture.EMPTY;
            }
            texture = TrafficSignClientTexture.load(getTextureId(), true, null);
        }
        return texture;
    }

    public void setAndResetTexture(NamedTrafficSignTextureReference texture) {
        setTextureId(texture.getTextureId());
        sendTextureResetToClients();
    }

    public void setTextureId(String id) {
        if (!Objects.equals(this.textureId, id)) {
            this.textureId = id;
            resetTexture();
            notifyUpdate();
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    /* BACK (double sided signs) */

    public String getBackTextureId() {
        return backTextureId;
    }

    public TrafficSignClientTexture getBackClientTexture() {
        if (backTexture == null) {
            if (getBackTextureId() == null || getBackTextureId().equals("empty")) {
                return TrafficSignClientTexture.EMPTY;
            }
            backTexture = TrafficSignClientTexture.load(getBackTextureId(), true, null);
        }
        return backTexture;
    }

    public void setAndResetBackTexture(NamedTrafficSignTextureReference texture) {
        setBackTextureId(texture.getTextureId());
        sendTextureResetToClients();
    }

    public void setBackTextureId(String id) {
        if (!Objects.equals(this.backTextureId, id)) {
            this.backTextureId = id;
            resetTexture();
            notifyUpdate();
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    /* SHARED */

    /**
     * Resets the cached client textures of BOTH sides, so the reset packet
     * (which calls this) works for front and back without any changes.
     */
    public void resetTexture() {
        if (level != null && level.isClientSide) {
            TrafficSignClientTexture oldTexture = texture;
            TrafficSignClientTexture oldBackTexture = backTexture;
            texture = null;
            backTexture = null;
            DLUtils.doIfNotNull(oldTexture, x -> x.close());
            if (oldBackTexture != oldTexture) {
                DLUtils.doIfNotNull(oldBackTexture, x -> x.close());
            }
        }
    }

    private void sendTextureResetToClients() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        for (ServerPlayer player : level.players().stream().filter(p -> p instanceof ServerPlayer).toArray(ServerPlayer[]::new)) {
            ModNetworkManager.RESET_TRAFFIC_SIGN_TEXTURE.send(NetworkDirection.toPlayer(player), new TrafficSignTextureResetPacket(getBlockPos()));
        }
    }
}
