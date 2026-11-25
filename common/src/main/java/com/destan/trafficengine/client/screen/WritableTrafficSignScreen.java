package com.destan.trafficengine.client.screen;

import com.destan.trafficengine.block.TownSignBlock;
import com.destan.trafficengine.block.TownSignBlock.ETownSignSide;
import com.destan.trafficengine.block.StreetSignBlock;
import com.destan.trafficengine.block.WritableTrafficSign;
import com.destan.trafficengine.block.data.IColorBlockEntity;
import com.destan.trafficengine.block.data.IPaintableBlock;
import com.destan.trafficengine.block.data.TownSignVariant;
import com.destan.trafficengine.block.entity.HouseNumberSignBlockEntity;
import com.destan.trafficengine.block.entity.StreetSignBlockEntity;
import com.destan.trafficengine.block.entity.TownSignBlockEntity;
import com.destan.trafficengine.block.entity.WritableTrafficSignBlockEntity;
import com.destan.trafficengine.client.ber.WritableSignLettering;
import com.destan.trafficengine.client.gui.TEText;
import com.destan.trafficengine.client.gui.TrafficEngineScreen;
import com.destan.trafficengine.client.gui.components.TEButton;
import com.destan.trafficengine.client.gui.components.TEEditBox;
import com.destan.trafficengine.client.gui.components.TEPanel;
import com.destan.trafficengine.client.gui.theme.TEColors;
import com.destan.trafficengine.data.PaintColor;
import com.destan.trafficengine.data.SignTextConfig.WritableSignConfig;
import com.destan.trafficengine.data.WritableSignText;
import com.destan.trafficengine.network.NetworkDirection;
import com.destan.trafficengine.network.packets.cts.TownSignPacket;
import com.destan.trafficengine.network.packets.cts.WritableSignPacket;
import com.destan.trafficengine.registry.ModNetworkManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Both faces of a town sign use one draft and a single save. */
public class WritableTrafficSignScreen extends TrafficEngineScreen {
    private static final PaintColor[] STREET_PALETTE = {
        PaintColor.NONE, PaintColor.WHITE, PaintColor.GREEN, PaintColor.BROWN, PaintColor.BLUE, PaintColor.BLACK,
        PaintColor.LIGHT_GRAY, PaintColor.GRAY, PaintColor.CYAN, PaintColor.LIGHT_BLUE, PaintColor.LIME, PaintColor.YELLOW,
        PaintColor.ORANGE, PaintColor.RED, PaintColor.PINK, PaintColor.MAGENTA, PaintColor.PURPLE
    };
    private static WeakReference<Level> lastNumberLevel = new WeakReference<>(null);
    private static String lastNumber = "";
    private final WritableTrafficSignBlockEntity sign;
    private final TownSignBlockEntity town;
    private final StreetSignBlockEntity street;
    private final boolean house;
    private final String[] initialFront, initialBack, front, back;
    private final TownSignVariant initialVariant;
    private final PaintColor initialPaint;
    private final TEButton[] streetSwatches;
    private final Component previewLabel = Component.translatable("gui.trafficengine.patterns.preview");
    private ETownSignSide side;
    private TownSignVariant variant;
    private PaintColor streetPaint;
    private Component streetColorLabel;
    private int colorLabelX, colorLabelY;
    private WritableSignConfig previewConfig;
    private BlockState previewState;
    private int previewX, previewY, previewWidth, previewHeight;
    private int previewTint, previewInk;
    private float modelScale, modelX, modelY, textX, textY, textDirection;

    public WritableTrafficSignScreen(WritableTrafficSignBlockEntity sign) {
        this(sign, null);
    }

    protected WritableTrafficSignScreen(WritableTrafficSignBlockEntity sign, ETownSignSide side) {
        super(sign.getBlockState().getBlock().getName());
        this.sign = sign;
        this.town = sign instanceof TownSignBlockEntity entity ? entity : null;
        this.street = sign instanceof StreetSignBlockEntity entity ? entity : null;
        this.house = sign instanceof HouseNumberSignBlockEntity;
        this.initialPaint = this.streetPaint = street == null ? null : street.getColor();
        this.streetSwatches = street == null ? null : new TEButton[STREET_PALETTE.length];
        front = new String[sign.getRenderConfig().lineData().length];
        for (int i = 0; i < front.length; i++) front[i] = sign.getText(i);
        initialFront = front.clone();
        if (town == null) {
            back = initialBack = null;
            initialVariant = null;
        } else {
            back = new String[town.getBackRenderConfig().lineData().length];
            for (int i = 0; i < back.length; i++) back[i] = town.getBackText(i);
            initialBack = back.clone();
            initialVariant = variant = sign.getBlockState().getValue(TownSignBlock.VARIANT);
            this.side = editableSide(side == null ? ETownSignSide.FRONT : side);
        }
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("gui.trafficengine.writable_sign." + key, arguments);
    }

