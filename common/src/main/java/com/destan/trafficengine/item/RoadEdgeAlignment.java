package com.destan.trafficengine.item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.destan.trafficengine.item.RoadStrokeGeometry.Point;

/** Reads and fits a road corridor once per paint transaction. Nothing is retained or ticked. */
public final class RoadEdgeAlignment {
    public static final int UNKNOWN=-1, EDGE=0, ROAD=1;
    private static final int MAX_READS=16384, MAX_ISLAND=4096, HARMONICS=4;
    private static final double REACH=24, MAX_SHIFT=6;
    @FunctionalInterface public interface Surface { int at(int x,int z); }
    private record Hit(double distance,int x,int z) {}
    private RoadEdgeAlignment() {}

    static List<Point> align(List<Point> input,boolean closed,List<RoadPaintPlan.Cell> clicks,Surface access) {
        if(input.size()<3 || access==null)return input;
        Cache road=new Cache(access);
        // The inward edge of a roundabout must belong to its central island, not a traffic splitter.
        if(closed && !centralIsland(input,clicks,road))return input;
        int size=closed?input.size()-1:input.size(),terms=closed?1+2*HARMONICS:4;
        double[] distance=new double[input.size()],shift=new double[size],width=new double[size],nx=new double[size],nz=new double[size];
        double[] weight=new double[size],measured=new double[size];
        double[][] basis=new double[size][terms];
        Arrays.fill(shift,Double.NaN);
        for(int i=1;i<input.size();i++)distance[i]=distance[i-1]+gap(input.get(i-1),input.get(i));
        double length=distance[distance.length-1];if(length<1)return input;
        double area=0;Point origin=input.get(0);
        if(closed)for(int i=0;i<size;i++) {
            Point a=input.get(i),b=input.get(i+1);
            area+=(a.x()-origin.x())*(b.z()-origin.z())-(b.x()-origin.x())*(a.z()-origin.z());
        }
        int valid=0;
        for(int i=0;i<size;i++) {
            Point p=input.get(i),a=input.get(neighbour(distance,size,i,-1,closed)),b=input.get(neighbour(distance,size,i,1,closed));
            double dx=b.x()-a.x(),dz=b.z()-a.z(),d=Math.hypot(dx,dz);if(d<1e-8)continue;
            nx[i]=-dz/d;nz[i]=dx/d;
            basis(distance[i]/length,closed,basis[i]);
            double seed=seed(p,nx[i],nz[i],road);if(!Double.isFinite(seed))continue;
            double x=p.x()+nx[i]*seed,z=p.z()+nz[i]*seed;
            Hit left=edge(x,z,nx[i],nz[i],road),right=edge(x,z,-nx[i],-nz[i],road);
            if(left==null || right==null)continue;
            if(closed) {
                Hit inner=area>0?left:right,outer=area>0?right:left;
                if(!road.inner(inner.x,inner.z) || road.inner(outer.x,outer.z))continue;
            }
            double w=left.distance+right.distance,s=seed+(left.distance-right.distance)/2;
            if(w<1 || Math.abs(s)>MAX_SHIFT)continue;
            shift[i]=s;width[i]=w;measured[valid++]=w;
        }
        if(valid<6 || valid<size/8)return input;
        Arrays.sort(measured,0,valid);double typical=measured[valid/2];
        int reliable=0;
        for(int i=0;i<size;i++) {
            // Entrances and median noses are poor width measurements; interpolate across them globally.
            if(width[i]>typical*1.4+1 || width[i]<typical*0.6)shift[i]=Double.NaN;
            if(Double.isFinite(shift[i])){weight[i]=1;reliable++;}
        }
        if(reliable<6 || reliable<size/8)return input;
        // Ambiguous sections stay near the user's middle-road clicks without forcing a local kink.
        for(int i=0;i<size;i++) {
            for(var click:clicks)if(Math.hypot(input.get(i).x()-click.x()-0.5,input.get(i).z()-click.z()-0.5)<1e-7) {
                if(!Double.isFinite(shift[i]))shift[i]=0;
                weight[i]=6;break;
            }
        }
        double[] fit=fit(basis,shift,weight,closed,reliable);
        if(fit==null)return input;
        double maximum=0;
        for(double[] row:basis)maximum=Math.max(maximum,Math.abs(dot(row,fit)));
        double scale=maximum>MAX_SHIFT?MAX_SHIFT/maximum:1;
        // Accept the whole smooth line, or reduce its correction as a whole. Never revert individual samples.
        for(int attempt=0;attempt<6;attempt++,scale*=0.5) {
            List<Point> result=new ArrayList<>(input.size());double movement=0;
            for(int i=0;i<size;i++) {
                Point p=input.get(i);double s=dot(basis[i],fit)*scale;
                result.add(new Point(p.x()+nx[i]*s,p.z()+nz[i]*s));movement+=Math.abs(s);
            }
            if(movement<1e-6)return input;
            if(closed)result.add(result.get(0));
            if(safe(result,input,closed,area,road))return List.copyOf(result);
        }
        return input;
    }

