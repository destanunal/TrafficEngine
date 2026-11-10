package com.destan.trafficengine.blueprint;

import com.destan.trafficengine.client.ClientWrapper;
import com.destan.trafficengine.util.ItemData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import java.util.List;

public final class BlueprintToolItem extends Item {
    public BlueprintToolItem(){super(new Properties().stacksTo(1));}
    public static Component text(String key,Object...args){return Component.translatable("gui.trafficengine.blueprint."+key,args);}
    public static CompoundTag data(ItemStack stack){CompoundTag tag=ItemData.get(stack);return singlePlan(tag==null?new CompoundTag():tag);}
    /** Keep the previously active plan when upgrading a tool from the four-slot UI. */
    public static CompoundTag singlePlan(CompoundTag tag){
        int previous=net.minecraft.util.Mth.clamp(tag.getInt("Slot"),0,3);
        if(previous!=0&&tag.contains("Plan"+previous))tag.put("Plan0",tag.getCompound("Plan"+previous));
        for(int i=1;i<4;i++)tag.remove("Plan"+i);
        tag.putInt("Slot",0);return tag;
    }
    public static void clearSelection(CompoundTag tag){
        tag.remove("First");tag.remove("Second");tag.remove("SelectionDimension");tag.remove("Plan0");
        tag.putInt("Revision",tag.getInt("Revision")+1);tag.putBoolean("Placing",false);
    }
    /** Return true only when the matching second corner is ready for server capture. */
    public static boolean selectCorner(CompoundTag tag,BlockPos pos,String dimension){
        boolean second=tag.contains("First")&&!tag.contains("Second")&&dimension.equals(tag.getString("SelectionDimension"));
        if(second)tag.putLong("Second",pos.asLong());
        else{clearSelection(tag);tag.putLong("First",pos.asLong());tag.putString("SelectionDimension",dimension);}
        tag.putBoolean("Placing",false);return second;
    }
    public static int slot(CompoundTag tag){return net.minecraft.util.Mth.clamp(tag.getInt("Slot"),0,BlueprintPlan.SLOTS-1);}
    public static BlueprintPlan selected(CompoundTag tag){return BlueprintPlan.load(tag.getCompound("Plan"+slot(tag)));}
    public static int copies(CompoundTag tag){return net.minecraft.util.Mth.clamp(tag.getInt("Copies"),1,BlueprintPlan.MAX_COPIES);}
    public static int spacing(CompoundTag tag){return tag.contains("Spacing")?net.minecraft.util.Mth.clamp(tag.getInt("Spacing"),0,BlueprintPlan.MAX_SPACING):8;}
    // Keep the four historical horizontal values when loading existing tools.
    public static net.minecraft.core.Direction direction(int value){return value==4?net.minecraft.core.Direction.UP:value==5?net.minecraft.core.Direction.DOWN:net.minecraft.core.Direction.from2DDataValue(value&3);}
    public static int mode(CompoundTag tag){return net.minecraft.util.Mth.clamp(tag.getInt("PlacementMode"),0,2);}
    public static BlockPos anchor(CompoundTag tag,BlockPos clicked,net.minecraft.core.Direction face){return mode(tag)==2?clicked:clicked.relative(face);}
    public static void message(Player p,String key,Object...args){p.displayClientMessage(text(key,args),true);}
    @Override public InteractionResultHolder<ItemStack> use(Level world,Player player,InteractionHand hand){
        if(player.isShiftKeyDown()){
            if(!world.isClientSide){ItemStack stack=player.getItemInHand(hand);CompoundTag tag=data(stack);clearSelection(tag);ItemData.set(stack,tag);message(player,"selection_cleared");}
        }else if(world.isClientSide)ClientWrapper.showBlueprintScreen(player.getItemInHand(hand));
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),world.isClientSide);
    }
    @Override public InteractionResult useOn(UseOnContext context){
        Player player=context.getPlayer();if(player==null)return InteractionResult.PASS;
        if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
        if(!(player instanceof ServerPlayer server)||!player.mayBuild())return InteractionResult.FAIL;
        ItemStack stack=context.getItemInHand();CompoundTag tag=data(stack);
        if(player.isShiftKeyDown()){
            clearSelection(tag);ItemData.set(stack,tag);message(player,"selection_cleared");
        }else if(!tag.getBoolean("Placing")){
            BlockPos pos=context.getClickedPos();if(!context.getLevel().mayInteract(player,pos))return InteractionResult.FAIL;
            String dim=context.getLevel().dimension().location().toString();
            boolean ready=selectCorner(tag,pos,dim);ItemData.set(stack,tag);
            if(ready){BlueprintPlacement.capture(server,stack);server.containerMenu.broadcastChanges();}
            else message(player,"first",pos.getX(),pos.getY(),pos.getZ());
        }else BlueprintPlacement.start(server,stack,anchor(tag,context.getClickedPos(),context.getClickedFace()));
        return InteractionResult.SUCCESS;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags){
        lines.add(text("usage").copy().withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
