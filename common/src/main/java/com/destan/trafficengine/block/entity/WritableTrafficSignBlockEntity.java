package com.destan.trafficengine.block.entity;
import java.util.Arrays;
import com.destan.trafficengine.data.SignTextConfig.WritableSignConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
public abstract class WritableTrafficSignBlockEntity extends SyncedBlockEntity {
    private String[] lines;
    protected WritableTrafficSignBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state) {super(type,pos,state);}
    public abstract WritableSignConfig getRenderConfig();
    private void initLines() {if (lines==null) {lines=new String[getRenderConfig().lineData().length]; Arrays.fill(lines,"");}}
    public String getText(int line) {initLines(); return line>=0 && line<lines.length ? lines[line] : "";}
    public void setText(String text,int line) {initLines(); if (line<0 || line>=lines.length) return; lines[line]=text==null?"":text; notifyUpdate();}
    public void setTexts(String[] messages) {initLines(); for(int i=0;i<lines.length;i++) lines[i]=i<messages.length && messages[i]!=null?messages[i]:""; notifyUpdate();}
    @Override public void load(CompoundTag tag) {super.load(tag); initLines(); for(int i=0;i<lines.length;i++) lines[i]=tag.getString("line"+i);}
    @Override protected void saveAdditional(CompoundTag tag) {super.saveAdditional(tag); initLines(); for(int i=0;i<lines.length;i++) tag.putString("line"+i,lines[i]);}
}
