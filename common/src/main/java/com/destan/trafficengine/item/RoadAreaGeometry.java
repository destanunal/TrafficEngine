package com.destan.trafficengine.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.destan.trafficengine.item.RoadStrokeGeometry.Point;

/** Bounded, convex road areas clipped to the same persistent meshes as brush strokes. */
public final class RoadAreaGeometry {
    public static final int RECTANGLE=0, TRIANGLE=1, QUADRILATERAL=2;
    public static final int CROSSHATCH=1, HATCH=2, FILL=3;
    private static final double THICKNESS=0.25, SPACING=2;
    public record Result(Map<RoadPaintPlan.Cell,float[]> geometry,List<RoadPaintPlan.Cell> coverage) {
        public boolean isEmpty() { return coverage.isEmpty(); }
    }
    private static final Result EMPTY=new Result(Map.of(),List.of());
    private RoadAreaGeometry() {}

    public static int cornerCount(int shape) { return shape==TRIANGLE?3:shape==QUADRILATERAL?4:2; }

    public static Result between(List<RoadPaintPlan.Cell> corners,int shape,int style) {
        if(shape<0 || shape>2 || style<1 || style>3 || corners.size()!=cornerCount(shape))return EMPTY;
        var first=corners.get(0);
        long minX=first.x(),maxX=minX,minZ=first.z(),maxZ=minZ;
        for(var cell:corners) {
            if(cell.y()!=first.y())return EMPTY;
            minX=Math.min(minX,cell.x());maxX=Math.max(maxX,cell.x());
            minZ=Math.min(minZ,cell.z());maxZ=Math.max(maxZ,cell.z());
        }
        if(maxX-minX>RoadPaintPlan.MAX_DISTANCE || maxZ-minZ>RoadPaintPlan.MAX_DISTANCE
                || corners.stream().distinct().count()!=corners.size())return EMPTY;
        List<Point> polygon=shape==RECTANGLE?List.of(new Point(minX,minZ),new Point(maxX+1,minZ),
                new Point(maxX+1,maxZ+1),new Point(minX,maxZ+1))
                :hull(corners.stream().map(cell->new Point(cell.x()+0.5,cell.z()+0.5)).toList());
        if(polygon.size()!=(shape==RECTANGLE?4:cornerCount(shape)) || Math.abs(RoadStrokeGeometry.area(polygon))<1e-8)return EMPTY;
        Map<RoadPaintPlan.Cell,List<Float>> footprint=new LinkedHashMap<>();
        if(!RoadStrokeGeometry.appendPolygon(footprint,first.y(),polygon,true))return EMPTY;
        Map<RoadPaintPlan.Cell,List<Float>> pieces=new LinkedHashMap<>();
        if(style==FILL) {
            // Full paint colours whole road blocks, like catalogue pattern #0.
            Map<RoadPaintPlan.Cell,float[]> blocks=new LinkedHashMap<>();
            for(var cell:footprint.keySet()) blocks.put(cell,
                new float[]{0,1.003125F,0,0,1.003125F,1,1,1.003125F,1,1,1.003125F,0});
            return new Result(blocks,List.copyOf(footprint.keySet()));
        }
        // Outline bands are clipped inward, keeping all painting within the chosen area.
        for(int i=0;i<polygon.size();i++) {
            Point a=polygon.get(i),b=polygon.get((i+1)%polygon.size());
            double dx=b.x()-a.x(),dz=b.z()-a.z(),length=Math.hypot(dx,dz);
            double nx=-dz/length*THICKNESS,nz=dx/length*THICKNESS;
            List<Point> band=clipToArea(List.of(a,b,new Point(b.x()+nx,b.z()+nz),new Point(a.x()+nx,a.z()+nz)),polygon);
            if(!RoadStrokeGeometry.appendPolygon(pieces,first.y(),band,true))return EMPTY;
        }
        // Follow the longest edge for rotated rectangles and triangular separation islands.
        Point origin=polygon.get(0);double angle=0,longest=-1;
        for(int i=0;i<polygon.size();i++) {
            Point a=polygon.get(i),b=polygon.get((i+1)%polygon.size());
            double dx=b.x()-a.x(),dz=b.z()-a.z(),length=dx*dx+dz*dz;
            if(length>longest+1e-8) {
                if(dx<0 || dx==0 && dz<0) {dx=-dx;dz=-dz;}
                longest=length;angle=Math.atan2(dz,dx);
            }
        }
        if(!hatch(pieces,first.y(),polygon,origin,angle+Math.PI/4))return EMPTY;
        if(style==CROSSHATCH && !hatch(pieces,first.y(),polygon,origin,angle-Math.PI/4))return EMPTY;
        return new Result(RoadStrokeGeometry.pack(pieces),List.copyOf(footprint.keySet()));
    }

