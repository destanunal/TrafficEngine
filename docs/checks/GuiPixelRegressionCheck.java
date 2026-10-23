package com.destan.trafficengine.client.gui;

import org.joml.Matrix4f;

/** Whole physical pixels prevent accented letters and ASCII from acquiring different stroke weights. */
public final class GuiPixelRegressionCheck {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        int checks = 0;
        for (double gui : new double[]{1, 1.5, 2, 3, 4}) for (float scale : new float[]{.5F, .75F, .9F, .94F, 1, 1.25F})
            for (int x = -3; x < 240; x += 7) {
                Matrix4f pose = new Matrix4f().translation(17.3F, 9.2F, 5).scale(scale, scale, 1);
                float expectedX = Math.round((pose.m30() + x * scale) * gui) / (float)gui;
                TEText.position(pose, x, x / 2F, gui);
                check(Math.abs(pose.m00() * gui - Math.round(pose.m00() * gui)) < .0001, "Horizontal glyph strokes must use whole pixels");
                check(Math.abs(pose.m11() * gui - Math.round(pose.m11() * gui)) < .0001, "Vertical glyph strokes must use whole pixels");
                check(Math.abs(pose.m30() - expectedX) < .0001, "Caption anchor must stay on the layout");
                check(pose.m32() == 5 && pose.m22() == 1, "Text alignment must preserve depth");
                checks++;
            }
        check(TEText.pixelScale(.02F, 2) == .02F, "Sign preview text must retain its model scale");
        check(TEText.pixelScale(3, 2) == 3, "Large previews must retain their scale");
        System.out.println("PASS: " + checks + " text scale/position combinations; sign preview scales preserved.");
    }
}
