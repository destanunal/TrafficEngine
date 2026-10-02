package com.destan.trafficengine.client.gui.components;

import com.destan.trafficengine.client.ModGuiIcons;
import com.destan.trafficengine.client.gui.theme.TEColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;

/** Shared close control for every Traffic Engine menu. */
public class TECloseButton extends TEButton {
    public TECloseButton(int x, int y, int width, int height, OnPress action) {
        super(x, y, width, height, CommonComponents.GUI_CANCEL, action);
        setTooltip(Tooltip.create(CommonComponents.GUI_CANCEL));
    }

    @Override protected void renderLabel(GuiGraphics g, int color) {
        int foreground = active ? 0xFFFF6B6B : TEColors.MUTED;
        var transform = g.pose().last().pose();
        float scaleX = transform.m00(), scaleY = transform.m11();
        if (scaleX == 1.0F && scaleY == 1.0F) {
            TEWorkbenchIcons.draw(g, ModGuiIcons.CANCEL,
                getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2, foreground);
            return;
        }
        // Fractional menu scaling gives the two diagonals uneven pixel steps.
        // Draw the icon at native GUI resolution, centered on whole pixels.
        float originX = transform.m30(), originY = transform.m31();
        int x = Math.round((getX() + getWidth() / 2.0F) * scaleX + originX) - 8;
        int y = Math.round((getY() + getHeight() / 2.0F) * scaleY + originY) - 8;
        g.pose().pushPose();
        try {
            g.pose().translate(-originX / scaleX, -originY / scaleY, 0);
            g.pose().scale(1.0F / scaleX, 1.0F / scaleY, 1);
            TEWorkbenchIcons.draw(g, ModGuiIcons.CANCEL, x, y, foreground);
        } finally {
            g.pose().popPose();
        }
    }
}
