package com.destan.trafficengine.blueprint;

import com.destan.trafficengine.network.*;
import com.destan.trafficengine.util.ItemData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Commands carry no block palette or entity data; snapshots are authored on the server. */
public final class BlueprintPacket extends NetworkPacketData {
    private int action,rotation,copies,spacing,direction,mode;
    public BlueprintPacket(PacketStatus status){super(status);}
    public BlueprintPacket(int action,int rotation,int copies,int spacing,int direction){this(action,rotation,copies,spacing,direction,0);}
    public BlueprintPacket(int action,int rotation,int copies,int spacing,int direction,int mode){super(PacketStatus.OK);this.action=action;this.rotation=rotation;this.copies=copies;this.spacing=spacing;this.direction=direction;this.mode=mode;}
    @Override protected void write(CompoundTag t){t.putInt("Action",action);t.putInt("Rotation",rotation);t.putInt("Copies",copies);t.putInt("Spacing",spacing);t.putInt("Direction",direction);t.putInt("PlacementMode",mode);}
    @Override protected void read(CompoundTag t){action=t.getInt("Action");rotation=t.getInt("Rotation");copies=t.getInt("Copies");spacing=t.getInt("Spacing");direction=t.getInt("Direction");mode=t.getInt("PlacementMode");}
    public static void handle(BlueprintPacket packet,NetworkPacketContext context){
        if(!(context.getPlayer() instanceof ServerPlayer player)||!player.mayBuild()||player.isSpectator())return;
        ItemStack stack=player.getMainHandItem();if(!(stack.getItem() instanceof BlueprintToolItem))stack=player.getOffhandItem();if(!(stack.getItem() instanceof BlueprintToolItem))return;
        if(packet.rotation<0||packet.rotation>3||packet.copies<1||packet.copies>BlueprintPlan.MAX_COPIES||packet.spacing<0||packet.spacing>BlueprintPlan.MAX_SPACING||packet.direction<0||packet.direction>5||packet.mode<0||packet.mode>2||packet.action<0||packet.action>5||packet.action==2||packet.action==3)return;
        CompoundTag tag=BlueprintToolItem.data(stack);tag.putInt("Rotation",packet.rotation);tag.putInt("Copies",packet.copies);tag.putInt("Spacing",packet.spacing);tag.putInt("Direction",packet.direction);tag.putInt("PlacementMode",packet.mode);
        if(!tag.contains("Plan0"))tag.putBoolean("Placing",false);
        if(packet.action==0)BlueprintToolItem.clearSelection(tag);
        if(packet.action==1)tag.putBoolean("Placing",BlueprintToolItem.selected(tag)!=null);
        ItemData.set(stack,tag);
        if(packet.action==5)BlueprintPlacement.undo(player,stack);
        player.containerMenu.broadcastChanges();
    }
}
