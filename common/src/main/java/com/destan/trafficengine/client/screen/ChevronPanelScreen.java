package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.block.ChevronPanelBlock;
import com.destan.trafficengine.client.gui.ScaledTrafficEngineScreen;
import com.destan.trafficengine.client.gui.TEText;
import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.components.TEPanel;
import com.destan.trafficengine.client.gui.theme.TEColors;
import com.destan.trafficengine.network.NetworkDirection;
import com.destan.trafficengine.network.packets.cts.ChevronPanelPacket;
import com.destan.trafficengine.registry.ModNetworkManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Explicit model, direction and colour choices, with an undistorted front preview. */
public final class ChevronPanelScreen extends ScaledTrafficEngineScreen {
    // Model-unit polygons also used by the baked sign assets; allocated once, not every frame.
    private static final float[] WIDE_QUADS = {0.6F, 0.6F, 2.25F, 0.6F, 5.1F, 4F, 3.45F, 4F, 3.45F, 4F, 5.1F, 4F, 2.25F, 7.4F, 0.6F, 7.4F, 5.6F, 0.6F, 7.25F, 0.6F, 10.1F, 4F, 8.45F, 4F, 8.45F, 4F, 10.1F, 4F, 7.25F, 7.4F, 5.6F, 7.4F, 10.6F, 0.6F, 12.25F, 0.6F, 15.1F, 4F, 13.45F, 4F, 13.45F, 4F, 15.1F, 4F, 12.25F, 7.4F, 10.6F, 7.4F};
    private static final float[] SINGLE_QUADS = {0.6F, 0.6F, 3.3F, 0.6F, 7.4F, 4F, 4.7F, 4F, 4.7F, 4F, 7.4F, 4F, 3.3F, 7.4F, 0.6F, 7.4F};
    private final BlockPos pos;
    private final TEButton[] arrows = new TEButton[2], themes = new TEButton[2], models = new TEButton[2];
    private boolean right, redWhite, single;
    private final Component directionLabel = text("direction"), colorsLabel = text("colors"), modelLabel = text("model");
    public ChevronPanelScreen(Level level, BlockPos pos) {
        super(text("title")); this.pos = pos;
        var state = level.getBlockState(pos);
        right = state.getValue(ChevronPanelBlock.ARROW_RIGHT);
        redWhite = state.getValue(ChevronPanelBlock.RED_WHITE);
        single = state.getValue(ChevronPanelBlock.SINGLE);
    }
    private static Component text(String key) { return Component.translatable("gui.trafficengine.chevron." + key); }
    @Override protected void initEditor() {
        layoutWindow(350, 282); addCloseButton();
        for (int i = 0; i < 2; i++) {
            final boolean choice = i == 1;
            models[i] = addRenderableWidget(new TEButton(left + 16, top + 65 + i * 28, 104, 22, text(choice ? "multiple" : "single"), b -> {
                single = !choice; updateChoices();
            }));
            arrows[i] = addRenderableWidget(new TEButton(left + 16 + i * 163, top + 160, 155, 22, text(choice ? "right" : "left"), b -> {
                right = choice; updateChoices();
            }));
            themes[i] = addRenderableWidget(new TEButton(left + 16 + i * 163, top + 205, 155, 22, text(choice ? "red_white" : "black_yellow"), b -> {
                redWhite = choice; updateChoices();
            }));
        }
        addRenderableWidget(new TEButton(left + 16, top + 248, 90, 22, Component.translatable("gui.cancel"), b -> onClose()));
        addRenderableWidget(new TEButton(left + windowWidth - 106, top + 248, 90, 22, Component.translatable("gui.done"), b -> {
            ModNetworkManager.UPDATE_CHEVRON_PANEL.send(NetworkDirection.C2S, new ChevronPanelPacket(pos, right, redWhite, single)); onClose();
        }).primary());
        updateChoices();
    }
    private void updateChoices() {
        models[0].setSelected(single); models[1].setSelected(!single);
        arrows[0].setSelected(!right); arrows[1].setSelected(right);
        themes[0].setSelected(!redWhite); themes[1].setSelected(redWhite);
    }
    @Override public void render(GuiGraphics g, int x, int y, float tick) {
        beginWindow(g);
        int w = single ? 88 : 176, h = 88, px = left + 238 - w / 2, py = top + 44;
        TEPanel.surface(g, px, py, w, h, redWhite ? 0xFFBFC3BE : 0xFFFFD62B, TEColors.BORDER);
        int ink = redWhite ? 0xFFBF2931 : 0xFF161A1C;
        float[] quads = single ? SINGLE_QUADS : WIDE_QUADS;
        float boardWidth = single ? 8 : 16;
        var matrix = g.pose().last().pose();
        var vertices = g.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < quads.length; i += 8) for (int corner = 0; corner < 4; corner++) {
            int point = i + (right ? 6 - corner * 2 : corner * 2);
            float u = right ? quads[point] : boardWidth - quads[point];
            vertices.vertex(matrix, px + u * 11, py + quads[point + 1] * 11, 0)
                .color((ink >>> 16) & 255, (ink >>> 8) & 255, ink & 255, 255).endVertex();
        }
        TEText.drawString(g, font, modelLabel, left + 16, top + 49, TEColors.MUTED, false);
        TEText.drawString(g, font, directionLabel, left + 16, top + 147, TEColors.MUTED, false);
        TEText.drawString(g, font, colorsLabel, left + 16, top + 192, TEColors.MUTED, false);
        finishWindow(g, x, y, tick);
    }
}
