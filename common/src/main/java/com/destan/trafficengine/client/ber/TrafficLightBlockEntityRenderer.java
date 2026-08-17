package com.destan.trafficengine.client.ber;

import de.mrjulsen.mcdragonlib.client.ber.BERGraphics;
import de.mrjulsen.mcdragonlib.client.ber.RotatableBlockEntityRenderer;
import com.destan.trafficengine.block.TrafficLightBlock;
import com.destan.trafficengine.block.data.TrafficLightColor;
import com.destan.trafficengine.block.data.TrafficLightIcon;
import com.destan.trafficengine.block.data.TrafficLightType;
import com.destan.trafficengine.block.entity.TrafficLightBlockEntity;
import com.destan.trafficengine.client.TrafficLightTextureManager;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import net.minecraft.core.BlockPos;

public class TrafficLightBlockEntityRenderer extends RotatableBlockEntityRenderer<TrafficLightBlockEntity> {

    private static final Map<String, Long> startTimes = new HashMap<>();
    private static final Map<String, Long> knownDurations = new HashMap<>();
    private static final Map<BlockPos, Integer> lastColorMap = new HashMap<>();

    private static boolean cacheLoaded = false;
    private static final Properties timerCache = new Properties();

    public TrafficLightBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    private static final int[] DIGIT_SEGMENTS = {
            0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110,
            0b1101101, 0b1111101, 0b0000111, 0b1111111, 0b1101111
    };

    private static void loadCache() {
        try {
            File dir = new File(net.minecraft.client.Minecraft.getInstance().gameDirectory, "config");
            File f = new File(dir, "te_timers.properties");
            if (f.exists()) {
                timerCache.load(new FileReader(f));
                for (String key : timerCache.stringPropertyNames()) {
                    knownDurations.put(key, Long.parseLong(timerCache.getProperty(key)));
                }
            }
        } catch (Exception ignored) {}
    }

