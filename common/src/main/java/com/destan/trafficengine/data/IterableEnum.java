package com.destan.trafficengine.data;
public interface IterableEnum<T extends Enum<T>> {
    T[] getValues();
    @SuppressWarnings("unchecked") default T next() { return getValues()[(((T)this).ordinal()+1)%getValues().length]; }
    @SuppressWarnings("unchecked") default T previous() { return getValues()[Math.floorMod(((T)this).ordinal()-1,getValues().length)]; }
}
