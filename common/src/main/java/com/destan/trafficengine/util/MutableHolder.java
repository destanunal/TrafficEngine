package com.destan.trafficengine.util;
public final class MutableHolder<T> {
    private T value;
    public MutableHolder(T value) {this.value=value;}
    public T get() {return value;}
    public void set(T value) {this.value=value;}
}