    private String[] draft() { return side == ETownSignSide.BACK ? back : front; }

    @Override protected void init() {
        layoutWindow(440, town != null ? 264 : street != null ? 242 : 218);
        addCloseButton();
        int footer = top + windowHeight - 30;
        previewX = left + 12;
        previewY = top + 48;
        previewWidth = Math.min(162, (windowWidth - 32) * 2 / 5);
        previewHeight = footer - previewY - 9;
        int x = previewX + previewWidth + 12;
        int w = left + windowWidth - 12 - x;
        int rowY = previewY + 8;
        int step = 26;
        if (town != null) {
            if (variant == TownSignVariant.BOTH) {
                int half = (w - 6) / 2;
                addRenderableWidget(new TEButton(x, previewY, half, 20, text("front"), b -> chooseSide(ETownSignSide.FRONT)))
                    .setSelected(side == ETownSignSide.FRONT);
                addRenderableWidget(new TEButton(x + half + 6, previewY, w - half - 6, 20, text("back"), b -> chooseSide(ETownSignSide.BACK)))
                    .setSelected(side == ETownSignSide.BACK);
                rowY = previewY + 27;
            }
            int variantY = footer - 28;
            addRenderableWidget(new TEButton(x, variantY, w, 20, variantLabel(), b -> {
                variant = switch (variant) {
                    case FRONT -> TownSignVariant.BACK;
                    case BACK -> TownSignVariant.BOTH;
                    case BOTH -> TownSignVariant.FRONT;
                };
                side = editableSide(side);
                rebuildWidgets();
            })).setTooltip(Tooltip.create(text("variant_hint")));
            step = Math.min(25, (variantY - rowY - 4) / draft().length);
        }
        String[] lines = draft();
        for (int i = 0; i < lines.length; i++) {
            final int index = i;
            var label = text(house ? "number" : town == null ? "street_name" : "line", i + 1);
            var field = addRenderableWidget(new TEEditBox(font, x, rowY + i * step, w, Math.min(20, step - 2), label).centered());
            field.setMaxLength(Math.max(WritableSignText.MAX_LENGTH, lines[i].length()));
            field.setValue(lines[i]);
            field.setHint(label.copy().withStyle(style -> style.withColor(TEColors.MUTED & 0xFFFFFF)));
            field.setResponder(value -> lines[index] = value);
            if (i == 0) setInitialFocus(field);
            if (house && footer - rowY >= 56) addNumberActions(field, x, rowY + 29, w, footer - 5);
        }
        if (street != null) addStreetColors(x, rowY + 26, w, footer);
        int buttonWidth = Math.min(90, (windowWidth - 32) / 2);
        int right = left + windowWidth - 12;
        addRenderableWidget(new TEButton(right - 2 * buttonWidth - 6, footer, buttonWidth, 20, CommonComponents.GUI_CANCEL, b -> onClose()));
        addRenderableWidget(new TEButton(right - buttonWidth, footer, buttonWidth, 20, text("apply"), b -> apply()).primary());
        layoutPreview();
    }

    private void chooseSide(ETownSignSide next) {
        if (side == next) return;
        side = next;
        rebuildWidgets();
    }

    private ETownSignSide editableSide(ETownSignSide preferred) {
        return switch (variant) {
            case FRONT -> ETownSignSide.FRONT;
            case BACK -> ETownSignSide.BACK;
            case BOTH -> preferred;
        };
    }

    private static Component streetColorName(PaintColor paint) {
        return paint == PaintColor.NONE ? text("default_blue") : paint.getValueTranslation();
    }

    private static int streetPanelColor(PaintColor paint) {
        return paint == PaintColor.NONE ? StreetSignBlock.DEFAULT_PANEL_COLOR.getAsARGB() : paint.getTextureARGB();
    }

