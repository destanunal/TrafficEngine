package com.destan.trafficengine.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.destan.trafficengine.item.RoadPaintPlan.Cell;
import com.destan.trafficengine.item.RoadStrokeGeometry.Point;

/** Bounded paths are tessellated once, then rendered with the existing static road mesh. */
public final class RoadCurveGeometry {
    public static final int STRAIGHT = 0, CURVE = 1, ROUNDABOUT = 2;
    private static final int MAX_SEGMENTS = 4096;
    private static final double ROUND_TANGENT = 4*(Math.sqrt(2)-1);
    private record Path(List<Point> points, int y, boolean closed, boolean aligned) {}
    private record Joint(double nx, double nz) {}
    private record Lane(Point[] left, Point[] right, double[] distance) {}
    private RoadCurveGeometry() {}

    /** One sampled/aligned path shared by the painted dashes and their solid undo/clearing coverage. */
    public static final class Prepared {
        private final Path path;
        private Prepared(Path path) {this.path=path;}
    }

    public static Prepared prepare(List<Cell> clicks,int shape,RoadEdgeAlignment.Surface road) {
        Path selected=path(clicks,shape);
        if(selected==null)return new Prepared(null);
        List<Point> aligned=RoadEdgeAlignment.align(selected.points,selected.closed,clicks,road);
        return new Prepared(new Path(aligned,selected.y,selected.closed,aligned!=selected.points));
    }

    public static Map<Cell,float[]> between(Prepared prepared,int width,int style,int dashGap) {
        return generate(prepared==null?null:prepared.path,width,style,dashGap,0,RoadStrokeGeometry.lineWidth(style),0.1875);
    }

    /** Also accepts the former four-point Junction setting saved as 3. */
    public static int normalize(int shape) { return Math.max(STRAIGHT,Math.min(ROUNDABOUT,shape)); }

    public static int pointCount(int shape) {
        return switch (shape) { case CURVE -> 3; case ROUNDABOUT -> 4; default -> 0; };
    }

    public static Map<Cell,float[]> between(List<Cell> points, int shape, int width, int style, int dashGap, int position) {
        return generate(points,shape,width,style,dashGap,position,RoadStrokeGeometry.lineWidth(style),0.1875);
    }

    /** Preserve the 2:4-pixel pattern proportions at the small path-preview scale. */
    public static Map<Cell,float[]> preview(List<Cell> points, int shape, int style, int dashGap, int position) {
        return generate(points,shape,1,style,dashGap,position,style==0?0.5:1,0.75);
    }

    private static Map<Cell,float[]> generate(List<Cell> cells,int shape,int width,int style,int dashGap,int position,
            double thickness,double bandOffset) {
        return generate(path(cells,shape),width,style,dashGap,position,thickness,bandOffset);
    }

    private static Map<Cell,float[]> generate(Path path,int width,int style,int dashGap,int position,
            double thickness,double bandOffset) {
        if(path==null || width<1 || width>RoadPaintPlan.MAX_WIDTH || width%2==0 || style<0 || style>3 || position<0 || position>2) return Map.of();
        // Curve and roundabout clicks define the center of the entire painted layout.
        double extent=(width/2)*RoadPaintPlan.LINE_SPACING+(style==2?bandOffset:0)+thickness/2;
        Joint[] joints=joints(path,extent);
        if(joints==null) return Map.of();
        Map<Cell,List<Float>> pieces=new LinkedHashMap<>();
        double[] bands=style==2?new double[]{-bandOffset,bandOffset}:new double[]{0};
        for(int lane=-width/2;lane<=width/2;lane++) for(double band:bands) {
            Lane strip=lane(path,joints,lane*RoadPaintPlan.LINE_SPACING+band,thickness);
            double length=strip.distance[strip.distance.length-1];
            if(style==1) {
                int gap=dashGap==4?4:2;
                if(path.closed) {
                    // Fit whole dashes around each lane. The seam gets the same gap as every other dash.
                    int count=(int)Math.floor(length/(2+gap));
                    if(count<1) return Map.of();
                    double period=length/count;
                    for(int i=0;i<count;i++)
                        if(!append(pieces,path.y,strip,i*period,i*period+2)) return Map.of();
                } else {
                    for(double start=0;start+2<=length+1e-8;start+=2+gap)
                        if(!append(pieces,path.y,strip,start,start+2)) return Map.of();
                }
            } else if(!append(pieces,path.y,strip,0,length)) return Map.of();
        }
        return RoadStrokeGeometry.pack(pieces);
    }