    private static int neighbour(double[] distance,int count,int start,int direction,boolean closed) {
        int index=start;
        for(int step=1;step<count/2;step++) {
            int next=start+step*direction;if(!closed && (next<0 || next>=count))break;
            index=(next+count)%count;double d=Math.abs(distance[index]-distance[start]);
            if(closed)d=Math.min(d,distance[count]-d);
            if(d>=2)break;
        }
        return index;
    }

    private static void basis(double u,boolean closed,double[] row) {
        if(closed) {
            row[0]=1;
            for(int k=1;k<=HARMONICS;k++){row[k*2-1]=Math.cos(2*Math.PI*k*u);row[k*2]=Math.sin(2*Math.PI*k*u);}
        } else {
            // Endpoint-zero Chebyshev terms keep the curve joined to the clicked straight markings.
            double t=2*u-1,e=1-t*t;
            row[0]=e;row[1]=e*t;row[2]=e*(2*t*t-1);row[3]=e*(4*t*t*t-3*t);
        }
    }

    /** Small robust least-squares fit; high frequencies are penalized instead of copying curb stair steps. */
    private static double[] fit(double[][] basis,double[] shift,double[] weights,boolean closed,int count) {
        int terms=basis[0].length;double[] result=new double[terms];
        for(int pass=0;pass<5;pass++) {
            double[][] matrix=new double[terms][terms+1];
            for(int i=0;i<basis.length;i++)if(weights[i]>0) {
                double residual=Math.abs(shift[i]-dot(basis[i],result));
                double w=weights[i]*(pass==0 || residual<=0.6?1:0.6/residual);
                for(int j=0;j<terms;j++) {
                    matrix[j][terms]+=w*basis[i][j]*shift[i];
                    for(int k=0;k<terms;k++)matrix[j][k]+=w*basis[i][j]*basis[i][k];
                }
            }
            for(int i=0;i<terms;i++) {
                int frequency=closed?(i+1)/2:i+1;
                matrix[i][i]+=count*(0.0001+0.0008*Math.pow(frequency,4));
            }
            result=solve(matrix);if(result==null)return null;
        }
        return result;
    }

    private static double[] solve(double[][] m) {
        int n=m.length;
        for(int k=0;k<n;k++) {
            int pivot=k;for(int i=k+1;i<n;i++)if(Math.abs(m[i][k])>Math.abs(m[pivot][k]))pivot=i;
            double[] swap=m[k];m[k]=m[pivot];m[pivot]=swap;
            if(Math.abs(m[k][k])<1e-9)return null;
            double divisor=m[k][k];for(int j=k;j<=n;j++)m[k][j]/=divisor;
            for(int i=0;i<n;i++)if(i!=k){double factor=m[i][k];for(int j=k;j<=n;j++)m[i][j]-=factor*m[k][j];}
        }
        double[] result=new double[n];for(int i=0;i<n;i++)result[i]=m[i][n];return result;
    }
    private static double dot(double[] a,double[] b){double sum=0;for(int i=0;i<a.length;i++)sum+=a[i]*b[i];return sum;}
    private static double gap(Point a,Point b){return Math.hypot(a.x()-b.x(),a.z()-b.z());}

    private static boolean safe(List<Point> points,List<Point> original,boolean closed,double area,Cache road) {
        int size=closed?points.size()-1:points.size();
        for(int i=0;i<size;i++) {
            Point p=points.get(i);
            if(road.at((int)Math.floor(p.x()),(int)Math.floor(p.z()))!=ROAD)return false;
            if(!closed && (i==0 || i==size-1))continue;
            Point a=points.get((i+size-1)%size),b=points.get((i+1)%size);
            double ax=p.x()-a.x(),az=p.z()-a.z(),bx=b.x()-p.x(),bz=b.z()-p.z();
            double al=Math.hypot(ax,az),bl=Math.hypot(bx,bz),cross=ax*bz-az*bx;
            if(al<1e-6 || bl<1e-6 || (ax*bx+az*bz)/(al*bl)<0.9)return false;
            if(closed && cross*Math.signum(area)<-1e-5)return false;
            Point oa=original.get((i+size-1)%size),ob=original.get((i+1)%size);
            double ox=ob.x()-oa.x(),oz=ob.z()-oa.z();
            if(((ax+bx)*ox+(az+bz)*oz)/(Math.hypot(ax+bx,az+bz)*Math.hypot(ox,oz))<0.85)return false;
        }
        return true;
    }

