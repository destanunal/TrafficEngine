package com.destan.trafficengine.data;

/** Each character is a lens mask: 0 off, 1 first, 2 second, 3 both. */
public record FlashSequence(String steps, int stepTicks) {
    public static final FlashSequence DEFAULT = new FlashSequence("12", 10);
    public FlashSequence {
        if (!valid(steps)) steps = "12";
        stepTicks = Math.max(2, Math.min(200, stepTicks));
    }
    public static boolean valid(String steps) {
        if (steps == null || steps.isEmpty() || steps.length() > 32) return false;
        for (int i = 0; i < steps.length(); i++) if (steps.charAt(i) < '0' || steps.charAt(i) > '3') return false;
        return true;
    }
    public int mask(long gameTime) { return steps.charAt((int)Math.floorMod(Math.floorDiv(gameTime, stepTicks), (long)steps.length())) - '0'; }
}