    private static Path path(List<Cell> cells,int shape) {
        if(cells.size()!=pointCount(shape) || cells.isEmpty()) return null;
        Cell first=cells.get(0);
        List<Point> selected=new ArrayList<>(cells.size());
        for(int i=0;i<cells.size();i++) {
            Cell cell=cells.get(i);
            if(cell.y()!=first.y() || Math.max(Math.abs((long)cell.x()-first.x()),Math.abs((long)cell.z()-first.z()))>RoadPaintPlan.MAX_DISTANCE)
                return null;
            for(int j=0;j<i;j++) if(cell.equals(cells.get(j))) return null;
            selected.add(new Point((long)cell.x()-first.x(),(long)cell.z()-first.z()));
        }
        List<Point> points=new ArrayList<>();
        boolean closed=shape!=CURVE;
        if(closed && !convex(selected)) return null;
        int count=selected.size(),sections=closed?count:count-1;
        points.add(selected.get(0));
        for(int i=0;i<sections;i++) {
            Point a=selected.get(i),b=selected.get((i+1)%count);
            Point before=i==0&&!closed?mirror(b,a):selected.get((i+count-1)%count);
            Point after=i+2==count&&!closed?mirror(a,b):selected.get((i+2)%count);
            if(!spline(points,before,a,b,after,closed?ROUND_TANGENT:1)) return null;
        }
        if(!closed) {
            // Square ends reach the clicked block edges and join straight markings cleanly.
            Point a=points.get(0),b=points.get(1),end=points.get(points.size()-1),before=points.get(points.size()-2);
            points.add(0,extend(a,b,0.5));
            points.add(extend(end,before,0.5));
        }
        double total=0;
        for(int i=1;i<points.size();i++) total+=distance(points.get(i-1),points.get(i));
        if(!Double.isFinite(total) || total<1 || total>RoadPaintPlan.MAX_DISTANCE*4 || points.size()>MAX_SEGMENTS+1) return null;
        double ox=first.x()+0.5,oz=first.z()+0.5;
        for(int i=0;i<points.size();i++) {
            Point p=points.get(i);points.set(i,new Point(p.x()+ox,p.z()+oz));
        }
        return new Path(points,first.y(),closed,false);
    }

    private static boolean convex(List<Point> points) {
        double sign=0;
        for(int i=0;i<points.size();i++) {
            Point a=points.get(i),b=points.get((i+1)%points.size()),c=points.get((i+2)%points.size());
            double cross=(b.x()-a.x())*(c.z()-b.z())-(b.z()-a.z())*(c.x()-b.x());
            if(Math.abs(cross)<1e-8 || sign!=0 && Math.signum(cross)!=sign) return false;
            sign=Math.signum(cross);
        }
        return true;
    }

    private static Point mirror(Point p,Point about) { return new Point(2*about.x()-p.x(),2*about.z()-p.z()); }
    private static Point extend(Point from,Point toward,double length) {
        double d=distance(from,toward);
        return new Point(from.x()+(from.x()-toward.x())*length/d,from.z()+(from.z()-toward.z())*length/d);
    }
    private static double distance(Point a,Point b) { return Math.hypot(b.x()-a.x(),b.z()-a.z()); }

    /** Centripetal Catmull-Rom passes through clicks without circular major arcs or uneven-spacing cusps. */
    private static boolean spline(List<Point> output,Point p0,Point p1,Point p2,Point p3,double strength) {
        double a=Math.sqrt(distance(p0,p1)),b=Math.sqrt(distance(p1,p2)),c=Math.sqrt(distance(p2,p3));
        if(Math.min(a,Math.min(b,c))<1e-8) return false;
        double mx=b*((p1.x()-p0.x())/a-(p2.x()-p0.x())/(a+b)+(p2.x()-p1.x())/b);
        double mz=b*((p1.z()-p0.z())/a-(p2.z()-p0.z())/(a+b)+(p2.z()-p1.z())/b);
        double nx=b*((p2.x()-p1.x())/b-(p3.x()-p1.x())/(b+c)+(p3.x()-p2.x())/c);
        double nz=b*((p2.z()-p1.z())/b-(p3.z()-p1.z())/(b+c)+(p3.z()-p2.z())/c);
        // Quarter-circle tangents keep a centered four-point loop from pinching inward between clicks.
        mx*=strength;mz*=strength;nx*=strength;nz*=strength;
        double bound=(Math.hypot(mx,mz)+Math.hypot(nx,nz))/3
            +Math.hypot(p2.x()-p1.x()-(mx+nx)/3,p2.z()-p1.z()-(mz+nz)/3);
        int steps=Math.max(4,(int)Math.ceil(bound/0.5));
        if(output.size()+steps>MAX_SEGMENTS+1) return false;
        for(int i=1;i<=steps;i++) {
            if(i==steps) {output.add(p2);continue;}
            double t=(double)i/steps,t2=t*t,t3=t2*t;
            output.add(new Point((2*t3-3*t2+1)*p1.x()+(t3-2*t2+t)*mx+(-2*t3+3*t2)*p2.x()+(t3-t2)*nx,
                (2*t3-3*t2+1)*p1.z()+(t3-2*t2+t)*mz+(-2*t3+3*t2)*p2.z()+(t3-t2)*nz));
        }
        return true;
    }

