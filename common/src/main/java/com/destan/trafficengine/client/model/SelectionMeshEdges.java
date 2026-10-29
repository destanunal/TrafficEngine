package com.destan.trafficengine.client.model;

import java.util.*;

/** Weld a static authored mesh once, then draw its silhouette without interior seams. */
public final class SelectionMeshEdges {
    private record Point(long x,long y,long z) implements Comparable<Point> {
        static Point of(float[] p,int i){return new Point(Math.round(p[i]*100000),Math.round(p[i+1]*100000),Math.round(p[i+2]*100000));}
        public int compareTo(Point b){int c=Long.compare(x,b.x);if(c==0)c=Long.compare(y,b.y);return c==0?Long.compare(z,b.z):c;}
    }
    private record Key(Point a,Point b) {}
    private static final class Edge {
        final float[] a,b;float[] n,m;
        Edge(float[] a,float[] b,float[] n){this.a=a;this.b=b;this.n=n;}
    }
    private SelectionMeshEdges() {}
    /** Face entries contain four xyz corners. Cold construction only. */
    public static float[] build(List<float[]> faces) {
        Map<Key,Edge> edges=new LinkedHashMap<>();
        for(float[] face:faces) {
            float ax=face[3]-face[0],ay=face[4]-face[1],az=face[5]-face[2];
            float bx=face[6]-face[0],by=face[7]-face[1],bz=face[8]-face[2];
            float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
            double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(length<1e-10)continue;
            float[] normal={(float)(nx/length),(float)(ny/length),(float)(nz/length)};
            for(int i=0;i<4;i++) {
                int j=(i+1)%4;Point a=Point.of(face,i*3),b=Point.of(face,j*3);if(a.equals(b))continue;
                Key key=a.compareTo(b)<0?new Key(a,b):new Key(b,a);
                Edge old=edges.get(key);
                if(old==null)edges.put(key,new Edge(Arrays.copyOfRange(face,i*3,i*3+3),Arrays.copyOfRange(face,j*3,j*3+3),normal));
                else if(old.m==null && dot(old.n,normal)<.99999F)old.m=normal;
                else if(old.m==null)old.m=normal;
            }
        }
        // [endpoints(6), line normal(3), adjacent face normals(6)]
        edges.values().removeIf(e->e.m!=null && Math.abs(dot(e.n,e.m))>.99999F);
        float[] packed=new float[edges.size()*15];int p=0;
        for(Edge edge:edges.values()) {
            for(float v:edge.a)packed[p++]=v;for(float v:edge.b)packed[p++]=v;
            float dx=edge.b[0]-edge.a[0],dy=edge.b[1]-edge.a[1],dz=edge.b[2]-edge.a[2];float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            packed[p++]=dx/len;packed[p++]=dy/len;packed[p++]=dz/len;
            for(float v:edge.n)packed[p++]=v;
            for(int i=0;i<3;i++)packed[p++]=edge.m==null?0:edge.m[i];
        }
        return packed;
    }
    private static float dot(float[] a,float[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    public static boolean visible(float[] edges,int i,double cameraX,double cameraY,double cameraZ) {
        double x=cameraX-(edges[i]+edges[i+3])*.5,y=cameraY-(edges[i+1]+edges[i+4])*.5,z=cameraZ-(edges[i+2]+edges[i+5])*.5;
        return (edges[i+9]*x+edges[i+10]*y+edges[i+11]*z>1e-7)
            !=(edges[i+12]*x+edges[i+13]*y+edges[i+14]*z>1e-7);
    }
}
