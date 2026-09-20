package com.destan.trafficengine.client;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import de.mrjulsen.mcdragonlib.block.DLWritableSignBlockEntity;
import de.mrjulsen.mcdragonlib.client.gui.builtin.WritableSignScreen;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
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
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.init.ClientInit;
import com.destan.trafficengine.network.packets.stc.TrafficSignWorkbenchUpdateClientPacket;
import com.destan.trafficengine.util.ETimeFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
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

    public static void showPaintBrushScreen(int pattern, int paint, PaintColor color) {
        DLWindow.openWindow(mgr -> new PaintBrushScreen(mgr, pattern, paint, color));
    }

    public static void showSignPatternSelectionScreen(ItemStack stack) {        
        DLWindow.openWindow(mgr -> new TrafficSignPatternSelectionScreen(mgr, stack));
    }

    public static void showStreetLampScheduleScreen(int turnOnTime, int turnOfftime, ETimeFormat format) {        
        DLWindow.openWindow(mgr -> new StreetLampScheduleScreen(mgr, turnOnTime, turnOfftime, format));
    }

    public static void showTrafficLightConfigScreen(Level level, BlockPos pos) {
        DLWindow.openWindow(mgr -> new TrafficLightConfigScreen(mgr, level, pos));
    }

    public static void showTrafficLightControllerScreen(BlockPos pos, Level level) {
        DLWindow.openWindow(mgr -> new TrafficLightControllerScreen(mgr, pos, level));
    }

    public static void showWritableSignScreen(DLWritableSignBlockEntity pSign) {
        Minecraft.getInstance().setScreen(new WritableSignScreen(pSign));
    }

    public static void showTownSignScreen(TownSignBlockEntity pSign, TownSignBlock.ETownSignSide side) {
        Minecraft.getInstance().setScreen(new TownSignScreen(pSign, side));
    }

    
    @SuppressWarnings("resource")
    public static void handleTrafficSignWorkbenchUpdateClientPacket(TrafficSignWorkbenchUpdateClientPacket packet) {
        if (Minecraft.getInstance().screen instanceof TrafficSignWorkbenchGui screen) {
            //screen.updatePreview();
        }
    }

    public static void showRoadConstructionToolScreen(ItemStack itemstack, int blocksCount, int slopesCount) {
        DLWindow.openWindow(mgr -> new RoadConstructionToolScreen(mgr, itemstack, blocksCount, slopesCount));
    }

    
	public static DynamicTexture getShapeTexture(int index) {
		return ClientInit.SHAPE_TEXTURES[index];
	}

    public static int getShapeTextureId(int index) {
        return getShapeTexture(index).getId();
    }
}
