package com.destan.trafficengine.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The existing lowercase coordinate and dimension keys remain world compatible. */
public final class WorldLocation {
    public final double x, y, z;
    public final ResourceLocation dimension;
    public final int signalFace;
    private final BlockPos blockPos;
    public WorldLocation(double x, double y, double z, ResourceLocation dimension) { this(x,y,z,dimension,0); }
    private WorldLocation(double x,double y,double z,ResourceLocation dimension,int face){this.x=x;this.y=y;this.z=z;this.dimension=dimension;this.signalFace=face==1?1:0;this.blockPos=new BlockPos((int)x,(int)y,(int)z);}
    public WorldLocation withSignalFace(int face){return face==signalFace?this:new WorldLocation(x,y,z,dimension,face);}
    public WorldLocation(double x, double y, double z, Level level) { this(x,y,z,level.dimension().location()); }
    public WorldLocation(BlockPos pos, Level level) { this(pos.getX(),pos.getY(),pos.getZ(),level); }
    public BlockPos getLocationBlockPos() { return blockPos; }
    public Vec3 getLocationVec3() { return new Vec3(x,y,z); }
    public CompoundTag toNbt() { CompoundTag t=new CompoundTag(); t.putDouble("x",x); t.putDouble("y",y); t.putDouble("z",z); t.putString("dimension",dimension.toString()); if(signalFace!=0)t.putByte("SignalFace",(byte)signalFace); return t; }
    public static WorldLocation loadFromNbt(CompoundTag t) { return new WorldLocation(t.getDouble("x"),t.getDouble("y"),t.getDouble("z"),new ResourceLocation(t.getString("dimension"))).withSignalFace(t.getByte("SignalFace")); }
    @Override public boolean equals(Object obj) { return obj instanceof WorldLocation p && x==p.x && y==p.y && z==p.z && dimension.equals(p.dimension) && signalFace==p.signalFace; }
    @Override public int hashCode() { return 31 * java.util.Objects.hash(x,y,z,dimension) + signalFace; }
}