    private void addStreetColors(int x, int y, int width, int footer) {
        colorLabelX = x + width / 2;
        colorLabelY = y;
        int columns = 6;
        int gap = 4;
        int swatchWidth = (width - (columns - 1) * gap) / columns;
        int swatchHeight = Math.max(10, Math.min(18, (footer - y - 23) / 3));
        int gridWidth = columns * swatchWidth + (columns - 1) * gap;
        int startX = x + (width - gridWidth) / 2;
        for (int i = 0; i < STREET_PALETTE.length; i++) {
            PaintColor paint = STREET_PALETTE[i];
            Component name = streetColorName(paint);
            int tint = streetPanelColor(paint);
            int row = i / columns;
            int swatchX = startX + i % columns * (swatchWidth + gap);
            if (row == 2) swatchX += (swatchWidth + gap) / 2;
            streetSwatches[i] = addRenderableWidget(new TEButton(swatchX, y + 15 + row * (swatchHeight + gap),
                swatchWidth, swatchHeight, name, b -> selectStreetPaint(paint)) {
                @Override protected void renderLabel(GuiGraphics g, int ink) {
                    g.fill(getX() + 4, getY() + 3, getX() + getWidth() - 4, getY() + getHeight() - 3, tint);
                }
            });
            streetSwatches[i].setTooltip(Tooltip.create(name));
        }
        selectStreetPaint(streetPaint);
    }

    private void selectStreetPaint(PaintColor paint) {
        streetPaint = paint;
        streetColorLabel = text("panel_color", streetColorName(paint));
        previewTint = streetPanelColor(paint);
        previewInk = StreetSignBlockEntity.textColor(paint);
        for (int i = 0; i < streetSwatches.length; i++) streetSwatches[i].setSelected(STREET_PALETTE[i] == paint);
    }

    private Component variantLabel() {
        Component target = switch (variant) {
            case FRONT -> text("front");
            case BACK -> text("back");
            case BOTH -> variant.getValueTranslation();
        };
        return text("visible_faces", target);
    }

    private void addNumberActions(TEEditBox field, int x, int y, int width, int bottom) {
        Component seriesLabel = text("next_number");
        boolean stacked = y + 44 <= bottom && TEText.width(font, seriesLabel) + 8 > width - 60;
        int stepWidth = stacked ? (width - 6) / 2 : 24;
        var previous = addRenderableWidget(new TEButton(x, y, stepWidth, 20, Component.literal("−"),
            b -> field.setValue(WritableSignText.stepNumber(field.getValue(), -1))));
        var next = addRenderableWidget(new TEButton(x + width - stepWidth, y, stepWidth, 20, Component.literal("+"),
            b -> field.setValue(WritableSignText.stepNumber(field.getValue(), 1))));
        previous.setTooltip(Tooltip.create(text("decrease")));
        next.setTooltip(Tooltip.create(text("increase")));
        String seriesNext = lastNumberLevel.get() == sign.getLevel() ? WritableSignText.stepNumber(lastNumber, 1) : "";
        var series = addRenderableWidget(new TEButton(stacked ? x : x + 30, stacked ? y + 24 : y, stacked ? width : width - 60,
            20, seriesLabel, b -> field.setValue(seriesNext)));
        if (!seriesNext.isEmpty()) series.setTooltip(Tooltip.create(Component.literal(seriesNext)));
        java.util.function.Consumer<String> update = value -> {
            front[0] = value;
            previous.active = !WritableSignText.stepNumber(value, -1).equals(value);
            next.active = !WritableSignText.stepNumber(value, 1).equals(value);
            series.active = value.isEmpty() && !seriesNext.isEmpty() && !seriesNext.equals(lastNumber);
        };
        field.setResponder(update);
        update.accept(field.getValue());
    }

    private void apply() {
        String[] changedFront = Arrays.equals(front, initialFront) ? null : front;
        if (town != null) {
            String[] changedBack = Arrays.equals(back, initialBack) ? null : back;
            TownSignVariant changedVariant = variant == initialVariant ? null : variant;
            if (changedFront != null || changedBack != null || changedVariant != null) {
                ModNetworkManager.UPDATE_TOWN_SIGN.send(NetworkDirection.toServer(),
                    new TownSignPacket(sign.getBlockPos(), changedFront, changedBack, changedVariant));
            }
        } else {
            PaintColor changedPaint = streetPaint == initialPaint ? null : streetPaint;
            if (changedFront != null || changedPaint != null) {
                ModNetworkManager.UPDATE_WRITABLE_SIGN.send(NetworkDirection.toServer(),
                    new WritableSignPacket(sign.getBlockPos(), changedFront, changedPaint));
            }
            if (house && changedFront != null && !WritableSignText.stepNumber(front[0], 1).equals(front[0])) {
                lastNumber = front[0];
                lastNumberLevel = new WeakReference<>(sign.getLevel());
            }
        }
        onClose();
    }