    private static boolean hatch(Map<RoadPaintPlan.Cell,List<Float>> output,int y,List<Point> polygon,Point origin,double angle) {
        double tx=Math.cos(angle),tz=Math.sin(angle),nx=-tz,nz=tx;
        double minT=Double.POSITIVE_INFINITY,maxT=Double.NEGATIVE_INFINITY,minS=minT,maxS=maxT;
        for(Point p:polygon) {
            double x=p.x()-origin.x(),z=p.z()-origin.z(),t=x*tx+z*tz,s=x*nx+z*nz;
            minT=Math.min(minT,t);maxT=Math.max(maxT,t);minS=Math.min(minS,s);maxS=Math.max(maxS,s);
        }
        for(int i=(int)Math.floor(minS/SPACING);i<=(int)Math.ceil(maxS/SPACING);i++) {
            double s=i*SPACING,from=minT-1,to=maxT+1;
            List<Point> stripe=List.of(project(origin,tx,tz,nx,nz,from,s+THICKNESS/2),
                    project(origin,tx,tz,nx,nz,to,s+THICKNESS/2),
                    project(origin,tx,tz,nx,nz,to,s-THICKNESS/2),
                    project(origin,tx,tz,nx,nz,from,s-THICKNESS/2));
            if(!RoadStrokeGeometry.appendPolygon(output,y,clipToArea(stripe,polygon),true))return false;
        }
        return true;
    }

    private static Point project(Point origin,double tx,double tz,double nx,double nz,double t,double s) {
        return new Point(origin.x()+tx*t+nx*s,origin.z()+tz*t+nz*s);
    }
    private static double cross(Point a,Point b,Point p) {
        return (b.x()-a.x())*(p.z()-a.z())-(b.z()-a.z())*(p.x()-a.x());
    }
    private static List<Point> hull(List<Point> points) {
        List<Point> sorted=points.stream().sorted(Comparator.comparingDouble(Point::x).thenComparingDouble(Point::z)).toList();
        List<Point> result=new ArrayList<>();
        for(Point point:sorted) {
            while(result.size()>1 && cross(result.get(result.size()-2),result.get(result.size()-1),point)<=0)result.remove(result.size()-1);
            result.add(point);
        }
        int lower=result.size();
        for(int i=sorted.size()-2;i>=0;i--) {
            Point point=sorted.get(i);
            while(result.size()>lower && cross(result.get(result.size()-2),result.get(result.size()-1),point)<=0)result.remove(result.size()-1);
            result.add(point);
        }
        result.remove(result.size()-1);
        return result;
    }
    private static List<Point> clipToArea(List<Point> source,List<Point> area) {
        List<Point> polygon=source;
        for(int edge=0;edge<area.size() && !polygon.isEmpty();edge++) {
            Point a=area.get(edge),b=area.get((edge+1)%area.size());
            List<Point> clipped=new ArrayList<>();Point previous=polygon.get(polygon.size()-1);
            double before=cross(a,b,previous);
            for(Point current:polygon) {
                double after=cross(a,b,current);
                if((before>=0)!=(after>=0)) {
                    double t=before/(before-after);
                    clipped.add(new Point(previous.x()+(current.x()-previous.x())*t,previous.z()+(current.z()-previous.z())*t));
                }
                if(after>=0)clipped.add(current);
                previous=current;before=after;
            }
            polygon=clipped;
        }
        return polygon;
    }
}