    private static void saveCache(String key, long duration) {
        try {
            File dir = new File(net.minecraft.client.Minecraft.getInstance().gameDirectory, "config");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "te_timers.properties");
            timerCache.setProperty(key, String.valueOf(duration));
            timerCache.store(new FileWriter(f), "");
        } catch (Exception ignored) {}
    }

    private void drawRect(VertexConsumer vc, PoseStack poseStack, float x, float y, float w, float h, int color) {
        Matrix4f m = poseStack.last().pose();
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        vc.vertex(m, x, y, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x + w, y, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x + w, y + h, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x, y + h, 0).color(r, g, b, a).endVertex();

        vc.vertex(m, x + w, y, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x, y, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x, y + h, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x + w, y + h, 0).color(r, g, b, a).endVertex();
    }

    private void renderDigit(PoseStack poseStack, VertexConsumer vc, int digit, float width, float height, float thickness, int color) {
        if (digit < 0 || digit > 9) return;
        int mask = DIGIT_SEGMENTS[digit];
        float mid = height / 2.0f;

        if ((mask & 0b0000001) != 0) drawRect(vc, poseStack, 0, 0, width, thickness, color);
        if ((mask & 0b0000010) != 0) drawRect(vc, poseStack, width - thickness, 0, thickness, mid, color);
        if ((mask & 0b0000100) != 0) drawRect(vc, poseStack, width - thickness, mid, thickness, mid, color);
        if ((mask & 0b0001000) != 0) drawRect(vc, poseStack, 0, height - thickness, width, thickness, color);
        if ((mask & 0b0010000) != 0) drawRect(vc, poseStack, 0, mid, thickness, mid, color);
        if ((mask & 0b0100000) != 0) drawRect(vc, poseStack, 0, 0, thickness, mid, color);
        if ((mask & 0b1000000) != 0) drawRect(vc, poseStack, 0, mid - thickness / 2.0f, width, thickness, color);
    }

    private int getRealRemainingSeconds(TrafficLightBlockEntity be, int rawColorCode) {
        try {
            if (!cacheLoaded) {
                loadCache();
                cacheLoaded = true;
            }

            if (be.getLevel() != null && rawColorCode != 0) {
                long currentTime = be.getLevel().getGameTime();
                BlockPos pos = be.getBlockPos();
                String dim = be.getLevel().dimension().location().toString();
                String phaseKey = dim + "_" + pos.asLong() + "_" + rawColorCode;

                Integer lastCol = lastColorMap.get(pos);
                if (lastCol == null || lastCol != rawColorCode) {
                    if (lastCol != null) {
                        String lastPhaseKey = dim + "_" + pos.asLong() + "_" + lastCol;
                        long startTime = startTimes.getOrDefault(lastPhaseKey, currentTime);
                        long duration = currentTime - startTime;
                        if (duration > 10) {
                            knownDurations.put(lastPhaseKey, duration);
                            saveCache(lastPhaseKey, duration);
                        }
                    }
                    startTimes.put(phaseKey, currentTime);
                    lastColorMap.put(pos, rawColorCode);
                }

                if (knownDurations.containsKey(phaseKey)) {
                    long startTime = startTimes.getOrDefault(phaseKey, currentTime);
                    long passedTicks = currentTime - startTime;
                    long totalTicks = knownDurations.get(phaseKey);
                    long remainingTicks = totalTicks - passedTicks;

                    if (remainingTicks < 0) return 0;
                    return (int) ((remainingTicks + 19) / 20);
                }
            }
        } catch (Exception e) {
            return -1;
        }
        return -1;
    }

    @Override
    public void renderBlock(BERGraphics<TrafficLightBlockEntity> graphics, float pPartialTick) {
        BlockState blockstate = graphics.blockEntity().getBlockState();
        boolean isCountdown = graphics.blockEntity().getTLType() == TrafficLightType.COUNTDOWN;

        boolean isRed = graphics.blockEntity().isColorEnabled(TrafficLightColor.RED, true);
        boolean isYellow = graphics.blockEntity().isColorEnabled(TrafficLightColor.YELLOW, true);
        boolean isGreen = graphics.blockEntity().isColorEnabled(TrafficLightColor.GREEN, true);

        int rawColorCode = 0;
        if (isRed && isYellow) rawColorCode = 4;
        else if (isRed) rawColorCode = 1;
        else if (isYellow) rawColorCode = 2;
        else if (isGreen) rawColorCode = 3;

        if (graphics.blockEntity().getLevel() != null && rawColorCode != 0) {
            getRealRemainingSeconds(graphics.blockEntity(), rawColorCode);
        }

        int activeColorHex = 0xFFFFFFFF;
        boolean isAnyLightOn = false;
        boolean showTimer = false;

        if (isCountdown) {
            if (rawColorCode == 1) {
                activeColorHex = 0xFFFF0000;
                showTimer = true;
                isAnyLightOn = true;
            } else if (rawColorCode == 3) {
                activeColorHex = 0xFF00FF00;
                showTimer = true;
                isAnyLightOn = true;
            } else if (rawColorCode == 2 || rawColorCode == 4) {
                showTimer = false;
                isAnyLightOn = true;
            }
        }

        graphics.poseStack().pushPose();
        graphics.poseStack().translate(6f, 5.5f, 13);
        TrafficLightIcon currentIcon = graphics.blockEntity().getIcon();

        int slotCount = Math.min(graphics.blockEntity().getColorSlotCount(), blockstate.getValue(TrafficLightBlock.MODEL).getLightsCount());

        // 1. İKONLARI ÇİZME MANTIĞI
        for (int i = 0; i < slotCount; i++) {
            TrafficLightColor slotColor = graphics.blockEntity().getColorOfSlot(i);

            boolean renderIcon = true;

            if (isCountdown) {
                if (slotCount == 3 && i == 1) {
                    renderIcon = false; // 3'lü lambada orta slot her zaman sayaçtır, ikonu gizle
                } else if (slotCount == 2) {
                    // 2'li yaya lambasında, yanan ışığa göre sayacın yerine karar veriyoruz
                    if (rawColorCode == 1 && i == 1) renderIcon = false; // Kırmızı yanarken: Sayaç alt slotta(1), alt ikonu gizle
                    if (rawColorCode == 3 && i == 0) renderIcon = false; // Yeşil yanarken: Sayaç üst slotta(0), üst ikonu gizle
                } else if (slotCount == 1) {
                    renderIcon = false; // Tekli lambada sadece sayaç vardır
                }
            }

            if (renderIcon && slotColor != null && graphics.blockEntity().isColorEnabled(slotColor, true)) {
                new TrafficLightTextureManager.TrafficLightTextureKey(currentIcon, slotColor).render(graphics, graphics.blockEntity(), graphics.packedLight());
            } else {
                new TrafficLightTextureManager.TrafficLightTextureKey(currentIcon, TrafficLightColor.NONE).render(graphics, graphics.blockEntity(), graphics.packedLight());
            }
            graphics.poseStack().translate(0, 5, 0); // Bir sonraki slota (aşağı) in
        }
        graphics.poseStack().popPose();

        // 2. SAYACI ÇİZME MANTIĞI
        if (showTimer && isAnyLightOn) {
            int remainingSeconds = getRealRemainingSeconds(graphics.blockEntity(), rawColorCode);

            if (remainingSeconds >= 0) {
                graphics.poseStack().pushPose();

                // Sayacın Y ekseni pozisyonunu dinamik olarak ayarlıyoruz:
                // 3.5f = Üst Slot (0)
                // 8.5f = Orta Slot (1)
                float timerYPosition = 8.5f;

                if (slotCount == 3) {
                    timerYPosition = 8.5f; // 3'lü lambada hep orta slotta
                } else if (slotCount == 2) {
                    if (rawColorCode == 1) {
                        timerYPosition = 8.5f; // Kırmızı yanarken: Sayaç alt slota (8.5f) geçer
                    } else if (rawColorCode == 3) {
                        timerYPosition = 3.5f; // Yeşil yanarken: Sayaç üst slota (3.5f) geçer
                    }
                } else if (slotCount == 1) {
                    timerYPosition = 3.5f; // Tekli lambada direkt ana slota yerleşir
                }

                graphics.poseStack().translate(8.0f, timerYPosition, 14.05f);

                float digitWidth = 1.6f;
                float digitHeight = 3.2f;
                float digitThickness = 0.4f;
                float digitSpacing = 0.8f;

                int tensDigit = remainingSeconds / 10;
                int onesDigit = remainingSeconds % 10;

                boolean showTens = (remainingSeconds >= 10);

                float wTens = (tensDigit == 1) ? digitThickness : digitWidth;
                float wOnes = (onesDigit == 1) ? digitThickness : digitWidth;

                float totalWidth = showTens ? (wTens + digitSpacing + wOnes) : wOnes;

                graphics.poseStack().translate(-totalWidth / 2.0f, -digitHeight / 2.0f, 0);
                VertexConsumer vc = graphics.multiBufferSource().getBuffer(RenderType.gui());

                if (showTens) {
                    renderDigit(graphics.poseStack(), vc, tensDigit, wTens, digitHeight, digitThickness, activeColorHex);
                    graphics.poseStack().translate(wTens + digitSpacing, 0, 0);
                }

                renderDigit(graphics.poseStack(), vc, onesDigit, wOnes, digitHeight, digitThickness, activeColorHex);

                graphics.poseStack().popPose();
            }
        }
    }
}