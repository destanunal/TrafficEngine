package com.destan.trafficengine.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Clips one straight stroke to road cells; diagonal lines never use repeated corner pieces. */
public final class RoadStrokeGeometry {
    record Point(double x,double z) {}
    private RoadStrokeGeometry() {}
    public static double lineWidth(int style) { return style==0?0.125:0.25; }
    /** Pattern 23 places its four-pixel stripe at pixels 9 through 12 of a 16-pixel block. */
    public static double positionOffset(int position) {
        return position==1?-3.0/16:position==2?3.0/16:0;
    }
    public static Map<RoadPaintPlan.Cell,float[]> between(RoadPaintPlan.Cell start,RoadPaintPlan.Cell end,int width,int style) {
        return between(start,end,width,style,2);
    }
    public static Map<RoadPaintPlan.Cell,float[]> between(RoadPaintPlan.Cell start,RoadPaintPlan.Cell end,int width,int style,int dashGap) {
        return between(start,end,width,style,dashGap,0);
    }
    /** Position: 0 centered, 1 left, 2 right, relative to the first-to-last click direction. */
    public static Map<RoadPaintPlan.Cell,float[]> between(RoadPaintPlan.Cell start,RoadPaintPlan.Cell end,int width,int style,int dashGap,int position) {
        long dx=(long)end.x()-start.x(),dz=(long)end.z()-start.z();
        if(start.y()!=end.y() || Math.max(Math.abs(dx),Math.abs(dz))>RoadPaintPlan.MAX_DISTANCE
                || width<1 || width>RoadPaintPlan.MAX_WIDTH || width%2==0 || style<0 || style>3 || dx==0 && dz==0)return Map.of();
        // Keep dash phase stable while left/right follows the direction in which the player paints.
        boolean reversed=start.x()>end.x() || start.x()==end.x() && start.z()>end.z();
        if(reversed) {
            var swap=start;start=end;end=swap;dx=-dx;dz=-dz;
        }
        double length=Math.hypot(dx,dz),tx=dx/length,tz=dz/length,nx=-tz,nz=tx;
        double x=start.x()+0.5,z=start.z()+0.5;
        Map<RoadPaintPlan.Cell,List<Float>> pieces=new LinkedHashMap<>();
        double thickness=lineWidth(style);
        double[] bands=style==2?new double[]{-0.1875,0.1875}:new double[]{0};
        double side=positionOffset(position);
        if(reversed)side=-side;
        for(int lane=-width/2;lane<=width/2;lane++) for(double band:bands) {
            if(style==1) {
                // Every dash is two blocks along the actual road angle. Do not shorten the final dash.
                int gap=dashGap==4?4:2;
                for(double distance=-0.5;distance+2<=length+0.5+1e-8;distance+=2+gap)
                    if(!strip(pieces,start.y(),x,z,tx,tz,nx,nz,distance,distance+2,lane*RoadPaintPlan.LINE_SPACING+band+side,thickness))return Map.of();
            } else if(!strip(pieces,start.y(),x,z,tx,tz,nx,nz,-0.5,length+0.5,lane*RoadPaintPlan.LINE_SPACING+band+side,thickness))return Map.of();
        }
        return pack(pieces);
    }
    public static float[] single(RoadPaintPlan.Cell cell, int dx, int dz, int style, int position) {
        if (Math.abs(dx) + Math.abs(dz) != 1 || style < 0 || style > 3) return new float[0];
        Map<RoadPaintPlan.Cell,List<Float>> pieces = new LinkedHashMap<>();
        double[] bands = style == 2 ? new double[]{-0.1875, 0.1875} : new double[]{0};
        for (double band : bands)
            strip(pieces, cell.y(), cell.x()+0.5, cell.z()+0.5, dx, dz, -dz, dx,
                    -0.5, 0.5, band+positionOffset(position), lineWidth(style));
        return pack(pieces).getOrDefault(cell, new float[0]);
    }

