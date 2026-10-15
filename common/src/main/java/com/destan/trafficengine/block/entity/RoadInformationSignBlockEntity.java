package com.destan.trafficengine.block.entity;

import com.destan.trafficengine.block.RoadInformationSignBlock;
import com.destan.trafficengine.block.RoadInformationSignBlock.Kind;
import com.destan.trafficengine.registry.ModBlockEntities;
import com.destan.trafficengine.data.RoadSignDesign;
import net.minecraft.core.BlockPos;
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
        this.enabled = enabled; this.design = design; notifyUpdate();
    }
    @Override public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
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
        if(theme!=oldTheme && level!=null && level.isClientSide)
            level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),net.minecraft.world.level.block.Block.UPDATE_ALL);
    }
    @Override protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        for (int i = 0; i < 3; i++) {
            tag.putString("destination" + i, destinations[i]);
            tag.putString("distance" + i, distances[i]);
        }
        tag.putIntArray("arrows", arrows); tag.putString("route", route); tag.putInt("theme", theme);
        tag.putInt("speed", speed); tag.putBoolean("enabled", enabled);
        tag.put("design", design.save());
    }
}
