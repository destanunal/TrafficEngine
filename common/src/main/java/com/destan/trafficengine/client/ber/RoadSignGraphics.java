package com.destan.trafficengine.client.ber;

import com.destan.trafficengine.block.RoadInformationSignBlock.Kind;
import com.destan.trafficengine.data.RoadSignDesign;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/** The editor preview and placed sign share exactly the same lettering and arrows. */
public final class RoadSignGraphics {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("trafficengine", "textures/block/road_sign_white.png");
    private static final ResourceLocation ROAD_FONT = ResourceLocation.fromNamespaceAndPath("trafficengine", "road_sign");
    private static final net.minecraft.network.chat.Style ROAD_STYLE = net.minecraft.network.chat.Style.EMPTY.withFont(ROAD_FONT);
    private static final net.minecraft.network.chat.Style ROAD_BOLD_STYLE = net.minecraft.network.chat.Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath("trafficengine", "road_sign_bold"));
    private boolean modernLettering;
    private final Layout layout;
    public static final class Layout {
        private final Row[] rows = {new Row(), new Row(), new Row()};
    }
    private static final class Row {
        int panel, group, captionWidth;
        boolean centerArrow, cachedBold;
        String captionValue, distanceValue;
        Component caption, distance;
        float center, dividerY, textCenter, textHeight, minimumPanelHeight, left, right, x, scale;
        float distanceRight, distanceScale, distanceWidth, arrowX, arrowY, arrowScale;
        float panelLeft, panelRight, panelTop, panelBottom;
        void updateLabels(String value, String km, boolean bold) {
            if (!value.equals(captionValue) || bold != cachedBold) {
                captionValue = value; cachedBold = bold;
                caption = Component.literal(value).setStyle(bold ? ROAD_BOLD_STYLE : ROAD_STYLE);
            }
            if (!km.equals(distanceValue)) {
                distanceValue = km; distance = Component.literal(km).setStyle(ROAD_STYLE);
            }
        }
    }
    private final PoseStack pose;
    private final MultiBufferSource buffers;
    private final Font font;
    private final int light;
    private final boolean world;
    private float depth;
    private VertexConsumer geometryBuffer;
    private float geometryDepth;
    private static final double[] CIRCLE_COS = new double[97], CIRCLE_SIN = new double[97];
    private static final double[] CORNER_COS = new double[20], CORNER_SIN = new double[20];
    private static final double[][] ARROWS = new double[7][14];
    private static final double[][] ARROW_CENTERS = new double[7][2];
    private static final float[] ARROW_HEIGHTS = new float[7];
    private static final int[] ICON_VERTICES = {0, 1, 2, 6, 5, 3, 4, 4};
    // Two sizes, three frame styles and four connection masks; built once, reused every frame.
    private static final double[][][][][][] SIGN_FRAMES = new double[2][3][4][][][];
    static {
        for (int i=0;i<=96;i++) { double angle=i*Math.PI/48;CIRCLE_COS[i]=Math.cos(angle);CIRCLE_SIN[i]=Math.sin(angle); }
        for (int c=0;c<4;c++) for(int s=0;s<=4;s++) { double angle=Math.toRadians(-90+c*90+s*22.5);CORNER_COS[c*5+s]=Math.cos(angle);CORNER_SIN[c*5+s]=Math.sin(angle); }
        for (int size = 0; size < 2; size++) for (int frame = 0; frame < 3; frame++) for (int joins = 0; joins < 4; joins++) {
            double[][][] layers = new double[frame + 1][][];
            for (int layer = 0; layer <= frame; layer++) {
                double inset = layer == 0 ? 0 : frame == 1 ? 1.3 : layer == 1 ? 0.8 : 2.8;
                boolean left = (joins & 1) != 0, right = (joins & 2) != 0;
                layers[layer] = roundedPerimeter(left ? 0 : inset, inset, right ? 192 : 192 - inset,
                        (size == 0 ? 72 : 144) - inset, left ? 0 : 4 - inset, right ? 0 : 4 - inset);
            }
            SIGN_FRAMES[size][frame][joins] = layers;
        }
        double[][] points={{-2,12},{2,12},{2,-2},{7,-2},{0,-11},{-7,-2},{-2,-2}};
        for(int direction=0;direction<7;direction++) {
            double angle=switch(direction) {case 2 -> -Math.PI/2;case 3 -> Math.PI/2;case 4 -> -Math.PI/4;case 5 -> Math.PI/4;case 6 -> Math.PI;default -> 0;};
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            for (int i = 0; i < points.length; i++) {
                double x = points[i][0] * cos - points[i][1] * sin;
                double y = points[i][0] * sin + points[i][1] * cos;
                ARROWS[direction][i * 2] = x; ARROWS[direction][i * 2 + 1] = y;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
            ARROW_CENTERS[direction][0] = (minX + maxX) / 2;
            ARROW_CENTERS[direction][1] = (minY + maxY) / 2;
            ARROW_HEIGHTS[direction] = (float) (maxY - minY);
        }
    }

    public RoadSignGraphics(PoseStack pose, MultiBufferSource buffers, Font font, int light) {
        this(pose, buffers, font, light, false);
    }
    public RoadSignGraphics(PoseStack pose, MultiBufferSource buffers, Font font, int light, boolean world) {
        this(pose, buffers, font, light, world, new Layout());
    }
    public RoadSignGraphics(PoseStack pose, MultiBufferSource buffers, Font font, int light, boolean world, Layout layout) {
        this.pose = pose; this.buffers = buffers; this.font = font; this.light = light;
        this.world = world; this.layout = layout;
    }
    /** The editor icons reuse the sign arrows, with identical scale in every direction. */
    public static void drawArrowIcon(PoseStack pose, MultiBufferSource buffers, int direction, float x, float y, int color) {
        if (direction <= 0 || direction >= ARROWS.length) return;
        double[] points = ARROWS[direction];
        VertexConsumer consumer = buffers.getBuffer(RenderType.gui());
        for (int index : ICON_VERTICES) {
            float px = x + (float) points[index * 2] * 0.45F;
            float py = y + (float) points[index * 2 + 1] * 0.45F;
            consumer.addVertex(pose.last().pose(), px, py, 0).setColor(color);
        }
    }
    public static int width(Kind kind) { return kind == Kind.VARIABLE_SPEED ? 96 : 192; }
    public static int height(Kind kind) { return switch (kind) { case HIGHWAY -> 144; case DIRECTION -> 72; case VARIABLE_SPEED -> 96; }; }
    public static int backgroundColor(Kind kind, int theme) {
        return kind==Kind.VARIABLE_SPEED?0xFF070B10
            :theme==1?0xFF174C9B:theme==2?0xFF784A2D:theme==3?0xFFDEE2DC:0xFF176B48;
    }
    public static int panelColor(int panel) {
        return switch (panel) {
            case RoadSignDesign.WHITE_PANEL -> 0xFFE7EAE4;
            case RoadSignDesign.BLUE_PANEL -> 0xFF174C9B;
            case RoadSignDesign.BROWN_PANEL -> 0xFF784A2D;
            case RoadSignDesign.GREEN_PANEL -> 0xFF176B48;
            default -> 0;
        };
    }
    public void draw(Kind kind, String[] lines, int[] arrows, String[] distances, String route, int theme, int speed, boolean enabled, RoadSignDesign design) {
        draw(kind, lines, arrows, distances, route, theme, speed, enabled, design, 0);
    }
    public void draw(Kind kind, String[] lines, int[] arrows, String[] distances, String route, int theme, int speed, boolean enabled, RoadSignDesign design, int joins) {
        geometryBuffer = null;
        modernLettering = kind != Kind.VARIABLE_SPEED;
        int w = width(kind), h = height(kind);
        int color = backgroundColor(kind,theme);
        int ink = theme == 3 ? 0xFF17252E : 0xFFFFFFFF;
        if (design.ink() == 1) ink = 0xFFFFFFFF;
        else if (design.ink() == 2) ink = 0xFFFFDA64;
        else if (design.ink() == 3) ink = 0xFF17252E;
        depth = 0;
        int frame = kind == Kind.VARIABLE_SPEED ? 2 : design.frame();
        if (kind == Kind.VARIABLE_SPEED) {
            roundedBorder(0, 0, w, h, 4, 0.8, 0xFF53616B);
            roundedBorder(0.8, 0.8, w - 0.8, h - 0.8, 3.2, 2, 0xFFA8B4BC);
            roundedRect(2.8, 2.8, w - 2.8, h - 2.8, 1.2, color);
        } else {
            double[][][] layers = SIGN_FRAMES[kind == Kind.DIRECTION ? 0 : 1][frame][joins & 3];
            if (frame > 0) borderBetween(layers[0], layers[1], frame == 1 ? 0xFFFFFFFF : 0xFF53616B);
            if (frame == 2) borderBetween(layers[1], layers[2], 0xFFA8B4BC);
            fillPerimeter(layers[frame], color);
        }
        depth = 0.2F;
        if (kind == Kind.VARIABLE_SPEED) {
            // The concentric bands share edges, never overlapping at the same depth.
            for (int y=8;y<88;y+=4) for(int x=8;x<88;x+=4) rect(x,y,x+0.7,y+0.7,0xFF17202A);
            if (!enabled) return;
            depth=0.3F;
            ring(48,48,40,38.5F,0xFF8B1928);
            ring(48,48,38.5F,33.5F,0xFFFF253E);
            ring(48,48,33.5F,32.8F,0xFFFFA092);
            String number = Integer.toString(speed);
            float numberScale = Math.min(4.5F, 57F / Math.max(1, font.width(number)));
            text(number, 48 - font.width(number) * numberScale / 2,
                    48 - (font.lineHeight - 2) * numberScale / 2, 57, numberScale, 0xFFFFFFEE, false);
            return;
        }
        int count = design.visibleRows(kind == Kind.HIGHWAY);
        float rowHeight = (h - 12F) / count;
        boolean compactDouble = kind == Kind.DIRECTION && count == 2;
        float glyphHeight = font.lineHeight + 2F, paddingY = compactDouble ? 1.5F : 2, arrowGap = 2;
        float requestedScale = 1.8F * design.textSize() / 100;
        int arrowCount = 0;
        float arrowSpan = 0;
        for (int i = 0; i < count; i++) if (arrows[i] != 0) {
            arrowCount++;
            int direction = arrows[i] >= 0 && arrows[i] < ARROWS.length ? arrows[i] : 1;
            arrowSpan += compactDouble ? ARROW_HEIGHTS[direction] : 23;
        }
        // Reserve divider clearance in both modes, so toggling it never resizes the lettering.
        float rowGap = compactDouble ? 2 : count > 1 ? 3 : 0;
        boolean compactSingle = kind == Kind.DIRECTION && count == 1 && design.centeredArrows();
        float inset = compactSingle ? 9 : compactDouble ? 4 : 6;
        float availableHeight = h - inset * 2 - (count - 1) * rowGap;
        float arrowScale = compactSingle ? 21.6F / 23 : Math.min(1F, (rowHeight - 8) / 28) * 1.35F;
        if (compactDouble && arrowCount > 0) {
            // Fit text and actual arrow bounds together. Size changes must not hit a fixed text ceiling.
            float fixedSpacing = count * paddingY * 2 + arrowCount * arrowGap;
            float desiredHeight = count * glyphHeight * requestedScale + arrowSpan * arrowScale;
            arrowScale *= Math.min(1F, (availableHeight - fixedSpacing) / desiredHeight);
        } else if (arrowCount > 0 && !compactSingle) {
            // Use the same safe size in all three positions, including crowded small signs.
            float minimumTextHeight = glyphHeight * Math.min(requestedScale, 1F) + paddingY * 2;
            float maximumArrowHeight = (availableHeight - count * minimumTextHeight - arrowCount * arrowGap) / arrowCount;
            arrowScale = Math.min(arrowScale, maximumArrowHeight / 23);
        }
        float arrowHeight = arrowScale * 23;
        float textHeight = (availableHeight - (design.centeredArrows() ? arrowSpan * arrowScale + arrowCount * arrowGap : 0)) / count;
        float sharedScale = Math.min(requestedScale, (textHeight - paddingY * 2) / glyphHeight);
        float minimumPanelHeight = design.alignment() == 1 && count < 3
                ? Math.min(textHeight, Math.min(rowHeight - 4, Math.min(48, Math.max(32, rowHeight * 0.65F)))) : 0;
        float rowTop = inset;
        Row[] rows = layout.rows;
        for (int i = 0; i < count; i++) {
            Row row = rows[i];
            row.updateLabels(lines[i], distances[i], design.bold());
            row.panel = design.panelColor(i);
            row.group = i;
            row.centerArrow = design.centeredArrows() && arrows[i] != 0;
            int direction = arrows[i] >= 0 && arrows[i] < ARROWS.length ? arrows[i] : 1;
            float rowArrowHeight = compactDouble ? ARROW_HEIGHTS[direction] * arrowScale : arrowHeight;
            float cellHeight = textHeight + (row.centerArrow ? rowArrowHeight + arrowGap : 0);
            row.center = rowTop + cellHeight / 2;
            row.textCenter = rowTop + textHeight / 2;
            row.dividerY = rowTop + cellHeight + rowGap / 2;
            row.textHeight = textHeight;
            row.minimumPanelHeight = minimumPanelHeight;
            row.arrowScale = arrowScale;
            double[] offset = ARROW_CENTERS[direction];
            row.arrowX = design.centeredArrows() ? w / 2F - (float) offset[0] * arrowScale : design.leftArrows() ? 25 : 167;
            row.arrowY = row.center - (float) offset[1] * arrowScale;
            if (row.centerArrow && (!lines[i].isEmpty() || !distances[i].isEmpty()))
                row.arrowY = rowTop + textHeight + arrowGap + rowArrowHeight / 2 - (float) offset[1] * arrowScale;
            rowTop += cellHeight + rowGap;
            boolean sideArrow = arrows[i] != 0 && !design.centeredArrows();
            row.left = sideArrow && design.leftArrows() ? 48 : 12;
            row.right = sideArrow && !design.leftArrows() ? 144 : w - 12;
            row.distanceRight = row.right;
            int distanceLength = distances[i].isEmpty() ? 0 : font.width(row.distance);
            row.distanceScale = distanceLength == 0 ? 0 : Math.min(sharedScale, Math.min(64, (row.right - row.left) * 0.4F) / distanceLength);
            row.distanceWidth = distanceLength * row.distanceScale;
            if (!distances[i].isEmpty()) row.right -= row.distanceWidth + 8;
            row.captionWidth = lines[i].isEmpty() ? 0 : font.width(row.caption);
        }
        // Resolve shared columns first, then choose one lettering scale for the whole sign.
        for (int start = 0; start < count;) {
            Row first = rows[start];
            int end = start;
            if (design.alignment() == 1 && first.panel != RoadSignDesign.NO_PANEL && !lines[start].isEmpty()) {
                while (end + 1 < count && rows[end + 1].panel == first.panel && !lines[end + 1].isEmpty()
                        && !rows[end].centerArrow) end++;
            }
            float left = first.left, right = first.right;
            for (int i = start + 1; i <= end; i++) {
                left = Math.max(left, rows[i].left);
                right = Math.min(right, rows[i].right);
            }
            for (int i = start; i <= end; i++) {
                Row row = rows[i];
                row.group = start; row.left = left; row.right = right;
                if (row.captionWidth > 0) sharedScale = Math.min(sharedScale, (right - left) / row.captionWidth);
            }
            start = end + 1;
        }
        for (int i = 0; i < count; i++) {
            Row row = rows[i];
            row.scale = sharedScale;
            float width = row.captionWidth * sharedScale;
            row.x = design.alignment() == 1 ? (row.left + row.right - width) / 2
                    : design.alignment() == 2 ? row.right - width : row.left;
            float panelHeight = Math.max(glyphHeight * sharedScale + paddingY * 2, minimumPanelHeight);
            row.panelTop = row.textCenter - panelHeight / 2;
            row.panelBottom = row.textCenter + panelHeight / 2;
            row.panelLeft = design.alignment() == 1 ? row.left - 4 : row.x - 4;
            row.panelRight = design.alignment() == 1 ? row.right + 4 : row.x + width + 4;
        }
        for (int start = 0; start < count;) {
            Row first = rows[start];
            int end = start;
            while (end + 1 < count && rows[end + 1].group == start) end++;
            if (first.panel != RoadSignDesign.NO_PANEL && !lines[start].isEmpty()) {
                depth = 0.25F;
                int border = first.panel == RoadSignDesign.WHITE_PANEL ? 0xFF17252E : 0xFFFFFFFF;
                roundedBorder(first.panelLeft, first.panelTop, first.panelRight, rows[end].panelBottom, 0.8, 0.6, border);
                roundedRect(first.panelLeft + 0.6, first.panelTop + 0.6, first.panelRight - 0.6,
                        rows[end].panelBottom - 0.6, 0.2, panelColor(first.panel));
            }
            start = end + 1;
        }
        for (int i = 0; i < count; i++) {
            Row row = rows[i];
            boolean panel = row.panel != RoadSignDesign.NO_PANEL && !lines[i].isEmpty();
            depth = panel ? 0.3F : 0.2F;
            if (!lines[i].isEmpty()) drawText(row.caption, row.x, row.textCenter - 5 * sharedScale, sharedScale,
                    row.panel == RoadSignDesign.WHITE_PANEL ? 0xFF17252E : panel ? 0xFFFFFFFF : ink);
            depth = 0.2F;
            if (!distances[i].isEmpty()) drawText(row.distance, row.distanceRight - row.distanceWidth,
                    row.textCenter - 5 * row.distanceScale, row.distanceScale, ink);
            arrow(arrows[i], row.arrowX, row.arrowY, ink, row.arrowScale);
            if (design.dividers() && i < count - 1) {
                boolean insidePanel = panel && row.group == rows[i + 1].group;
                depth = insidePanel ? 0.3F : 0.25F;
                float divider = row.dividerY;
                rect(insidePanel ? row.panelLeft + 4 : 12, divider - 0.35F,
                        insidePanel ? row.panelRight - 4 : w - 12, divider + 0.35F,
                        insidePanel && row.panel == RoadSignDesign.WHITE_PANEL ? 0xFF17252E : ink);
                depth = 0.2F;
            }
        }
    }

    private void roundedRect(double x1, double y1, double x2, double y2, double radius, int color) {
        if (radius <= 0) { rect(x1, y1, x2, y2, color); return; }
        fillPerimeter(roundedPerimeter(x1, y1, x2, y2, radius), color);
    }
    private void fillPerimeter(double[][] perimeter, int color) {
        double cx=(perimeter[0][0]+perimeter[10][0])/2,cy=(perimeter[0][1]+perimeter[10][1])/2;
        for (int i=0;i<perimeter.length;i++) {
            double[] a=perimeter[i],b=perimeter[(i+1)%perimeter.length];
            quad(cx,cy,a[0],a[1],b[0],b[1],b[0],b[1],color);
        }
    }
    private void roundedBorder(double x1, double y1, double x2, double y2, double radius, double inset, int color) {
        double[][] outer = roundedPerimeter(x1, y1, x2, y2, radius);
        double[][] inner = roundedPerimeter(x1 + inset, y1 + inset, x2 - inset, y2 - inset,
                Math.max(0, radius - inset));
        borderBetween(outer, inner, color);
    }
    private void borderBetween(double[][] outer, double[][] inner, int color) {
        // Border and fill share edges instead of covering the same surface twice.
        for (int i=0;i<outer.length;i++) {
            int next=(i+1)%outer.length;
            quad(outer[i][0],outer[i][1],outer[next][0],outer[next][1],
                    inner[next][0],inner[next][1],inner[i][0],inner[i][1],color);
        }
    }
    private static double[][] roundedPerimeter(double x1, double y1, double x2, double y2, double radius) {
        return roundedPerimeter(x1, y1, x2, y2, radius, radius);
    }
    private static double[][] roundedPerimeter(double x1, double y1, double x2, double y2, double leftRadius, double rightRadius) {
        double[][] perimeter = new double[20][2];
        for (int corner=0;corner<4;corner++) {
            double radius = corner < 2 ? rightRadius : leftRadius;
            double cx=corner<2?x2-radius:x1+radius,cy=corner==0||corner==3?y1+radius:y2-radius;
            for (int step=0;step<=4;step++) {
                int i=corner*5+step;
                perimeter[i][0]=cx+radius*CORNER_COS[i];
                perimeter[i][1]=cy+radius*CORNER_SIN[i];
            }
        }
        return perimeter;
    }
    private void ring(float x,float y,float outside,float inside,int color) {
        for(int i=0;i<96;i++) {
            quad(x+outside*CIRCLE_COS[i],y+outside*CIRCLE_SIN[i],x+outside*CIRCLE_COS[i+1],y+outside*CIRCLE_SIN[i+1],
                x+inside*CIRCLE_COS[i+1],y+inside*CIRCLE_SIN[i+1],x+inside*CIRCLE_COS[i],y+inside*CIRCLE_SIN[i],color);
        }
    }
    private Component label(String value, boolean bold) {
        return modernLettering
            ? Component.literal(value).setStyle(bold ? ROAD_BOLD_STYLE : ROAD_STYLE)
            : bold ? Component.literal(value).withStyle(ChatFormatting.BOLD) : Component.literal(value);
    }
    private void text(String value, float x, float y, float available, float requestedScale, int color, boolean bold) {
        if (value.isEmpty()) return;
        Component label = label(value, bold);
        float scale = Math.min(requestedScale, available / Math.max(1, font.width(label)));
        drawText(label, x, y, scale, color);
    }
    private void drawText(Component label, float x, float y, float scale, int color) {
        pose.pushPose(); pose.translate(x, y, depth + 0.1F); pose.scale(scale, scale, 1);
        MultiBufferSource lettering = world ? type -> buffers.getBuffer(RoadSignRenderTypes.lettering(type)) : buffers;
        Font.DisplayMode mode = world ? Font.DisplayMode.POLYGON_OFFSET : Font.DisplayMode.NORMAL;
        // The road bold font has its own outlines: one glyph pass, without shifted copies.
        font.drawInBatch(label, 0, 0, color, false, pose.last().pose(), lettering, mode, 0, light);
        pose.popPose();
        geometryBuffer = null; // Lettering can flush the previous geometry batch.
    }
    private void arrow(int direction, float x, float y, int color) {
        arrow(direction,x,y,color,1);
    }
    private void arrow(int direction, float x, float y, int color,float scale) {
        if (direction == 0) return;
        double[] p=ARROWS[direction>=0 && direction<ARROWS.length?direction:1];
        quad(x+scale*p[0],y+scale*p[1],x+scale*p[2],y+scale*p[3],x+scale*p[4],y+scale*p[5],x+scale*p[12],y+scale*p[13],color);
        quad(x+scale*p[10],y+scale*p[11],x+scale*p[6],y+scale*p[7],x+scale*p[8],y+scale*p[9],x+scale*p[8],y+scale*p[9],color);
    }
    private void rect(double x1, double y1, double x2, double y2, int color) {
        quad(x1,y1,x2,y1,x2,y2,x1,y2,color);
    }
    private void quad(double x1,double y1,double x2,double y2,double x3,double y3,double x4,double y4,int color) {
        // Joined borders collapse to lines. Do not submit them: projection rounding can flash pixels.
        double area=x1*y2-x2*y1+x2*y3-x3*y2+x3*y4-x4*y3+x4*y1-x1*y4;
        if (Math.abs(area) < 1.0e-8) return;
        if (geometryBuffer == null || geometryDepth != depth) {
            geometryBuffer = buffers.getBuffer(world ? RoadSignRenderTypes.atDepth(depth) : RenderType.text(WHITE));
            geometryDepth = depth;
        }
        VertexConsumer consumer = geometryBuffer;
        vertex(consumer,x1,y1,color,0,0);
        if(area>0) {
            vertex(consumer,x4,y4,color,0,1); vertex(consumer,x3,y3,color,1,1); vertex(consumer,x2,y2,color,1,0);
        } else {
            vertex(consumer,x2,y2,color,1,0); vertex(consumer,x3,y3,color,1,1); vertex(consumer,x4,y4,color,0,1);
        }
    }
    private void vertex(VertexConsumer consumer,double x,double y,int color,float u,float v) {
        consumer.addVertex(pose.last().pose(),(float)x,(float)y,depth)
            .setColor((color>>16)&255,(color>>8)&255,color&255,255).setUv(u,v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),0,0,1);
    }
}