    private static boolean centralIsland(List<Point> points,List<RoadPaintPlan.Cell> clicks,Cache road) {
        double x=0,z=0,minX=Double.POSITIVE_INFINITY,maxX=-minX,minZ=minX,maxZ=-minX;
        for(var p:clicks){x+=p.x()+0.5;z+=p.z()+0.5;}x/=clicks.size();z/=clicks.size();
        for(Point p:points){minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minZ=Math.min(minZ,p.z());maxZ=Math.max(maxZ,p.z());}
        int seedX=(int)Math.floor(x),seedZ=(int)Math.floor(z);
        if(road.at(seedX,seedZ)!=EDGE) {
            boolean found=false;
            for(int radius=1;radius<=3 && !found;radius++)for(int dz=-radius;dz<=radius && !found;dz++)for(int dx=-radius;dx<=radius;dx++)
                if(road.at(seedX+dx,seedZ+dz)==EDGE){seedX+=dx;seedZ+=dz;found=true;break;}
            if(!found)return false;
        }
        long[] queue=new long[MAX_ISLAND];int end=0;
        queue[end++]=key(seedX,seedZ);road.markInner(seedX,seedZ);
        for(int next=0;next<end;next++) {
            int cx=(int)(queue[next]>>32),cz=(int)queue[next];
            if(cx<=minX || cx>=maxX || cz<=minZ || cz>=maxZ)return false;
            for(int direction=0;direction<4;direction++) {
                int px=cx+(direction==0?1:direction==1?-1:0),pz=cz+(direction==2?1:direction==3?-1:0);
                int type=road.at(px,pz);if(type==UNKNOWN)return false;
                if(type==EDGE && !road.inner(px,pz)) {
                    if(end==MAX_ISLAND)return false;
                    queue[end++]=key(px,pz);road.markInner(px,pz);
                }
            }
        }
        return true;
    }

    private static double seed(Point p,double nx,double nz,Cache road) {
        int surface=road.at((int)Math.floor(p.x()),(int)Math.floor(p.z()));
        if(surface==ROAD)return 0;if(surface==UNKNOWN)return Double.NaN;
        for(double offset=0.25;offset<=MAX_SHIFT;offset+=0.25) {
            if(road.at((int)Math.floor(p.x()+nx*offset),(int)Math.floor(p.z()+nz*offset))==ROAD)return offset;
            if(road.at((int)Math.floor(p.x()-nx*offset),(int)Math.floor(p.z()-nz*offset))==ROAD)return -offset;
        }
        return Double.NaN;
    }

    /** Grid traversal finds the actual curb cell without repeatedly sampling the same block. */
    private static Hit edge(double x,double z,double nx,double nz,Cache road) {
        int cx=(int)Math.floor(x),cz=(int)Math.floor(z),sx=nx>0?1:-1,sz=nz>0?1:-1;
        double tx=Math.abs(nx)<1e-10?Double.POSITIVE_INFINITY:((nx>0?cx+1:cx)-x)/nx;
        double tz=Math.abs(nz)<1e-10?Double.POSITIVE_INFINITY:((nz>0?cz+1:cz)-z)/nz;
        double dx=Math.abs(nx)<1e-10?Double.POSITIVE_INFINITY:Math.abs(1/nx),dz=Math.abs(nz)<1e-10?Double.POSITIVE_INFINITY:Math.abs(1/nz);
        while(true) {
            double t=Math.min(tx,tz);if(t>REACH)return null;
            boolean moveX=tx<=tz+1e-9,moveZ=tz<=tx+1e-9;
            if(moveX){cx+=sx;tx+=dx;}if(moveZ){cz+=sz;tz+=dz;}
            int type=road.at(cx,cz);if(type!=ROAD)return type==EDGE?new Hit(t,cx,cz):null;
        }
    }

    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    /** Bounded primitive cache; its small inner-island flag shares the existing byte array. */
    private static final class Cache {
        private final Surface surface;
        private long[] keys=new long[256];private byte[] values=new byte[256];private int count;
        Cache(Surface surface){this.surface=surface;}
        private int slot(long key) {
            int index=index(key,keys.length-1);
            while(values[index]!=0 && keys[index]!=key)index=(index+1)&(keys.length-1);
            return index;
        }
        int at(int x,int z) {
            long key=key(x,z);int index=slot(key);
            if(values[index]!=0)return (values[index]&3)-2;
            if(count==MAX_READS)return UNKNOWN;
            int value=surface.at(x,z);if(value<UNKNOWN || value>ROAD)value=UNKNOWN;
            keys[index]=key;values[index]=(byte)(value+2);count++;
            if(count*2>=keys.length && count<MAX_READS)grow();
            return value;
        }
        boolean inner(int x,int z){return (values[slot(key(x,z))]&4)!=0;}
        void markInner(int x,int z){values[slot(key(x,z))]|=4;}
        private static int index(long key,int mask){long hash=key*0x9E3779B97F4A7C15L;return (int)(hash^(hash>>>32))&mask;}
        private void grow() {
            long[] oldKeys=keys;byte[] oldValues=values;keys=new long[keys.length*2];values=new byte[keys.length];
            for(int i=0;i<oldKeys.length;i++)if(oldValues[i]!=0) {
                int slot=slot(oldKeys[i]);keys[slot]=oldKeys[i];values[slot]=oldValues[i];
            }
        }
    }
}
