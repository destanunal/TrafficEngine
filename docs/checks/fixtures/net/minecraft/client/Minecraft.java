package net.minecraft.client;
/** Test-only environment: production favorites code only needs its config directory. */
public final class Minecraft {
    private static final Minecraft INSTANCE=new Minecraft();
    public final java.io.File gameDirectory=new java.io.File(System.getProperty("trafficengine.scratch"));
    public static Minecraft getInstance(){return INSTANCE;}
}
