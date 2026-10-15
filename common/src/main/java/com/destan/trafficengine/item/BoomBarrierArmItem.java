package com.destan.trafficengine.item;

import com.destan.trafficengine.block.BoomBarrierBlock;
import com.destan.trafficengine.block.BoomBarrierPartBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import java.util.List;

/** Adds one reusable arm module to an existing motor; never places another ticking block. */
public class BoomBarrierArmItem extends Item {
    public BoomBarrierArmItem(){super(new Item.Properties());}
    @Override public InteractionResult useOn(UseOnContext context) {
        var player=context.getPlayer();if(player==null)return InteractionResult.PASS;
        var level=context.getLevel();var pos=context.getClickedPos();var state=level.getBlockState(pos);
        if(state.getBlock() instanceof BoomBarrierPartBlock){pos=BoomBarrierPartBlock.masterPos(pos,state);state=level.getBlockState(pos);}
        return state.getBlock() instanceof BoomBarrierBlock?BoomBarrierBlock.addArm(level,pos,state,player,context.getHand()):InteractionResult.PASS;
    }
    @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> lines,TooltipFlag flag) {
        super.appendHoverText(stack,context,lines,flag);
        lines.add(Component.translatable("item.trafficengine.boom_barrier_arm.tooltip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
