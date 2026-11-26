package com.destan.trafficengine.item;
import com.destan.trafficengine.util.ItemData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.google.common.collect.Multimap;
import com.google.common.collect.ImmutableMultimap;

import com.destan.trafficengine.network.PacketStatus;
import com.destan.trafficengine.data.WorldLocation;
import com.destan.trafficengine.util.ModUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.block.AsphaltSlope;
import com.destan.trafficengine.block.data.RoadType;
import com.destan.trafficengine.client.ClientWrapper;
import com.destan.trafficengine.config.ModCommonConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class RoadConstructionTool extends Item {

    public static final String NBT_LOCATION1 = "Location1";
    public static final String NBT_LOCATION2 = "Location2";
    private static final String NBT_NEXT_ENDPOINT = "NextEndpoint";
    public static final String NBT_ROAD_WIDTH = "RoadWidth";
    public static final String NBT_REPLACE_BLOCKS = "ReplaceBlocks";
    public static final String NBT_ROAD_TYPE = "RoadType";

    public static final boolean DEFAULT_REPLACE_BLOCKS = true;
    public static final byte DEFAULT_ROAD_WIDTH = 7;
    public static final RoadType DEFAULT_ROAD_TYPE = RoadType.ASPHALT;

    private static final int ERROR_TOO_FAR = 1;
    private static final int ERROR_SLOPE_TOO_STEEP = 2;

    public static final int BUILD_DELAY_TICKS = 4;

    private static byte clientTicks;
    private static final byte FAST_GRAPHICS_CLIENT_TICK_DELAY = 8;
    private static final byte FANCY_GRAPHICS_CLIENT_TICK_DELAY = 4;


    private final float attackDamage;

    public RoadConstructionTool(Tiers tier, Properties properties) {
        super(properties.stacksTo(1).durability(tier.getUses()));

        // DÜZELTME: Aletin saldırı gücünü sıfırladık ve hasar/hız özelliklerini tamamen sildik.
        // Artık altında "+8 Saldırı Hasarı" gibi yeşil silah yazıları çıkmayacak!
        this.attackDamage = 0f;
    }

    // Use the vanilla stone pickaxe's harvesting rules without changing road-building speed.
    private final ItemStack miningTool = new ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE);

    @Override
    public float getDestroySpeed(ItemStack stack, net.minecraft.world.level.block.state.BlockState state) {
        return miningTool.getDestroySpeed(state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, net.minecraft.world.level.block.state.BlockState state) {
        return miningTool.isCorrectToolForDrops(state);
    }

    public boolean canReplaceBlock(net.minecraft.world.level.block.state.BlockState state) {
        return state.getBlock().defaultDestroyTime() != net.minecraft.world.level.block.Block.INDESTRUCTIBLE
            && (!state.requiresCorrectToolForDrops() || miningTool.isCorrectToolForDrops(state));
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack ingredient) {
        return Tiers.STONE.getRepairIngredient().test(ingredient);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, net.minecraft.world.level.block.state.BlockState state,
                             BlockPos pos, net.minecraft.world.entity.LivingEntity miner) {
        if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0) {
            stack.hurtAndBreak(1, miner, EquipmentSlot.MAINHAND);
        }
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        Level level = pContext.getLevel();
        BlockPos clickedPos = pContext.getClickedPos();
        Vec3 clickedVec = pContext.getClickLocation();
        Player player = pContext.getPlayer();

        if (player == null) return InteractionResult.PASS;
        if (!player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                CompoundTag points = ItemData.getOrCreate(pContext.getItemInHand());

                WorldLocation location = new WorldLocation(clickedPos.getX(), clickedVec.y, clickedPos.getZ(),
                    level.dimension().location());
                String selected = points.contains(NBT_LOCATION1) ? NBT_LOCATION2 : NBT_LOCATION1;
                String requested = points.getString(NBT_NEXT_ENDPOINT);
                if (requested.equals(NBT_LOCATION1) || requested.equals(NBT_LOCATION2)) selected = requested;
                String anchor = selected.equals(NBT_LOCATION1) ? NBT_LOCATION2 : NBT_LOCATION1;
                if (points.contains(anchor)) {
                    WorldLocation previous = WorldLocation.loadFromNbt(points.getCompound(anchor));
                    if (!previous.dimension.equals(location.dimension)) {
                        points.remove(NBT_LOCATION1);
                        points.remove(NBT_LOCATION2);
                        if (requested.isEmpty()) selected = NBT_LOCATION1;
                    } else {
                        PacketStatus validity = isLineValid(previous.getLocationVec3(), location.getLocationVec3());
                        if (validity.flag() != PacketStatus.FLAG_OK) {
                            player.displayClientMessage(selectionError(validity).withStyle(ChatFormatting.RED), true);
                            return InteractionResult.SUCCESS;
                        }
                    }
                }
                points.put(selected, location.toNbt());
                points.remove(NBT_NEXT_ENDPOINT);
                ItemData.set(pContext.getItemInHand(), points);
            }
            return InteractionResult.SUCCESS;
        }

        return super.useOn(pContext);
    }

    @Override
    public void appendHoverText(ItemStack pStack, TooltipContext context, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {

    }

    public boolean canBeDepleted() {
        return true;
    }

    public float getAttackDamage() {
        return attackDamage;
    }

    @Override
    public boolean isFoil(ItemStack pStack) {
        CompoundTag tag = ItemData.get(pStack);
        return (tag != null && (tag.contains(NBT_LOCATION1) || tag.contains(NBT_LOCATION2))) || super.isFoil(pStack);
    }

    public static void reset(ItemStack stack) {
        CompoundTag nbt = ItemData.getOrCreate(stack);
        nbt.remove(RoadConstructionTool.NBT_LOCATION1);
        nbt.remove(RoadConstructionTool.NBT_LOCATION2);
        nbt.remove(NBT_NEXT_ENDPOINT);
        nbt.putBoolean(RoadConstructionTool.NBT_REPLACE_BLOCKS, DEFAULT_REPLACE_BLOCKS);
        nbt.putByte(RoadConstructionTool.NBT_ROAD_WIDTH, DEFAULT_ROAD_WIDTH);
        nbt.putInt(RoadConstructionTool.NBT_ROAD_TYPE, DEFAULT_ROAD_TYPE.getIndex());
        ItemData.set(stack, nbt);
    }

    public static void clearSelection(ItemStack stack, boolean start, boolean end) {
        CompoundTag tag = ItemData.getOrCreate(stack);
        if (start) tag.remove(NBT_LOCATION1);
        if (end) tag.remove(NBT_LOCATION2);
        if (start && end) tag.remove(NBT_NEXT_ENDPOINT);
        else tag.putString(NBT_NEXT_ENDPOINT, start ? NBT_LOCATION1 : NBT_LOCATION2);
        ItemData.set(stack, tag);
    }

    public static void initStackTag(ItemStack stack) {
        CompoundTag tag = ItemData.getOrCreate(stack);
        if (!tag.contains(NBT_ROAD_WIDTH)) tag.putByte(NBT_ROAD_WIDTH, DEFAULT_ROAD_WIDTH);
        if (!tag.contains(NBT_ROAD_TYPE)) tag.putInt(NBT_ROAD_TYPE, DEFAULT_ROAD_TYPE.getIndex());
        if (!tag.contains(NBT_REPLACE_BLOCKS)) tag.putBoolean(NBT_REPLACE_BLOCKS, DEFAULT_REPLACE_BLOCKS);
        ItemData.set(stack,tag);
    }

    public static PacketStatus isLineValid(Vec3 a, Vec3 b) {
        double dx = b.x - a.x, dz = b.z - a.z, rise = Math.abs(b.y - a.y);
        double horizontalSquared = dx * dx + dz * dz;
        double distanceSquared = horizontalSquared + rise * rise;
        int maxDistance = ModCommonConfig.ROAD_BUILDER_MAX_DISTANCE.get();
        boolean flag1 = Double.isFinite(distanceSquared) && distanceSquared > 1.0E-14
            && distanceSquared <= maxDistance * maxDistance;
        boolean flag2 = horizontalSquared > 1.0E-14
            && Math.sqrt(horizontalSquared) / rise >= ModCommonConfig.ROAD_BUILDER_MAX_SLOPE.get();
        int status = 0;

        if (!flag1) {
            status = ERROR_TOO_FAR;
        } else if (!flag2) {
            status = ERROR_SLOPE_TOO_STEEP;
        }

        return new PacketStatus(flag1 && flag2 ? PacketStatus.FLAG_OK : PacketStatus.FLAG_ERROR, status, "");
    }

    public static MutableComponent selectionError(PacketStatus validity) {
        return Component.translatable(validity.code() == ERROR_TOO_FAR
            ? "item.trafficengine.road_construction_tool.status_too_far"
            : "item.trafficengine.road_construction_tool.status_slope_too_steep");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        initStackTag(stack);
        if (level.isClientSide) ClientWrapper.showRoadConstructionToolScreen(stack);
        return InteractionResultHolder.success(stack);
    }

    public RoadBuilderCountResult countBlocksNeeded(Level level, Vec3 start, Vec3 end, byte width,
            boolean replaceBlocks, RoadType type, boolean creative) {
        int blocks = 0, slopes = 0;
        for (Map<BlockPos, Integer> slice : calculateRoad(level, start, end, width, replaceBlocks)) {
            for (var entry : slice.entrySet()) {
                BlockState existing = level.getBlockState(entry.getKey());
                int layers = entry.getValue();
                if (existing.equals(replacementState(type, layers))) continue;
                if (creative ? existing.getBlock().defaultDestroyTime() == Block.INDESTRUCTIBLE
                        : !canReplaceBlock(existing)) continue;
                if (layers < 8) slopes += layers;
                else blocks++;
            }
        }
        return new RoadBuilderCountResult(blocks, slopes);
    }

    public static BlockState replacementState(RoadType type, int layers) {
        return layers < 8 ? type.getSlope().defaultBlockState().setValue(AsphaltSlope.LAYERS, layers)
            : type.getBlock().defaultBlockState();
    }

    public static Collection<Map<BlockPos, Integer>> calculateRoad(Level level, Vec3 start, Vec3 end, byte roadWidth, boolean replaceBlocks) {
        List<Map<BlockPos, Integer>> result = new ArrayList<>();
        for (Map<RoadConstructionPlan.Cell, Integer> slice : RoadConstructionPlan.calculate(
                start.x, start.y, start.z, end.x, end.y, end.z, roadWidth,
                cell -> {
                    BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
                    return !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos)
                        && (replaceBlocks || level.isEmptyBlock(pos));
                })) {
            Map<BlockPos, Integer> blocks = new LinkedHashMap<>();
            slice.forEach((cell, height) -> blocks.put(new BlockPos(cell.x(), cell.y(), cell.z()), height));
            result.add(blocks);
        }
        return result;
    }

    public static RoadBuildingData prepareRoadBuilding(Level pLevel, Player pPlayer, InteractionHand pHand, ItemStack pStack, Vec3 start, Vec3 end, byte roadWidth, boolean replaceBlocks, RoadType roadType) {

        Collection<Map<BlockPos, Integer>> blockList = calculateRoad(pLevel, start, end, roadWidth, replaceBlocks);
        pPlayer.getCooldowns().addCooldown(pStack.getItem(), blockList.size() * BUILD_DELAY_TICKS);

        if (!pLevel.isClientSide) {
            ModUtils.giveAdvancement((ServerPlayer)pPlayer, TrafficEngine.MOD_ID, "road_construction_tool", "req");
        }

        return new RoadBuildingData(blockList, pPlayer, pHand, pStack, start, end, roadWidth, replaceBlocks, roadType);
    }

    private static final DustParticleOptions VALID_CENTER = new DustParticleOptions(new Vector3f(0.2f, 0.9f, 0.2f), 1);
    private static final DustParticleOptions INVALID_CENTER = new DustParticleOptions(new Vector3f(0.9f, 0.2f, 0.2f), 1);
    private static final DustParticleOptions ROAD_EDGE = new DustParticleOptions(new Vector3f(1, 1, 0.6f), 0.5f);

    @Environment(EnvType.CLIENT)
    public static void clientTick() {
        if (++clientTicks <= (Minecraft.useFancyGraphics() ? FANCY_GRAPHICS_CLIENT_TICK_DELAY : FAST_GRAPHICS_CLIENT_TICK_DELAY)) return;
        clientTicks = 0;
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        Level level = client.level;
        if (player == null || level == null || client.screen != null) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof RoadConstructionTool)) stack = player.getOffhandItem();
        if (!(stack.getItem() instanceof RoadConstructionTool)) return;
        CompoundTag tag = ItemData.get(stack);
        if (tag == null) return;
        WorldLocation a = endpoint(tag, NBT_LOCATION1, level);
        WorldLocation b = endpoint(tag, NBT_LOCATION2, level);
        if (a == null && b == null) return;

        Vec3 start = a == null ? null : a.getLocationVec3().add(0.5, 0, 0.5);
        Vec3 end = b == null ? null : b.getLocationVec3().add(0.5, 0, 0.5);
        if (start == null || end == null) {
            WorldLocation anchor = a != null ? a : b;
            player.displayClientMessage(Component.translatable(a == null
                ? "item.trafficengine.road_construction_tool.status_end_only"
                : "item.trafficengine.road_construction_tool.status_pos1", anchor.getLocationBlockPos().toShortString()), true);
            if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
            Vec3 candidate = new Vec3(hit.getBlockPos().getX() + 0.5, hit.getLocation().y, hit.getBlockPos().getZ() + 0.5);
            if (start == null) start = candidate;
            else end = candidate;
        } else {
            player.displayClientMessage(Component.translatable("item.trafficengine.road_construction_tool.status_pos2",
                a.getLocationBlockPos().toShortString(), b.getLocationBlockPos().toShortString()).withStyle(ChatFormatting.GREEN), true);
        }

        Vec3 line = end.subtract(start);
        double horizontal = line.horizontalDistance();
        double distance = line.length();
        if (distance < 1.0E-7 || distance >= 256 || horizontal < 1.0E-7) return;
        PacketStatus validity = isLineValid(start, end);
        boolean valid = validity.flag() == PacketStatus.FLAG_OK;
        if (!valid) player.displayClientMessage(selectionError(validity).withStyle(ChatFormatting.RED), true);
        var center = valid ? VALID_CENTER : INVALID_CENTER;
        double halfWidth = Math.max(1, tag.getByte(NBT_ROAD_WIDTH)) / 2.0;
        double rightX = line.z / horizontal * halfWidth, rightZ = -line.x / horizontal * halfWidth;
        int samples = Math.max(1, (int)Math.ceil(distance * 4));
        for (int sample = 0; sample <= samples; sample++) {
            double progress = sample / (double)samples;
            double x = start.x + line.x * progress, y = start.y + line.y * progress, z = start.z + line.z * progress;
            level.addParticle(center, x, y, z, 0, 0, 0);
            level.addParticle(ROAD_EDGE, x + rightX, y, z + rightZ, 0, 0, 0);
            level.addParticle(ROAD_EDGE, x - rightX, y, z - rightZ, 0, 0, 0);
        }
    }

    private static WorldLocation endpoint(CompoundTag tag, String key, Level level) {
        if (!tag.contains(key)) return null;
        WorldLocation location = WorldLocation.loadFromNbt(tag.getCompound(key));
        return location.dimension.equals(level.dimension().location()) ? location : null;
    }

    public static class RoadBuilderCountResult {
        public final int blocksCount;
        public final int slopesCount;

        protected RoadBuilderCountResult(int blocksCount, int slopesCount) {
            this.blocksCount = blocksCount;
            this.slopesCount = slopesCount;
        }
    }

    public static class RoadBuildingData {
        public final List<Map<BlockPos, Integer>> blocks;
        public final Player player;
        public final InteractionHand hand;
        public final ItemStack item;
        public final Item toolItem;
        public final Vec3 start;
        public final Vec3 end;
        public final byte roadWidth;
        public final boolean replaceBlocks;
        public final RoadType roadType;

        public RoadBuildingData(Collection<Map<BlockPos, Integer>> blocks, Player player, InteractionHand hand, ItemStack item, Vec3 start, Vec3 end, byte roadWidth, boolean replaceBlocks, RoadType roadType) {
            this.blocks = new ArrayList<>(blocks);
            this.player = player;
            this.hand = hand;
            this.item = item;
            this.toolItem = item.getItem();
            this.start = start;
            this.end = end;
            this.roadWidth = roadWidth;
            this.replaceBlocks = replaceBlocks;
            this.roadType = roadType;
        }
    }
}
