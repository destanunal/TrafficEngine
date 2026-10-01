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
    public WorldLocation(double x, double y, double z, ResourceLocation dimension) { this.x=x; this.y=y; this.z=z; this.dimension=dimension; }
    public WorldLocation(double x, double y, double z, Level level) { this(x,y,z,level.dimension().location()); }
    public WorldLocation(BlockPos pos, Level level) { this(pos.getX(),pos.getY(),pos.getZ(),level); }
    public BlockPos getLocationBlockPos() { return new BlockPos((int)x,(int)y,(int)z); }
    public Vec3 getLocationVec3() { return new Vec3(x,y,z); }
    public CompoundTag toNbt() { CompoundTag t=new CompoundTag(); t.putDouble("x",x); t.putDouble("y",y); t.putDouble("z",z); t.putString("dimension",dimension.toString()); return t; }
    public static WorldLocation loadFromNbt(CompoundTag t) { return new WorldLocation(t.getDouble("x"),t.getDouble("y"),t.getDouble("z"),new ResourceLocation(t.getString("dimension"))); }
    @Override public boolean equals(Object obj) { return obj instanceof WorldLocation p && x==p.x && y==p.y && z==p.z && dimension.equals(p.dimension); }
    @Override public int hashCode() { return java.util.Objects.hash(x,y,z,dimension); }
}