    private static boolean strip(Map<RoadPaintPlan.Cell,List<Float>> output,int y,double x,double z,
            double tx,double tz,double nx,double nz,double from,double to,double offset,double width) {
        List<Point> polygon=List.of(new Point(x+tx*from+nx*(offset+width/2),z+tz*from+nz*(offset+width/2)),
            new Point(x+tx*to+nx*(offset+width/2),z+tz*to+nz*(offset+width/2)),
            new Point(x+tx*to+nx*(offset-width/2),z+tz*to+nz*(offset-width/2)),
            new Point(x+tx*from+nx*(offset-width/2),z+tz*from+nz*(offset-width/2)));
        return appendPolygon(output,y,polygon,false);
    }
    static Map<RoadPaintPlan.Cell,float[]> pack(Map<RoadPaintPlan.Cell,List<Float>> pieces) {
        Map<RoadPaintPlan.Cell,float[]> result=new LinkedHashMap<>();
        pieces.forEach((cell,list)->{float[] vertices=new float[list.size()];for(int i=0;i<vertices.length;i++)vertices[i]=list.get(i);result.put(cell,vertices);});
        return Collections.unmodifiableMap(result);
    }
    static boolean appendPolygon(Map<RoadPaintPlan.Cell,List<Float>> output,int y,List<Point> polygon,boolean compact) {
        return appendPolygon(output,y,polygon,compact,384);
    }
    static boolean appendPolygon(Map<RoadPaintPlan.Cell,List<Float>> output,int y,List<Point> polygon,boolean compact,int vertexLimit) {
        if(polygon.size()<3 || Math.abs(area(polygon))<1e-9)return true;
        int minX=(int)Math.floor(polygon.stream().mapToDouble(Point::x).min().orElseThrow());
        int maxX=(int)Math.floor(polygon.stream().mapToDouble(Point::x).max().orElseThrow());
        int minZ=(int)Math.floor(polygon.stream().mapToDouble(Point::z).min().orElseThrow());
        int maxZ=(int)Math.floor(polygon.stream().mapToDouble(Point::z).max().orElseThrow());
        for(int cx=minX;cx<=maxX;cx++)for(int cz=minZ;cz<=maxZ;cz++) {
            List<Point> clipped=clip(clip(clip(clip(polygon,0,cx,true),0,cx+1,false),1,cz,true),1,cz+1,false);
            if(clipped.size()<3 || Math.abs(area(clipped))<1e-9)continue;
            if(area(clipped)>0)Collections.reverse(clipped);
            List<Float> vertices=output.computeIfAbsent(new RoadPaintPlan.Cell(cx,y,cz),unused->new ArrayList<>());
            for(int i=1;i<clipped.size()-1;i+=compact?2:1) {
                point(vertices,clipped.get(0),cx,cz);point(vertices,clipped.get(i),cx,cz);
                point(vertices,clipped.get(i+1),cx,cz);
                point(vertices,clipped.get(compact?Math.min(i+2,clipped.size()-1):i+1),cx,cz);
            }
            if(output.size()>RoadPaintPlan.MAX_CELLS || vertices.size()>vertexLimit)return false;
        }
        return true;
    }
    private static void point(List<Float> output,Point p,int x,int z) {
        output.add((float)Math.max(0,Math.min(1,p.x-x)));output.add(1.003125F);output.add((float)Math.max(0,Math.min(1,p.z-z)));
    }
    static double area(List<Point> polygon) {
        if(polygon.size()<3)return 0;
        // Translate before calculating area to avoid cancellation at distant world coordinates.
        double sum=0;Point origin=polygon.get(0);
        for(int i=1;i<polygon.size()-1;i++) {
            Point a=polygon.get(i),b=polygon.get(i+1);
            sum+=(a.x-origin.x)*(b.z-origin.z)-(a.z-origin.z)*(b.x-origin.x);
        }
        return sum;
    }
    private static List<Point> clip(List<Point> polygon,int axis,double boundary,boolean greater) {
        if(polygon.isEmpty())return new ArrayList<>();
        List<Point> result=new ArrayList<>();Point a=polygon.get(polygon.size()-1);
        double av=axis==0?a.x:a.z;boolean insideA=greater?av>=boundary:av<=boundary;
        for(Point b:polygon) {
            double bv=axis==0?b.x:b.z;boolean insideB=greater?bv>=boundary:bv<=boundary;
            if(insideA!=insideB) {
                double t=(boundary-av)/(bv-av);
                result.add(new Point(a.x+(b.x-a.x)*t,a.z+(b.z-a.z)*t));
            }
            if(insideB)result.add(b);
            a=b;av=bv;insideA=insideB;
        }
        return result;
    }
}