    private static Joint[] joints(Path path,double extent) {
        int count=path.points.size(),unique=path.closed?count-1:count;
        Joint[] result=new Joint[count];
        double[] distances=new double[count];
        if(path.aligned)for(int i=1;i<count;i++)distances[i]=distances[i-1]+distance(path.points.get(i-1),path.points.get(i));
        for(int i=0;i<unique;i++) {
            Point p=path.points.get(i);
            int beforeIndex=path.closed?(i+unique-1)%unique:Math.max(0,i-1);
            int afterIndex=path.closed?(i+1)%unique:Math.min(unique-1,i+1);
            if(path.aligned) {
                // Block-sized curb steps must not turn into tight zigzags in the parallel stripes.
                beforeIndex=tangentIndex(path,distances,unique,i,-1);
                afterIndex=tangentIndex(path,distances,unique,i,1);
            }
            Point before=path.points.get(beforeIndex),after=path.points.get(afterIndex);
            double ax=p.x()-before.x(),az=p.z()-before.z(),bx=after.x()-p.x(),bz=after.z()-p.z();
            double al=Math.hypot(ax,az),bl=Math.hypot(bx,bz);
            if(al<1e-8) {ax=bx;az=bz;al=bl;}
            if(bl<1e-8) {bx=ax;bz=az;bl=al;}
            if(Math.min(al,bl)<1e-8) return null;
            double nx=-az/al-bz/bl,nz=ax/al+bx/bl,normal=Math.hypot(nx,nz);
            if(normal<1e-8) return null;
            nx/=normal;nz/=normal;
            double cosine=nx*(-bz/bl)+nz*(bx/bl);
            if(cosine<0.2) return null;
            double cross=Math.abs(ax*bz-az*bx);
            if(cross>1e-8 && al*bl*Math.hypot(ax+bx,az+bz)/(2*cross)<=extent+0.05) return null;
            result[i]=new Joint(nx/cosine,nz/cosine);
        }
        if(path.closed) result[count-1]=result[0];
        return result;
    }

    private static int tangentIndex(Path path,double[] distance,int count,int start,int direction) {
        int index=start;
        for(int step=1;step<=count/2;step++) {
            int next=start+step*direction;
            if(!path.closed && (next<0 || next>=count))break;
            index=(next+count)%count;
            double gap=Math.abs(distance[index]-distance[start]);
            if(path.closed)gap=Math.min(gap,distance[count]-gap);
            if(gap>=2)break;
        }
        return index;
    }

    private static Lane lane(Path path,Joint[] joints,double offset,double width) {
        int count=path.points.size();Point[] left=new Point[count],right=new Point[count];double[] lengths=new double[count];
        Point previous=null;
        for(int i=0;i<count;i++) {
            Point p=path.points.get(i);Joint normal=joints[i];
            left[i]=new Point(p.x()+normal.nx*(offset+width/2),p.z()+normal.nz*(offset+width/2));
            right[i]=new Point(p.x()+normal.nx*(offset-width/2),p.z()+normal.nz*(offset-width/2));
            Point center=new Point(p.x()+normal.nx*offset,p.z()+normal.nz*offset);
            if(i>0) lengths[i]=lengths[i-1]+distance(previous,center);
            previous=center;
        }
        return new Lane(left,right,lengths);
    }

    private static Point lerp(Point a,Point b,double t) { return new Point(a.x()+(b.x()-a.x())*t,a.z()+(b.z()-a.z())*t); }
    private static boolean append(Map<Cell,List<Float>> output,int y,Lane lane,double from,double to) {
        int index=java.util.Arrays.binarySearch(lane.distance,from);
        if(index<0) index=-index-2;
        index=Math.max(0,Math.min(lane.distance.length-2,index));
        for(int i=index;i<lane.distance.length-1 && lane.distance[i]<to-1e-9;i++) {
            double start=Math.max(from,lane.distance[i]),end=Math.min(to,lane.distance[i+1]);
            double length=lane.distance[i+1]-lane.distance[i];
            if(end-start<1e-9 || length<1e-9) continue;
            double a=(start-lane.distance[i])/length,b=(end-lane.distance[i])/length;
            if(!RoadStrokeGeometry.appendPolygon(output,y,List.of(lerp(lane.left[i],lane.left[i+1],a),
                    lerp(lane.left[i],lane.left[i+1],b),lerp(lane.right[i],lane.right[i+1],b),lerp(lane.right[i],lane.right[i+1],a)),true)) return false;
        }
        return true;
    }
}
