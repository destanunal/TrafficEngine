package com.destan.trafficengine.block.entity;

import com.destan.trafficengine.block.RoadInformationSignBlock;
import com.destan.trafficengine.block.RoadInformationSignBlock.Kind;
import com.destan.trafficengine.registry.ModBlockEntities;
import com.destan.trafficengine.data.RoadSignDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

public class RoadInformationSignBlockEntity extends SyncedBlockEntity {
    private final String[] distances = {"", "", ""};
    private final String[] destinations = {"", "", ""};
    private int[] arrows = {0, 0, 0};
    private String route = "";
    private int theme;
    private int speed = 70;
    private boolean enabled = true;
    private RoadSignDesign design = RoadSignDesign.DEFAULT;
    public static final int JOIN_LEFT = 1, JOIN_RIGHT = 2;
    private int horizontalConnections = -1;
    public RoadInformationSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROAD_INFORMATION_SIGN_BLOCK_ENTITY.get(), pos, state);
        if (kind() == Kind.DIRECTION) design = design.withRows(1);
    }
    public Kind kind() { return ((RoadInformationSignBlock)getBlockState().getBlock()).kind(); }
    public String destination(int index) { return destinations[index]; }
    /** Read-only render view, reused until the next configuration update. */
    public String[] distances() { return distances; }
    public String distance(int index) { return distances[index]; }
    public int arrow(int index) { return arrows[index]; }
    public String route() { return route; }
    public int theme() { return theme; }
    public int speed() { return speed; }
    public boolean enabled() { return enabled; }
    public RoadSignDesign design() { return design; }
    public int displayedSpeed() { return speed; }

    /** The plate is two blocks wide. Cache only its two immediate touching neighbors. */
    public int horizontalConnections() {
        if (horizontalConnections < 0 && level != null)
            horizontalConnections = findHorizontalConnections(level);
        return Math.max(0, horizontalConnections);
    }
    int findHorizontalConnections(BlockGetter world) {
        if (kind() == Kind.VARIABLE_SPEED) return 0;
        Direction facing = getBlockState().getValue(RoadInformationSignBlock.FACING);
        int result = 0;
        if (joins(world, worldPosition.relative(facing.getClockWise(), 2))) result |= JOIN_LEFT;
        if (joins(world, worldPosition.relative(facing.getCounterClockWise(), 2))) result |= JOIN_RIGHT;
        return result;
    }
    private boolean joins(BlockGetter world, BlockPos pos) {
        BlockState state = getBlockState(), other = world.getBlockState(pos);
        return other.getBlock() == state.getBlock()
            && other.getValue(RoadInformationSignBlock.FACING) == state.getValue(RoadInformationSignBlock.FACING)
            && other.getValue(RoadInformationSignBlock.POST_MOUNTED).equals(state.getValue(RoadInformationSignBlock.POST_MOUNTED))
            && other.getValue(RoadInformationSignBlock.WALL_MOUNTED).equals(state.getValue(RoadInformationSignBlock.WALL_MOUNTED))
            && world.getBlockEntity(pos) instanceof RoadInformationSignBlockEntity neighbor
            && !neighbor.isRemoved() && neighbor.theme == theme && neighbor.design.frame() == design.frame();
    }
    private void invalidateConnections() {
        horizontalConnections = -1;
        if (level == null || !level.isClientSide) return;
        // Include both axes, so rotating or removing a sign also refreshes its former neighbors.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = worldPosition.relative(direction, 2);
            var chunk = level.getChunkSource().getChunk(neighborPos.getX() >> 4, neighborPos.getZ() >> 4, false);
            // Read only existing entities: setLevel must never recursively create a neighboring sign.
            if (chunk != null && chunk.getBlockEntities().get(neighborPos) instanceof RoadInformationSignBlockEntity neighbor)
                neighbor.horizontalConnections = -1;
        }
    }
    @Override public void setLevel(Level level) {
        super.setLevel(level);
        invalidateConnections();
    }
    @Override public void setBlockState(BlockState state) {
        super.setBlockState(state);
        invalidateConnections();
    }
    @Override public void setRemoved() {
        super.setRemoved();
        invalidateConnections();
    }
    public static String clean(String value, int limit) {
        if (value == null) return "";
        String text = value.replaceAll("[\\p{Cntrl}§]", "").strip();
        return text.substring(0, Math.min(limit, text.length()));
    }
    public void configure(String[] lines, int[] directions, String[] distances, String route, int theme, int speed, boolean enabled, RoadSignDesign design) {
        for (int i = 0; i < 3; i++) {
            destinations[i] = clean(i < lines.length ? lines[i] : "", 96);
            this.distances[i] = clean(i < distances.length ? distances[i] : "", 12);
            arrows[i] = Math.max(0, Math.min(6, i < directions.length ? directions[i] : 0));
        }
        this.route = clean(route, 16); this.theme = Math.max(0, Math.min(3, theme));
        this.speed = Math.max(5, Math.min(200, speed));
        this.enabled = enabled; this.design = kind() == Kind.DIRECTION ? design.withRows(Math.min(2, design.rows())) : design; invalidateConnections(); notifyUpdate();
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        int oldTheme=theme;
        int[] storedArrows = tag.getIntArray("arrows");
        for (int i = 0; i < 3; i++) {
            destinations[i] = clean(tag.getString("destination" + i), 96);
            distances[i] = clean(tag.getString("distance" + i), 12);
            arrows[i] = Math.max(0, Math.min(6, i < storedArrows.length ? storedArrows[i] : 0));
        }
        route = clean(tag.getString("route"), 16); theme = Math.max(0, Math.min(3, tag.getInt("theme")));
        speed = Math.max(5, Math.min(200, tag.contains("speed") ? tag.getInt("speed") : 70));
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        CompoundTag layout = tag.getCompound("design");
        design = RoadSignDesign.load(layout);
        // Earlier small signs treated rows=3 as a one-row layout. Preserve their appearance.
        if (kind() == Kind.DIRECTION && !layout.contains("layout_version") && design.rows() == 3)
            design = design.withRows(1);
        if (kind() == Kind.DIRECTION) design = design.withRows(Math.min(2, design.rows()));
        invalidateConnections();
        if(theme!=oldTheme && level!=null && level.isClientSide)
            level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),net.minecraft.world.level.block.Block.UPDATE_ALL);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        for (int i = 0; i < 3; i++) {
            tag.putString("destination" + i, destinations[i]);
            tag.putString("distance" + i, distances[i]);
        }
        tag.putIntArray("arrows", arrows); tag.putString("route", route); tag.putInt("theme", theme);
        tag.putInt("speed", speed); tag.putBoolean("enabled", enabled);
        tag.put("design", design.save());
    }
}
