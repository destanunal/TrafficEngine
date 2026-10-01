package com.destan.trafficengine.util;
public final class MutablePair<A,B> {
    private final A first; private B second;
    public MutablePair(A first,B second) {this.first=first;this.second=second;}
    public A getFirst() {return first;}
    public B getSecond() {return second;}
    public void setSecond(B value) {second=value;}
}
