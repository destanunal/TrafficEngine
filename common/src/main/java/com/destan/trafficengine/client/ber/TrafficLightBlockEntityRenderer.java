package com.destan.trafficengine.client.ber;

import com.destan.trafficengine.client.ber.RenderContext;
import com.destan.trafficengine.client.ber.RotatableBlockEntityRenderer;
import com.destan.trafficengine.block.TrafficLightBlock;
import com.destan.trafficengine.block.HorizontalTrafficLightBlock;
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
import com.mojang.math.Axis;

import java.util.Map;

public class TrafficLightBlockEntityRenderer extends RotatableBlockEntityRenderer<TrafficLightBlockEntity> {

    private static final Map<String, Long> startTimes = BufferedTimerProperties.boundedMap(BufferedTimerProperties.PHASE_LIMIT);
    private static final Map<String, Long> knownDurations = BufferedTimerProperties.boundedMap(BufferedTimerProperties.PHASE_LIMIT);
    private static final Map<String, Integer> lastColorMap = BufferedTimerProperties.boundedMap(BufferedTimerProperties.LIGHT_LIMIT);
    private static final Map<String, Long> lastLitTimes = BufferedTimerProperties.boundedMap(BufferedTimerProperties.LIGHT_LIMIT);
    private static final long BLINK_HOLD_TICKS = 40L;

    private static boolean cacheLoaded = false;
    private static final BufferedTimerProperties timerPersistence = new BufferedTimerProperties(
        () -> net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("config/te_timers.properties"), knownDurations);

    private final HorizontalTrafficLightBlockEntityRenderer horizontalRenderer; // YENİ EKLENEN SATIR