    private void layoutPreview() {
        previewConfig = town == null ? sign.getRenderConfig() : town.getTownSignRenderConfig(side);
        previewState = sign.getBlockState().setValue(WritableTrafficSign.FACING, Direction.NORTH);
        if (town != null) previewState = previewState.setValue(TownSignBlock.VARIANT,
            side == ETownSignSide.BACK ? TownSignVariant.BACK : TownSignVariant.FRONT);
        if (street == null) {
            previewTint = -1;
            previewInk = previewConfig.berColor();
            if (sign instanceof IColorBlockEntity colored && previewState.getBlock() instanceof IPaintableBlock paintable) {
                PaintColor paint = colored.getColor();
                previewTint = paint == PaintColor.NONE ? paintable.getDefaultColor().getAsARGB() : paint.getTextureARGB();
            }
        }
        float signWidth = street == null ? 1 : previewConfig.renderBack() ? StreetSignBlock.POST_PLATE_WIDTH : StreetSignBlock.WALL_PLATE_WIDTH;
        float scaleWidth = (previewWidth - 18) / signWidth;
        float scaleHeight = street == null ? previewHeight - 30 : (previewHeight - 30) * 2;
        modelScale = Math.max(1, Math.min(scaleWidth, scaleHeight));
        modelX = previewX + previewWidth * .5F + (.5F + previewConfig.xCenterOffset()) * modelScale;
        float raisedSign = street != null ? .75F : .5F;
        modelY = previewY + (previewHeight + 22) * .5F + raisedSign * modelScale;
        float modelAngle = (float)Math.toRadians(previewConfig.yRot());
        boolean opposite = previewConfig.renderBack();
        double angle = Math.toRadians(previewConfig.yRot() + previewConfig.blockEntityRendererRotation().applyAsDouble(previewState)
            + (opposite ? 180 : 0));
        textDirection = (float)Math.cos(angle);
        float pivot = (float)(Math.cos(modelAngle) + Math.sin(modelAngle)) * .5F;
        textX = modelX - modelScale * (pivot + textDirection * (opposite ? -previewConfig.berX() : previewConfig.berX())
            + (float)Math.sin(angle) * previewConfig.berZ());
        textY = modelY - modelScale * (.5F + previewConfig.berY());
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        renderWindow(g);
        renderPreview(g);
        if (street != null) {
            TEText.drawCenteredString(g, font, streetColorLabel, colorLabelX,
                TEText.centeredY(g, font, streetColorLabel, colorLabelY, 13), TEColors.MUTED);
        }
        super.render(g, mouseX, mouseY, tick);
    }

    private void renderPreview(GuiGraphics g) {
        TEPanel.draw(g, previewX, previewY, previewWidth, previewHeight);
        TEText.drawCenteredString(g, font, previewLabel, previewX + previewWidth * .5F,
            TEText.centeredY(g, font, previewLabel, previewY, 23), TEColors.MUTED);
        g.enableScissor(previewX + 2, previewY + 23, previewX + previewWidth - 2, previewY + previewHeight - 2);
        g.flush();
        Lighting.setupForFlatItems();
        g.pose().pushPose();
        g.pose().translate(modelX, modelY, 100);
        g.pose().scale(-modelScale, -modelScale, -1);
        g.pose().mulPose(Axis.YP.rotationDegrees(previewConfig.yRot()));
        var renderer = minecraft.getBlockRenderer();
        renderer.getModelRenderer().renderModel(g.pose().last(),
            g.bufferSource().getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)),
            previewState, renderer.getBlockModel(previewState), (previewTint >>> 16 & 255) / 255F,
            (previewTint >>> 8 & 255) / 255F, (previewTint & 255) / 255F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        g.flush();
        g.pose().popPose();
        Lighting.setupFor3DItems();

        float unit = modelScale / previewConfig.scale();
        String[] lines = draft();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;
            var lettering = WritableSignLettering.layout(font, line);
            int width = lettering.width();
            if (width == 0) continue;
            float sx = previewConfig.scaleX(i, width), sy = previewConfig.scaleY(i, width);
            g.pose().pushPose();
            g.pose().translate(textX - textDirection * previewConfig.lineData()[i].xOffset() * modelScale,
                textY + previewConfig.baseline(i, sy) * unit, 105);
            g.pose().scale(sx * unit, sy * unit, 1);
            TEText.drawString(g, font, lettering.glyphs(), -width * .5F, 0, previewInk | 0xFF000000, false);
            g.pose().popPose();
        }
        g.disableScissor();
    }
}
