package com.destan.trafficengine.data;
import net.minecraft.util.StringRepresentable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
public interface TranslatableEnum extends StringRepresentable {
    record Data(String modid,String enumName,String valueName) {}
    Data getTranslationData();
    default String getSerializedName() { return getTranslationData().valueName(); }
    default MutableComponent getValueTranslation() { Data d=getTranslationData(); return Component.translatable("enum."+d.modid()+"."+d.enumName()+"."+d.valueName()); }
    default MutableComponent getValueDescriptionTranslation() { Data d=getTranslationData(); return Component.translatable("enum."+d.modid()+"."+d.enumName()+".description."+d.valueName()); }
}
