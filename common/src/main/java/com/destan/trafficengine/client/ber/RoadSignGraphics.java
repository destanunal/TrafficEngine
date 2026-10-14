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
    private static final ResourceLocation WHITE = new ResourceLocation("trafficengine", "textures/block/road_sign_white.png");
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
    static {
        for (int i=0;i<=96;i++) { double angle=i*Math.PI/48;CIRCLE_COS[i]=Math.cos(angle);CIRCLE_SIN[i]=Math.sin(angle); }
        for (int c=0;c<4;c++) for(int s=0;s<=4;s++) { double angle=Math.toRadians(-90+c*90+s*22.5);CORNER_COS[c*5+s]=Math.cos(angle);CORNER_SIN[c*5+s]=Math.sin(angle); }
        double[][] points={{-2,12},{2,12},{2,-2},{7,-2},{0,-11},{-7,-2},{-2,-2}};
        for(int direction=0;direction<7;direction++) {
            double angle=switch(direction) {case 2 -> -Math.PI/2;case 3 -> Math.PI/2;case 4 -> -Math.PI/4;case 5 -> Math.PI/4;case 6 -> Math.PI;default -> 0;};
            for(int i=0;i<points.length;i++) {ARROWS[direction][i*2]=points[i][0]*Math.cos(angle)-points[i][1]*Math.sin(angle);ARROWS[direction][i*2+1]=points[i][0]*Math.sin(angle)+points[i][1]*Math.cos(angle);}
        }
    }

    public RoadSignGraphics(PoseStack pose, MultiBufferSource buffers, Font font, int light) {
        this(pose, buffers, font, light, false);
    }
    public RoadSignGraphics(PoseStack pose, MultiBufferSource buffers, Font font, int light, boolean world) {
        this.pose = pose; this.buffers = buffers; this.font = font; this.light = light;
        this.world = world;
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
    public void draw(Kind kind, String[] lines, int[] arrows, String route, int theme, int speed, boolean enabled, RoadSignDesign design) {
        geometryBuffer = null;
        int w = width(kind), h = height(kind);
        int color = backgroundColor(kind,theme);
        int ink = theme == 3 ? 0xFF17252E : 0xFFFFFFFF;
        if (design.ink() == 1) ink = 0xFFFFFFFF;
        else if (design.ink() == 2) ink = 0xFFFFDA64;
        else if (design.ink() == 3) ink = 0xFF17252E;
        depth = 0;
        int frame = kind == Kind.VARIABLE_SPEED ? 2 : design.frame();
        // White is a fine painted border; metal has a wider silver rim and dark outer edge.
        double inset = frame == 0 ? 0 : frame == 1 ? 1.3 : 2.8;
        if (frame == 1) roundedBorder(0, 0, w, h, 4, inset, 0xFFFFFFFF);
        else if (frame == 2) {
            roundedBorder(0, 0, w, h, 4, 0.8, 0xFF53616B);
            roundedBorder(0.8, 0.8, w - 0.8, h - 0.8, 3.2, 2, 0xFFA8B4BC);
        }
        roundedRect(inset, inset, w - inset, h - inset, Math.max(0, 4 - inset), color);
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
        float first = 12;
        float rowHeight = (h - first - 10) / count;
        for (int i = 0; i < count; i++) {
            float center = first + (i + 0.5F) * rowHeight;
            boolean hasArrow = arrows[i] != 0;
            float left = hasArrow && design.leftArrows() ? 43 : 12;
            float right = hasArrow && !design.leftArrows() ? 149 : w - 12;
            int panel = design.panelColor(i);
            Component label = label(lines[i], design.bold());
            float scale = Math.min(1.8F * design.textSize() / 100,
                    Math.min((right-left)/Math.max(1,font.width(label)), rowHeight
                            * (panel > RoadSignDesign.WHITE_PANEL ? 0.60F : 0.64F) / (font.lineHeight-2)));
            float textWidth = font.width(label) * scale;
            float x = design.alignment() == 1 ? (left + right - textWidth) / 2
                    : design.alignment() == 2 ? right-textWidth : left;
            if (panel != RoadSignDesign.NO_PANEL && !lines[i].isEmpty()) {
                depth = 0.25F;
                float panelLeft = x - 4, panelRight = x + textWidth + 4;
                float panelTop = center - rowHeight * 0.34F, panelBottom = center + rowHeight * 0.34F;
                if (panel == RoadSignDesign.WHITE_PANEL) {
                    roundedRect(panelLeft, panelTop, panelRight, panelBottom, 2.5, panelColor(panel));
                } else {
                    roundedBorder(panelLeft, panelTop, panelRight, panelBottom, 2.5, 1, 0xFFFFFFFF);
                    roundedRect(panelLeft + 1, panelTop + 1, panelRight - 1, panelBottom - 1, 1.5, panelColor(panel));
                }
                depth = 0.3F;
            }
            text(lines[i], x, center-(font.lineHeight-2)*scale/2, right-left, scale,
                    panel == RoadSignDesign.WHITE_PANEL ? 0xFF17252E : panel != RoadSignDesign.NO_PANEL ? 0xFFFFFFFF : ink,
                    design.bold());
            depth = 0.2F;
            arrow(arrows[i], design.leftArrows() ? 25 : 167, center, ink);
            if (design.dividers() && i < count - 1) {
                depth = 0.25F;
                rect(12, first+(i+1)*rowHeight-0.35F, w-12, first+(i+1)*rowHeight+0.35F, ink);
                depth = 0.2F;
            }
        }
    }
    private void roundedRect(double x1, double y1, double x2, double y2, double radius, int color) {
        if (radius <= 0) { rect(x1, y1, x2, y2, color); return; }
        double[][] perimeter = roundedPerimeter(x1, y1, x2, y2, radius);
        double cx=(x1+x2)/2,cy=(y1+y2)/2;
        for (int i=0;i<perimeter.length;i++) {
            double[] a=perimeter[i],b=perimeter[(i+1)%perimeter.length];
            quad(cx,cy,a[0],a[1],b[0],b[1],b[0],b[1],color);
        }
    }
    private void roundedBorder(double x1, double y1, double x2, double y2, double radius, double inset, int color) {
        double[][] outer = roundedPerimeter(x1, y1, x2, y2, radius);
        double[][] inner = roundedPerimeter(x1 + inset, y1 + inset, x2 - inset, y2 - inset,
                Math.max(0, radius - inset));
        // Border and fill share edges instead of covering the same surface twice.
        for (int i=0;i<outer.length;i++) {
            int next=(i+1)%outer.length;
            quad(outer[i][0],outer[i][1],outer[next][0],outer[next][1],
                    inner[next][0],inner[next][1],inner[i][0],inner[i][1],color);
        }
    }
    private static double[][] roundedPerimeter(double x1, double y1, double x2, double y2, double radius) {
        double[][] perimeter = new double[20][2];
        for (int corner=0;corner<4;corner++) {
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
        return bold ? Component.literal(value).withStyle(ChatFormatting.BOLD) : Component.literal(value);
    }
    private void text(String value, float x, float y, float available, float requestedScale, int color, boolean bold) {
        if (value.isEmpty()) return;
        Component label = label(value, bold);
        float scale = Math.min(requestedScale, available / Math.max(1, font.width(label)));
        pose.pushPose(); pose.translate(x, y, depth + 0.1F); pose.scale(scale, scale, 1);
        MultiBufferSource lettering = world ? type -> buffers.getBuffer(RoadSignRenderTypes.lettering(type)) : buffers;
        Font.DisplayMode mode = world ? Font.DisplayMode.POLYGON_OFFSET : Font.DisplayMode.NORMAL;
        if (bold) drawBold(label, color, lettering, mode);
        else font.drawInBatch(label, 0, 0, color, false, pose.last().pose(), lettering, mode, 0, light);
        pose.popPose();
        geometryBuffer = null; // Lettering can flush the previous geometry batch.
    }
    private void drawBold(Component label, int color, MultiBufferSource lettering, Font.DisplayMode mode) {
        float[] cursor = {0};
        label.getVisualOrderText().accept((index, style, codePoint) -> {
            var glyph = net.minecraft.util.FormattedCharSequence.codepoint(codePoint, style.withBold(false));
            float advance = font.getSplitter().stringWidth(
                    net.minecraft.util.FormattedCharSequence.codepoint(codePoint, style));
            float weight = advance - font.getSplitter().stringWidth(glyph);
            font.drawInBatch(glyph, cursor[0], 0, color, false, pose.last().pose(), lettering, mode, 0, light);
            if (weight > 0) {
                // Bitmap glyphs inset their UV edges. A small overlap closes the seam
                // between the normal and bold copy, while retaining native advances.
                font.drawInBatch(glyph, cursor[0] + weight * 0.95F, 0, color, false,
                        pose.last().pose(), lettering, mode, 0, light);
            }
            cursor[0] += advance;
            return true;
        });
    }
    private void arrow(int direction, float x, float y, int color) {
        arrow(direction,x,y,color,direction==4 || direction==5 ? 1.18F : 1);
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
        if (geometryBuffer == null || geometryDepth != depth) {
            geometryBuffer = buffers.getBuffer(world ? RoadSignRenderTypes.atDepth(depth) : RenderType.text(WHITE));
            geometryDepth = depth;
        }
        VertexConsumer consumer = geometryBuffer;
        // Use the same winding as BakedGlyph. The world renderer flips the Y axis.
        double area=x1*y2-x2*y1+x2*y3-x3*y2+x3*y4-x4*y3+x4*y1-x1*y4;
        vertex(consumer,x1,y1,color,0,0);
        if(area>0) {
            vertex(consumer,x4,y4,color,0,1); vertex(consumer,x3,y3,color,1,1); vertex(consumer,x2,y2,color,1,0);
        } else {
            vertex(consumer,x2,y2,color,1,0); vertex(consumer,x3,y3,color,1,1); vertex(consumer,x4,y4,color,0,1);
        }
    }
    private void vertex(VertexConsumer consumer,double x,double y,int color,float u,float v) {
        consumer.vertex(pose.last().pose(),(float)x,(float)y,depth)
            .color((color>>16)&255,(color>>8)&255,color&255,255).uv(u,v)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.last().normal(),0,0,1).endVertex();
    }
}
