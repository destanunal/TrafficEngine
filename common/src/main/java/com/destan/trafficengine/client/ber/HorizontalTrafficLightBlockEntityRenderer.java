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

public class HorizontalTrafficLightBlockEntityRenderer extends RotatableBlockEntityRenderer<TrafficLightBlockEntity> {

    private static final Map<String, Long> startTimes = new HashMap<>();
    private static final Map<String, Long> knownDurations = new HashMap<>();
    private static final Map<String, Integer> lastColorMap = new HashMap<>();
    private static final Map<String, Long> lastLitTimes = new HashMap<>();
    private static final long BLINK_HOLD_TICKS = 40L;

    private static boolean cacheLoaded = false;
    private static final Properties timerCache = new Properties();

    public HorizontalTrafficLightBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    private static final int[] DIGIT_SEGMENTS = {
            0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110,
            0b1101101, 0b1111101, 0b0000111, 0b1111111, 0b1101111
    };

    private static void loadCache() {
        try {
            File dir = new File(net.minecraft.client.Minecraft.getInstance().gameDirectory, "config");
            File f = new File(dir, "te_timers_hz.properties");
            if (f.exists()) {
                try (FileReader reader = new FileReader(f)) {
                    timerCache.load(reader);
                }
                for (String key : timerCache.stringPropertyNames()) {
                    knownDurations.put(key, Long.parseLong(timerCache.getProperty(key)));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void saveCache(String key, long duration) {
        try {
            File dir = new File(net.minecraft.client.Minecraft.getInstance().gameDirectory, "config");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "te_timers_hz.properties");
            timerCache.setProperty(key, String.valueOf(duration));
            try (FileWriter writer = new FileWriter(f)) {
                timerCache.store(writer, "");
            }
        } catch (Exception ignored) {
        }
    }

    public static void clearRuntimeCache() {
        startTimes.clear();
        knownDurations.clear();
        lastColorMap.clear();
        lastLitTimes.clear();
        timerCache.clear();
        cacheLoaded = false;
    }

    private void drawRect(VertexConsumer vc, PoseStack poseStack, float x, float y, float w, float h, int color) {
        Matrix4f m = poseStack.last().pose();
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        vc.addVertex(m, x, y, 0).setColor(r, g, b, a);
        vc.addVertex(m, x + w, y, 0).setColor(r, g, b, a);
        vc.addVertex(m, x + w, y + h, 0).setColor(r, g, b, a);
        vc.addVertex(m, x, y + h, 0).setColor(r, g, b, a);

        vc.addVertex(m, x + w, y, 0).setColor(r, g, b, a);
        vc.addVertex(m, x, y, 0).setColor(r, g, b, a);
        vc.addVertex(m, x, y + h, 0).setColor(r, g, b, a);
        vc.addVertex(m, x + w, y + h, 0).setColor(r, g, b, a);
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
        if (be.hasScheduleTiming()) return be.getScheduleRemainingSeconds();
        try {
            if (!cacheLoaded) {
                loadCache();
                cacheLoaded = true;
            }

            if (be.getLevel() != null && rawColorCode != 0) {
                long currentTime = be.getLevel().getGameTime();
                String dim = be.getLevel().dimension().location().toString();
                String lightKey = dim + "_" + be.getBlockPos().asLong();
                String phaseKey = lightKey + "_" + rawColorCode;

                Integer lastCol = lastColorMap.get(lightKey);
                if (lastCol == null || lastCol != rawColorCode) {
                    if (lastCol != null) {
                        String lastPhaseKey = lightKey + "_" + lastCol;
                        long startTime = startTimes.getOrDefault(lastPhaseKey, currentTime);
                        long duration = currentTime - startTime;
                        if (duration > 10) {
                            knownDurations.put(lastPhaseKey, duration);
                            saveCache(lastPhaseKey, duration);
                        }
                    }
                    long savedStart = be.getColorPhaseStart();
                    startTimes.put(phaseKey, savedStart >= 0 && savedStart <= currentTime ? savedStart : currentTime);
                    lastColorMap.put(lightKey, rawColorCode);
                }

                if (knownDurations.containsKey(phaseKey)) {
                    long startTime = startTimes.getOrDefault(phaseKey, currentTime);
                    long passedTicks = Math.max(0L, currentTime - startTime);
                    long totalTicks = knownDurations.get(phaseKey);
                    if (totalTicks <= 0L) return -1;
                    long remainingTicks = totalTicks - (passedTicks % totalTicks);
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

        if (isCountdown && !graphics.blockEntity().hasScheduleTiming() && graphics.blockEntity().getLevel() != null) {
            String lightKey = graphics.blockEntity().getLevel().dimension().location() + "_"
                + graphics.blockEntity().getBlockPos().asLong();
            long gameTime = graphics.blockEntity().getLevel().getGameTime();
            if (rawColorCode != 0) {
                lastLitTimes.put(lightKey, gameTime);
            } else {
                Integer heldColor = lastColorMap.get(lightKey);
                Long lastLit = lastLitTimes.get(lightKey);
                if (heldColor != null && lastLit != null && gameTime >= lastLit && gameTime - lastLit <= BLINK_HOLD_TICKS) {
                    rawColorCode = heldColor;
                }
            }
        }

        if (isCountdown && graphics.blockEntity().getLevel() != null && rawColorCode != 0) {
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

        int slotCount = Math.min(graphics.blockEntity().getColorSlotCount(), blockstate.getValue(TrafficLightBlock.MODEL).getLightsCount());

        int timerSlotIndex = -1;
        if (isCountdown && showTimer) {
            if (slotCount == 1) {
                timerSlotIndex = 0;
            } else if (slotCount == 2) {
                // Kırmızı solda (0), Yeşil sağda (1) olduğu için
                // Kırmızı yanarken (1), sayaç yeşilin yerinde (1)
                // Yeşil yanarken (3), sayaç kırmızının yerinde (0)
                timerSlotIndex = (rawColorCode == 1) ? 1 : 0;
            } else if (slotCount == 3) {
                timerSlotIndex = 1; // Ortada
            } else if (slotCount >= 4) {
                timerSlotIndex = 3; // En sağda
            }
        }

        // Soldan Sağa çizim için başlangıç noktaları
        float startX = 1.0f;
        if (slotCount == 1) startX = 6.0f;
        else if (slotCount == 2) startX = 3.5f;
        else if (slotCount == 3) startX = 1.0f;
        else if (slotCount >= 4) startX = -1.5f;

        graphics.poseStack().pushPose();
        graphics.poseStack().translate(startX, 10.5f, 13.0f);

        TrafficLightIcon currentIcon = graphics.blockEntity().getIcon();

        // 1. IŞIKLARI ÇİZ (Artık +5.0f ile Soldan Sağa gidiyor)
        for (int i = 0; i < slotCount; i++) {
            TrafficLightColor slotColor = graphics.blockEntity().getColorOfSlot(i);
            boolean renderIcon = true;

            if (showTimer && i == timerSlotIndex) {
                renderIcon = false;
            }

            if (renderIcon && slotColor != null && graphics.blockEntity().isColorEnabled(slotColor, true) && !graphics.blockEntity().isScheduleBlinkOff()) {
                TrafficLightTextureManager.render(graphics, graphics.blockEntity(), currentIcon, slotColor, graphics.packedLight());
            } else {
                TrafficLightTextureManager.render(graphics, graphics.blockEntity(), currentIcon, TrafficLightColor.NONE, graphics.packedLight());
            }

            // Sağa doğru kaydır (+5.0f)
            graphics.poseStack().translate(5.0f, 0.0f, 0.0f);
        }
        graphics.poseStack().popPose();

        // 2. SAYACI ÇİZ
        if (showTimer && isAnyLightOn && timerSlotIndex != -1) {
            int remainingSeconds = getRealRemainingSeconds(graphics.blockEntity(), rawColorCode);

            if (remainingSeconds > 0) {
                graphics.poseStack().pushPose();

                // Çizim yönü sağa doğru olduğu için sayaç X pozisyonu artar
                float timerX = startX + (timerSlotIndex * 5.0f) + 2.0f;

                // Yüksekliği 9.5f yaptık (8.0f ile 12.5f'nin tam arası, üste yapışmaz, alta inmez)
                graphics.poseStack().translate(timerX, 8.6f, 14.05f);

                float digitWidth = 1.6f;
                float digitHeight = 3.2f;
                float digitThickness = 0.4f;
                float digitSpacing = 0.8f;

                String digits = Integer.toString(Math.min(999, remainingSeconds));
                float totalWidth = 0;
                for (int i = 0; i < digits.length(); i++) {
                    if (i > 0) totalWidth += digitSpacing;
                    totalWidth += digits.charAt(i) == '1' ? digitThickness : digitWidth;
                }
                float scale = totalWidth > 4.2f ? 4.2f / totalWidth : 1.0f;
                graphics.poseStack().scale(scale, scale, 1.0f);
                graphics.poseStack().translate(-totalWidth / 2.0f, -digitHeight / 2.0f, 0);
                VertexConsumer vc = graphics.multiBufferSource().getBuffer(RenderType.gui());
                for (int i = 0; i < digits.length(); i++) {
                    int digit = digits.charAt(i) - '0';
                    float digitActualWidth = digit == 1 ? digitThickness : digitWidth;
                    renderDigit(graphics.poseStack(), vc, digit, digitActualWidth, digitHeight, digitThickness, activeColorHex);
                    if (i + 1 < digits.length()) graphics.poseStack().translate(digitActualWidth + digitSpacing, 0, 0);
                }
                graphics.poseStack().popPose();
            }
        }
    }
}