    public TrafficLightBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
        this.horizontalRenderer = new HorizontalTrafficLightBlockEntityRenderer(context); // YENİ EKLENEN SATIR
    }

    private static final int[] DIGIT_SEGMENTS = {
            0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110,
            0b1101101, 0b1111101, 0b0000111, 0b1111111, 0b1101111
    };

    private static void loadCache() { timerPersistence.load(); }
    private static void saveCache(String key, long duration) { timerPersistence.remember(key, duration); }
    public static void flushTimerCache() { timerPersistence.flush(false); }

    public static void clearRuntimeCache() {
        TrafficLightTextureManager.clearStencilCache();
        timerPersistence.flush(true);
        startTimes.clear();
        knownDurations.clear();
        lastColorMap.clear();
        lastLitTimes.clear();
        timerPersistence.reset();
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
                String lightKey = be.getTimingKey();
                String phaseKey = be.getTimingPhaseKey(rawColorCode);

                Integer lastCol = lastColorMap.get(lightKey);
                if (lastCol == null || lastCol != rawColorCode) {
                    if (lastCol != null) {
                        String lastPhaseKey = be.getTimingPhaseKey(lastCol);
                        long startTime = startTimes.getOrDefault(lastPhaseKey, currentTime);
                        long duration = currentTime - startTime;
                        if (duration > 10) {
                            saveCache(lastPhaseKey, duration);
                        }
                    }
                    long savedStart = be.getColorPhaseStart();
                    startTimes.put(phaseKey, savedStart >= 0 && savedStart <= currentTime ? savedStart : currentTime);
                    lastColorMap.put(lightKey, rawColorCode);
                }

                Long totalTicks = knownDurations.get(phaseKey);
                if (totalTicks != null) {
                    long startTime = startTimes.getOrDefault(phaseKey, currentTime);
                    long passedTicks = Math.max(0L, currentTime - startTime);
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
    public void renderBlock(RenderContext<TrafficLightBlockEntity> graphics, float pPartialTick) {
        BlockState state = graphics.blockEntity() == null ? null : graphics.blockEntity().getBlockState();
        boolean diagonal = state != null
            && !(state.getBlock() instanceof HorizontalTrafficLightBlock)
            && !(state.getBlock() instanceof com.destan.trafficengine.block.WarningLightBlock)
            && state.hasProperty(TrafficLightBlock.DIAGONAL)
            && state.getValue(TrafficLightBlock.DIAGONAL);

        if (diagonal) {
            graphics.poseStack().pushPose();
            graphics.poseStack().translate(8.0f, 8.0f, 8.0f);
            graphics.poseStack().mulPose(Axis.YP.rotationDegrees(-45.0f));
            graphics.poseStack().translate(-8.0f, -8.0f, -8.0f);
        }

        try {
            renderOriented(graphics, graphics.blockEntity(), pPartialTick);
            if(graphics.blockEntity() instanceof com.destan.trafficengine.block.entity.DoubleTrafficLightBlockEntity light) {
                graphics.poseStack().pushPose();
                graphics.poseStack().translate(8,8,8);graphics.poseStack().mulPose(Axis.YP.rotationDegrees(180));graphics.poseStack().translate(-8,-8,-8);
                renderOriented(graphics,light.face(1),pPartialTick);
                graphics.poseStack().popPose();
            }
        } finally {
            if (diagonal) {
                graphics.poseStack().popPose();
            }
        }
    }

    private void renderOriented(RenderContext<TrafficLightBlockEntity> graphics, TrafficLightBlockEntity be, float pPartialTick) {
        if (be.getBlockState().getBlock() instanceof com.destan.trafficengine.block.WarningLightBlock) {
            WarningLightRenderer.render(graphics); return;
        }
        if (be.getBlockState().getBlock() instanceof HorizontalTrafficLightBlock) {
            this.horizontalRenderer.renderBlock(graphics, pPartialTick);
            return;
        }

        BlockState blockstate = be.getBlockState();
        boolean isCountdown = be.getTLType() == TrafficLightType.COUNTDOWN;

        boolean isRed = be.isColorEnabled(TrafficLightColor.RED, true);
        boolean isYellow = be.isColorEnabled(TrafficLightColor.YELLOW, true);
        boolean isGreen = be.isColorEnabled(TrafficLightColor.GREEN, true);

        int rawColorCode = 0;
        if (isRed && isYellow) rawColorCode = 4;
        else if (isRed) rawColorCode = 1;
        else if (isYellow) rawColorCode = 2;
        else if (isGreen) rawColorCode = 3;

        if (isCountdown && !be.hasScheduleTiming() && be.getLevel() != null) {
            String lightKey = be.getTimingKey();
            long gameTime = be.getLevel().getGameTime();
            if (rawColorCode != 0) {
                Long previousLit = lastLitTimes.get(lightKey);
                if (previousLit == null || previousLit.longValue() != gameTime) lastLitTimes.put(lightKey, gameTime);
            } else {
                Integer heldColor = lastColorMap.get(lightKey);
                Long lastLit = lastLitTimes.get(lightKey);
                if (heldColor != null && lastLit != null && gameTime >= lastLit && gameTime - lastLit <= BLINK_HOLD_TICKS) {
                    rawColorCode = heldColor;
                }
            }
        }

        int remainingSeconds = isCountdown && be.getLevel() != null && rawColorCode != 0
            ? getRealRemainingSeconds(be, rawColorCode) : -1;

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
        // New vertical lenses sit against the bezel, not on the legacy floating plane.
        float lensOffset=TrafficLightTextureManager.usesRoundLenses(blockstate.getBlock())?-0.5F:0F;
        graphics.poseStack().translate(6f, 5.5f, 13+lensOffset);
        TrafficLightIcon currentIcon = be.getIcon();

        int slotCount = Math.min(be.getColorSlotCount(), blockstate.getValue(TrafficLightBlock.MODEL).getLightsCount());

        // 1. İKONLARI ÇİZME MANTIĞI
        for (int i = 0; i < slotCount; i++) {
            TrafficLightColor slotColor = be.getColorOfSlot(i);

            boolean renderIcon = true;

            if (isCountdown) {
                // YENİ EKLENEN: 4'lü lambada sayaç her zaman en altta (3. indeks) gösterilsin
                if (slotCount == 4 && i == 3) {
                    if (showTimer) {
                        renderIcon = false;
                    }
                }
                // 3'lü lambada sayaç ortada (1. indeks)
                else if (slotCount == 3 && i == 1) {
                    if (showTimer) {
                        renderIcon = false;
                    }
                }
                else if (slotCount == 2) {
                    // 2'li yaya lambasında, yanan ışığa göre sayacın yerine karar ver
                    if (rawColorCode == 1 && i == 1) renderIcon = false; // Kırmızı
                    if (rawColorCode == 3 && i == 0) renderIcon = false; // Yeşil
                }
                else if (slotCount == 1) {
                    renderIcon = false; // Tekli lambada sadece sayaç vardır
                }
            }

            if (renderIcon && slotColor != null && be.isColorEnabled(slotColor, true) && !be.isScheduleBlinkOff()) {
                TrafficLightTextureManager.renderSlot(graphics, be, i, currentIcon, slotColor, graphics.packedLight());
            } else {
                TrafficLightTextureManager.renderSlot(graphics, be, i, currentIcon, TrafficLightColor.NONE, graphics.packedLight());
            }
            graphics.poseStack().translate(0, 5, 0); // Bir sonraki slota (aşağı) in
        }
        graphics.poseStack().popPose();

        // 2. SAYACI ÇİZME MANTIĞI
        if (showTimer && isAnyLightOn) {

            if (remainingSeconds > 0) {
                graphics.poseStack().pushPose();

// Sayacın Y ekseni pozisyonunu dinamik olarak ayarlıyoruz:
                // 3.5f = Üst Slot (0)
                // 8.5f = Orta Slot (1)
                // 13.5f = Alt Slot (2)
                // 18.5f = Dörtlü Lamba En Alt Slotu (3)
                float timerYPosition = 8.5f;

                // SİHİRLİ DOKUNUŞ: 4'lü lamba için sayacın konumunu ekledik
                if (slotCount == 4) {
                    timerYPosition = 18.5f; // 4'lü lambada hep en alt (4.) slotta
                } else if (slotCount == 3) {
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

                graphics.poseStack().translate(8.0f, timerYPosition, 14.05f+lensOffset);

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
