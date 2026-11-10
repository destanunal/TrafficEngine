package com.destan.trafficengine.client.ber;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

/** Hidden native GPU check of the production atlas, mip chain and patch coordinates. */
public final class SignSurfaceGpuCheck {
    static final int W=800,H=600;
    static void patch(int tile,double x1,double y1,double x2,double y2,int joins) {
        boolean left=(joins&1)!=0,right=(joins&2)!=0;
        glBegin(GL_QUADS);
        for(int row=0;row<3;row++)for(int col=0;col<3;col++) {
            double ax=RoadSignSurface.position(col,x1,x2,left,right),bx=RoadSignSurface.position(col+1,x1,x2,left,right);
            double ay=RoadSignSurface.position(row,y1,y2,false,false),by=RoadSignSurface.position(row+1,y1,y2,false,false);
            if(bx<=ax||by<=ay)continue;
            float u1=RoadSignSurface.u(tile,RoadSignSurface.texel(col,left,right)),u2=RoadSignSurface.u(tile,RoadSignSurface.texel(col+1,left,right));
            float v1=RoadSignSurface.v(tile,RoadSignSurface.texel(row,false,false)),v2=RoadSignSurface.v(tile,RoadSignSurface.texel(row+1,false,false));
            glTexCoord2f(u1,v1);glVertex2d(ax,ay);glTexCoord2f(u1,v2);glVertex2d(ax,by);
            glTexCoord2f(u2,v2);glVertex2d(bx,by);glTexCoord2f(u2,v1);glVertex2d(bx,ay);
        }
        glEnd();
    }
    static void sign(double x,double y,double scale,double tilt,int theme,boolean panel) {
        glPushMatrix();glTranslated(x,y,0);glRotated(tilt,0,0,1);glScaled(scale,scale,1);
        patch(RoadSignSurface.frame(theme,2),0,0,192,144,0);
        if(panel)patch(RoadSignSurface.panel(theme==2?3:1),5,5,187,100,0);
        glPopMatrix();
    }
    public static void main(String[] args)throws Exception {
        if(!glfwInit())throw new AssertionError("Cannot initialize hidden GPU test");
        glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
        long window=glfwCreateWindow(W,H,"Traffic Engine border regression",0,0);
        if(window==0)throw new AssertionError("Cannot create hidden GPU context");
        ByteBuffer pixels=MemoryUtil.memAlloc(W*H*4);
        try {
            glfwMakeContextCurrent(window);GL.createCapabilities();
            glViewport(0,0,W,H);glMatrixMode(GL_PROJECTION);glLoadIdentity();glOrtho(0,W,H,0,-1,1);glMatrixMode(GL_MODELVIEW);
            glEnable(GL_TEXTURE_2D);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
            glEnable(GL_ALPHA_TEST);glAlphaFunc(GL_GREATER,.1F);glColor4f(1,1,1,1);
            int texture=glGenTextures();glBindTexture(GL_TEXTURE_2D,texture);
            int[][] levels=RoadSignSurface.pixels();
            for(int level=0;level<levels.length;level++) {
                int w=RoadSignSurface.WIDTH>>level,h=RoadSignSurface.HEIGHT>>level;
                ByteBuffer data=MemoryUtil.memAlloc(w*h*4);
                try{for(int argb:levels[level])data.put((byte)(argb>>16)).put((byte)(argb>>8)).put((byte)argb).put((byte)(argb>>>24));data.flip();
                    glTexImage2D(GL_TEXTURE_2D,level,GL_RGBA8,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,data);
                }finally{MemoryUtil.memFree(data);}
            }
            glTexParameteri(GL_TEXTURE_2D,0x813D,RoadSignSurface.LEVELS);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,0x812F);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,0x812F);
            int checks=0;double minimum=Double.POSITIVE_INFINITY,maximum=0;
            for(double scale:new double[]{.25,.375,.5,.75,1}) {
                double min=Double.POSITIVE_INFINITY,max=0;
                for(int phase=0;phase<10;phase++) {
                    glClearColor(214/255F,217/255F,211/255F,1);glClear(GL_COLOR_BUFFER_BIT);
                    glLoadIdentity();glTranslated(100+phase/10D,100+phase/10D,0);glScaled(scale,scale,1);
                    patch(RoadSignSurface.panel(1),0,0,192,144,0);
                    glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
                    double sum=0;
                    for(int x=110;x<100+(int)(180*scale);x++) {
                        int darkness=0;
                        for(int y=92;y<109;y++)darkness+=Math.max(0,214-Byte.toUnsignedInt(pixels.get(((H-y-1)*W+x)*4)));
                        if(darkness<4)throw new AssertionError("Broken top edge at scale="+scale+", phase="+phase+", x="+x);
                        sum+=darkness;checks++;
                    }
                    double mean=sum/(100+(int)(180*scale)-110);min=Math.min(min,mean);max=Math.max(max,mean);
                }
                minimum=Math.min(minimum,min);maximum=Math.max(maximum,max);
                if(max/min>1.30)throw new AssertionError("Large phase-dependent edge coverage change at "+scale+": "+min+".."+max);
            }
            glLoadIdentity();glClearColor(.23F,.33F,.18F,1);glClear(GL_COLOR_BUFFER_BIT);
            for(int row=0;row<3;row++)for(int col=0;col<4;col++)sign(45+col*190,45+row*180,.35+row*.2,-2+col, col%3,col>0);
            glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
            BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
                int p=((H-y-1)*W+x)*4;
                image.setRGB(x,y,0xFF000000|(Byte.toUnsignedInt(pixels.get(p))<<16)|(Byte.toUnsignedInt(pixels.get(p+1))<<8)|Byte.toUnsignedInt(pixels.get(p+2)));
            }
            ImageIO.write(image,"png",new File(args[0]));
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("OpenGL error");
            System.out.println("PASS hidden GPU: "+checks+" edge samples over five minification scales and ten subpixel phases; "+glGetString(GL_RENDERER));
            glDeleteTextures(texture);
        }finally{MemoryUtil.memFree(pixels);glfwDestroyWindow(window);glfwTerminate();}
    }
}
