package com.destan.trafficengine.client;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.destan.trafficengine.block.entity.WritableTrafficSignBlockEntity;
import com.destan.trafficengine.client.screen.WritableTrafficSignScreen;
import com.destan.trafficengine.block.TownSignBlock;
import com.destan.trafficengine.block.entity.TownSignBlockEntity;
import com.destan.trafficengine.client.screen.TrafficLightConfigScreen;
import com.destan.trafficengine.client.screen.TrafficSignPatternSelectionScreen;
import com.destan.trafficengine.client.screen.TrafficSignWorkbenchGui;
import com.destan.trafficengine.client.screen.PaintBrushScreen;
import com.destan.trafficengine.client.screen.RoadConstructionToolScreen;
import com.destan.trafficengine.client.screen.StreetLampScheduleScreen;
import com.destan.trafficengine.client.screen.TownSignScreen;
import com.destan.trafficengine.client.screen.TrafficLightControllerScreen;
import com.destan.trafficengine.client.screen.LedDeviceScreen;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.init.ClientInit;
import com.destan.trafficengine.network.packets.stc.TrafficSignWorkbenchUpdateClientPacket;
import com.destan.trafficengine.util.ETimeFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ClientWrapper {


    private static final Queue<Runnable> afterRenderTasks = new ConcurrentLinkedQueue<>();

    public static final void submitTaskAfterRenderFrame(Runnable task) {
        afterRenderTasks.add(task);
    }

    public static void runAllScheduledRenderTasks() {
        while (!afterRenderTasks.isEmpty()) {
            afterRenderTasks.poll().run();
        }
    }

    public static void showPaintBrushScreen(int pattern, PaintColor color) {
        Minecraft.getInstance().setScreen(new PaintBrushScreen(pattern, color));
    }

    public static void showSignPatternSelectionScreen(ItemStack stack) {        
        Minecraft.getInstance().setScreen(new TrafficSignPatternSelectionScreen(stack));
    }

    public static void showStreetLampScheduleScreen(int turnOnTime, int turnOfftime, ETimeFormat format) {        
        Minecraft.getInstance().setScreen(new StreetLampScheduleScreen(turnOnTime, turnOfftime, format));
    }

    public static void showTrafficLightConfigScreen(Level level, BlockPos pos) {
        Minecraft.getInstance().setScreen(new TrafficLightConfigScreen(level, pos));
    }

    public static void showTrafficLightControllerScreen(BlockPos pos, Level level) {
        Minecraft.getInstance().setScreen(new TrafficLightControllerScreen(pos, level));
    }

    public static void showLedDeviceScreen(Level level, BlockPos pos) {
        Minecraft.getInstance().setScreen(new LedDeviceScreen(level, pos));
    }

    public static void showWritableSignScreen(WritableTrafficSignBlockEntity pSign) {
        Minecraft.getInstance().setScreen(new WritableTrafficSignScreen(pSign) {
            @Override public boolean isPauseScreen() { return false; }
        });
    }

    public static void showTownSignScreen(TownSignBlockEntity pSign, TownSignBlock.ETownSignSide side) {
        Minecraft.getInstance().setScreen(new TownSignScreen(pSign, side) {
            @Override public boolean isPauseScreen() { return false; }
        });
    }

    
    @SuppressWarnings("resource")
    public static void handleTrafficSignWorkbenchUpdateClientPacket(TrafficSignWorkbenchUpdateClientPacket packet) {
        if (Minecraft.getInstance().screen instanceof TrafficSignWorkbenchGui screen) {
            screen.updatePreview();
        }
    }

    public static void showRoadConstructionToolScreen(ItemStack itemstack, int blocksCount, int slopesCount) {
        Minecraft.getInstance().setScreen(new RoadConstructionToolScreen(itemstack, blocksCount, slopesCount));
    }
}
