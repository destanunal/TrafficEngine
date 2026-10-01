package com.destan.trafficengine.util;
import com.destan.trafficengine.data.TranslatableEnum;
public enum ETimeFormat implements TranslatableEnum {
    HOURS_24(1,"hours_24"),HOURS_12(2,"hours_12"),TICKS(0,"ticks");
    private final int index;private final String name;
    ETimeFormat(int index,String name) {this.index=index;this.name=name;}
    public int getIndex() {return index;}
    public String getName() {return name;}
    public static ETimeFormat getByIndex(int index) {for(var format:values())if(format.index==index)return format;return HOURS_24;}
    @Override public Data getTranslationData() {return new Data("trafficengine","time_format",name);}
}
