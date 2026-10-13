package com.destan.trafficengine.item;

import java.util.ArrayList;
import java.util.List;
import com.destan.trafficengine.block.AsphaltBlock;
import com.destan.trafficengine.block.AsphaltSlope;
import com.destan.trafficengine.block.ConnectedRoadBlock;
import com.destan.trafficengine.block.ConnectedRoadSlopeBlock;
import com.destan.trafficengine.block.PaintedAsphaltSlope;
import com.destan.trafficengine.block.data.ConnectedLineStyle;
import com.destan.trafficengine.block.data.RoadBlock;
import com.destan.trafficengine.block.data.RoadType;
import com.destan.trafficengine.block.entity.ColoredBlockEntity;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class RoadPainting {
    public static final String BATCH = "road_batch", WIDTH = "road_width", STYLE = "road_line_style";
    public static final String DASH_GAP = "road_dash_gap", POSITION = "road_line_position";
    public static final String AREA_STYLE = "road_area_style", AREA_SHAPE = "road_area_shape";
    private static final String START = "road_paint_start", DIMENSION = "road_paint_dimension";
    private static final String CORNERS = "road_area_corners";
    private RoadPainting() {}
    public static boolean isRoad(BlockState state) {
        return state.getBlock() instanceof AsphaltBlock || state.getBlock() instanceof RoadBlock;
    }
    public static int style(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(STYLE) ? Math.max(-1, Math.min(3, tag.getInt(STYLE))) : -1;
    }
    public static void clearSelection(CompoundTag tag) { tag.remove(START); tag.remove(DIMENSION); tag.remove(CORNERS); }
    public static int areaStyle(ItemStack brush) {
        return brush.getTag()==null?0:Math.max(0,Math.min(3,brush.getTag().getInt(AREA_STYLE)));
    }
    public static int areaShape(ItemStack brush) {
        return brush.getTag()==null?0:Math.max(0,Math.min(2,brush.getTag().getInt(AREA_SHAPE)));
    }
    public static int dashGap(ItemStack brush) {
        return brush.getTag()!=null && brush.getTag().getInt(DASH_GAP)==4?4:2;
    }
    public static int position(ItemStack brush) {
        return brush.getTag()==null?0:Math.max(0,Math.min(2,brush.getTag().getInt(POSITION)));
    }
    private record Change(BlockPos pos, BlockState state, PaintColor base, PaintColor marking,
            float[] stroke, int dashGap, int automaticPosition) {}

    public static InteractionResult use(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack brush = context.getItemInHand();
        CompoundTag tag = brush.getOrCreateTag();
        if (player == null) return InteractionResult.FAIL;
        if (level.isClientSide) {
            BlockPos pos = context.getClickedPos();
            if (areaStyle(brush) == 0 && !tag.getBoolean(BATCH) && style(brush) >= 0
                    && (player.isCreative() || BrushItem.getPaint(brush) > 0)
                    && level.isLoaded(pos) && level.mayInteract(player, pos)
                    && player.mayUseItemAt(pos, Direction.UP, brush)) {
                Change change = prepare(level, pos, brush, player.getDirection(), null);
                if (change != null) apply(level, change);
            }
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        boolean area = areaStyle(brush)>0;
        List<RoadPaintPlan.Cell> corners = new ArrayList<>();
        if (area) {
            if(!isRoad(level.getBlockState(clicked)))return fail(player,"needs_road");
            String dimension=level.dimension().location().toString();
            long[] selected=dimension.equals(tag.getString(DIMENSION))?tag.getLongArray(CORNERS):new long[0];
            int needed=RoadAreaGeometry.cornerCount(areaShape(brush));
            if(selected.length>=needed)selected=new long[0];
            for(long packed:selected) {
                BlockPos pos=BlockPos.of(packed);
                if(pos.equals(clicked) || pos.getY()!=clicked.getY())return fail(player,"area_invalid");
                if(Math.max(Math.abs((long)pos.getX()-clicked.getX()),Math.abs((long)pos.getZ()-clicked.getZ()))>RoadPaintPlan.MAX_DISTANCE)
                    return fail(player,"too_long");
                corners.add(new RoadPaintPlan.Cell(pos.getX(),pos.getY(),pos.getZ()));
            }
            if(!level.mayInteract(player,clicked) || !player.mayUseItemAt(clicked,Direction.UP,brush))return fail(player,"blocked");
            if(level.getBlockState(clicked).getBlock() instanceof AsphaltSlope
                    || level.getBlockState(clicked).getBlock() instanceof PaintedAsphaltSlope)return fail(player,"needs_road");
            corners.add(new RoadPaintPlan.Cell(clicked.getX(),clicked.getY(),clicked.getZ()));
            if(corners.size()<needed) {
                long[] packed=java.util.Arrays.copyOf(selected,corners.size());packed[selected.length]=clicked.asLong();
                tag.putLongArray(CORNERS,packed);tag.putString(DIMENSION,dimension);
                player.getInventory().setChanged();
                player.displayClientMessage(Component.translatable("item.trafficengine.paint_brush.corner_selected",corners.size(),needed),true);
                return InteractionResult.SUCCESS;
            }
        }
        boolean batch = !area && tag.getBoolean(BATCH);
        BlockPos start = clicked;
        if (batch) {
            String dimension = level.dimension().location().toString();
            if (!tag.contains(START) || !dimension.equals(tag.getString(DIMENSION))) {
                tag.putLong(START, clicked.asLong()); tag.putString(DIMENSION, dimension);
                player.getInventory().setChanged();
                player.displayClientMessage(Component.translatable("item.trafficengine.paint_brush.start_selected"), true);
                return InteractionResult.SUCCESS;
            }
            start = BlockPos.of(tag.getLong(START));
        }
        int width = Math.max(1, Math.min(9, tag.getInt(WIDTH))) | 1;
        var first=new RoadPaintPlan.Cell(start.getX(),start.getY(),start.getZ());
        var last=new RoadPaintPlan.Cell(clicked.getX(),clicked.getY(),clicked.getZ());
        List<RoadPaintPlan.Cell> route = batch ? RoadPaintPlan.between(first,last,width) : List.of(last);
        if (!area && route.isEmpty()) return fail(player,"too_long");
        // A multi-block stroke follows the selected route. Single-block automatic
        // markings, handled by prepare(), adapt to neighbours instead.
        boolean stroke=area ? areaStyle(brush)!=RoadAreaGeometry.FILL
            : batch && style(brush)>=0 && !start.equals(clicked);
        var flatEnd=new RoadPaintPlan.Cell(clicked.getX(),start.getY(),clicked.getZ());
        var areaPlan=area?RoadAreaGeometry.between(corners,areaShape(brush),areaStyle(brush)):null;
        if(area && areaPlan.isEmpty())return fail(player,"area_invalid");
        var geometry=area?areaPlan.geometry():stroke?RoadStrokeGeometry.between(first,flatEnd,width,style(brush),dashGap(brush),position(brush))
                :java.util.Map.<RoadPaintPlan.Cell,float[]>of();
        // Include dash gaps so repainting a solid line clears the spaces too.
        var coverage=!area && stroke && style(brush)==1?RoadStrokeGeometry.between(first,flatEnd,width,3,dashGap(brush),position(brush)):geometry;
        List<RoadPaintPlan.Cell> cells = area?areaPlan.coverage():stroke?List.copyOf(coverage.keySet()):route;
        if (cells.isEmpty()) return fail(player, "too_long");
        Direction orientation = area ? Direction.NORTH : start.equals(clicked) ? player.getDirection()
            : Math.abs(clicked.getX() - start.getX()) > Math.abs(clicked.getZ() - start.getZ())
                ? clicked.getX() > start.getX() ? Direction.EAST : Direction.WEST
                : clicked.getZ() > start.getZ() ? Direction.SOUTH : Direction.NORTH;
        List<Change> changes = new ArrayList<>();
        for (var cell : cells) {
            BlockPos pos = batch ? surface(level,cell,first,last) : new BlockPos(cell.x(), cell.y(), cell.z());
            if (pos == null) return fail(player,"needs_road");
            if (!level.isLoaded(pos) || !level.mayInteract(player, pos)
                    || !player.mayUseItemAt(pos, Direction.UP, brush)) return fail(player, "blocked");
            Change change = prepare(level, pos, brush, orientation,stroke?geometry.getOrDefault(cell,new float[0]):null);
            if (change == null) return fail(player, "needs_road");
            if (!same(level, change)) changes.add(change);
        }
        int paintNeeded=stroke?(int)changes.stream().filter(change->change.stroke.length>0).count():changes.size();
        if (!player.isCreative() && BrushItem.getPaint(brush) < paintNeeded)
            return fail(player, "needs_paint");
        var undo = RoadPaintUndo.begin(level, player, brush, changes.stream().map(Change::pos).toList());
        for (Change change : changes) {
            apply(level, change);
        }
        for (Change change : changes) {
            BlockState state = level.getBlockState(change.pos);
            if (state.getBlock() instanceof ConnectedRoadBlock)
                level.setBlockAndUpdate(change.pos, ConnectedRoadBlock.reconnect(state, level, change.pos));
        }
        if (!player.isCreative()) tag.putInt(BrushItem.NBT_PAINT, BrushItem.getPaint(brush) - paintNeeded);
        if (batch || area) clearSelection(tag);
        RoadPaintUndo.finish(undo);
        player.getInventory().setChanged();
        if (!changes.isEmpty()) level.playSound(null, clicked, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 2);
        if (batch || area)
            player.displayClientMessage(Component.translatable("item.trafficengine.paint_brush.painted", changes.size()), true);
        return InteractionResult.SUCCESS;
    }
    private static void apply(Level level, Change change) {
        // Do not queue a client mesh with the new entity's default white tint.
        // The colour/geometry setters rebuild it after both colours are assigned.
        level.setBlock(change.pos, change.state, level.isClientSide ? Block.UPDATE_INVISIBLE : Block.UPDATE_ALL);
        if (level.getBlockEntity(change.pos) instanceof ColoredBlockEntity colored) {
            colored.setRoadColors(change.base, change.marking);
            colored.setStrokeVertices(change.stroke, change.dashGap, change.automaticPosition);
        }
    }
    private static Change prepare(Level level, BlockPos pos, ItemStack brush, Direction orientation,float[] stroke) {
        BlockState old = level.getBlockState(pos);
        if (!isRoad(old)) return null;
        RoadType type = old.getBlock() instanceof RoadBlock road ? road.getDefaultRoadType()
            : ((AsphaltBlock)old.getBlock()).getDefaultRoadType();
        boolean fullAreaPaint = areaStyle(brush)==RoadAreaGeometry.FILL;
        int pattern = fullAreaPaint ? 0 : BrushItem.getPatternId(brush);
        int autoStyle = areaStyle(brush)>0 ? fullAreaPaint ? -1 : 3 : style(brush);
        boolean slope = isSlope(old);
        int automaticPosition = autoStyle >= 0 && stroke == null ? position(brush) : -1;
        if (slope && stroke != null) {
            stroke=stroke.clone();
            for(int i=1;i<stroke.length;i+=3) stroke[i]=old.getValue(PaintedAsphaltSlope.LAYERS)/8F+0.003125F;
        }
        Block block;
        if (autoStyle >= 0) {
            var supplier = (slope ? ModBlocks.CONNECTED_SLOPES : ModBlocks.CONNECTED_ROADS).get(type);
            if (supplier == null) return null;
            block = supplier.get();
        } else {
            var supplier = ModBlocks.ROAD_BLOCKS.get(type.getRoadType() + (slope ? "_slope_pattern_" : "_pattern_") + pattern);
            if (supplier == null) return null;
            block = supplier.get();
        }
        PaintColor base = PaintColor.NONE;
        boolean basePainted = old.hasProperty(RoadBlock.BASE_PAINTED) && old.getValue(RoadBlock.BASE_PAINTED);
        if (level.getBlockEntity(pos) instanceof ColoredBlockEntity colored) {
            base = colored.getColor();
            if (!basePainted && BuiltInRegistries.BLOCK.getKey(old.getBlock()).getPath().endsWith("_pattern_0")) {
                base = colored.getMarkingColor() != PaintColor.NONE ? colored.getMarkingColor() : base;
                basePainted = base != PaintColor.NONE;
            }
        }
        PaintColor paint = BrushItem.getColor(brush), marking = paint;
        if (autoStyle < 0 && pattern == 0) { base = paint; basePainted = true; marking = PaintColor.NONE; }
        BlockState state = block.defaultBlockState().setValue(RoadBlock.FACING, orientation)
            .setValue(RoadBlock.BASE_PAINTED, basePainted);
        if (autoStyle >= 0) {
            state=state.setValue(ConnectedRoadBlock.STYLE,ConnectedLineStyle.byIndex(autoStyle));
            if (!slope) state=ConnectedRoadBlock.reconnect(state.setValue(ConnectedRoadBlock.STROKE,stroke!=null || automaticPosition>=0),level,pos);
        }
        if (slope) state = state.setValue(PaintedAsphaltSlope.LAYERS, old.getValue(PaintedAsphaltSlope.LAYERS))
            .setValue(PaintedAsphaltSlope.WATERLOGGED,old.getValue(PaintedAsphaltSlope.WATERLOGGED));
        return new Change(pos, state, base, marking,stroke==null?new float[0]:stroke,dashGap(brush),automaticPosition);
    }
    private static boolean isSlope(BlockState state) {
        return state.getBlock() instanceof AsphaltSlope || state.getBlock() instanceof PaintedAsphaltSlope;
    }
    private static BlockPos surface(Level level, RoadPaintPlan.Cell cell, RoadPaintPlan.Cell first, RoadPaintPlan.Cell last) {
        Integer height=RoadPaintSurface.height(cell,first,last,y -> {
            BlockPos pos=new BlockPos(cell.x(),y,cell.z());
            if (y<level.getMinBuildHeight() || y>=level.getMaxBuildHeight() || !level.isLoaded(pos)) return false;
            if (!isRoad(level.getBlockState(pos))) return false;
            return cell.x()==first.x() && cell.z()==first.z() && y==first.y()
                || cell.x()==last.x() && cell.z()==last.z() && y==last.y()
                || !isRoad(level.getBlockState(pos.above()));
        });
        return height==null?null:new BlockPos(cell.x(),height,cell.z());
    }
    private static boolean same(Level level, Change change) {
        return level.getBlockState(change.pos).equals(change.state)
            && level.getBlockEntity(change.pos) instanceof ColoredBlockEntity colored
            && colored.getColor() == change.base && colored.getMarkingColor() == change.marking
            && java.util.Arrays.equals(colored.getStrokeVertices(),change.stroke)
            && colored.getRoadDashGap() == change.dashGap
            && colored.getAutomaticLinePosition() == change.automaticPosition;
    }
    private static InteractionResult fail(Player player, String reason) {
        player.displayClientMessage(Component.translatable("item.trafficengine.paint_brush." + reason), true);
        return InteractionResult.FAIL;
    }
}
