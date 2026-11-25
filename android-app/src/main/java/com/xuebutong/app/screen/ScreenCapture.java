package com.xuebutong.app.screen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.nio.ByteBuffer;

/** 授权仅在当前服务生命周期内有效。采集后立即断开输出表面。 */
public final class ScreenCapture {
    private final Context context;
    private final Runnable onRevoked;
    private MediaProjection projection;
    private VirtualDisplay display;
    private final Handler main = new Handler(Looper.getMainLooper());
    public ScreenCapture(Context context,Runnable onRevoked) { this.context=context;this.onRevoked=onRevoked; }

    public synchronized void start(Intent data) {
        stop();
        MediaProjection p=context.getSystemService(MediaProjectionManager.class).getMediaProjection(Activity.RESULT_OK,data);
        if(p==null) throw new IllegalStateException("未取得屏幕共享授权");
        projection=p;
        p.registerCallback(new MediaProjection.Callback(){
            @Override public void onStop(){
                synchronized(ScreenCapture.this){
                    if(projection!=p) return;
                    projection=null;
                    if(display!=null){display.release();display=null;}
                }
                onRevoked.run();
            }
        }, main);
    }
    public synchronized void stop() {
        MediaProjection p=projection;projection=null;
        if(display!=null){display.release();display=null;}
        if(p!=null) p.stop();
    }
    public DisplayMetrics metrics() {
        DisplayMetrics m=new DisplayMetrics();
        context.getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(m);
        return m;
    }
    public synchronized Bitmap capture() throws InterruptedException {
        if(projection==null) throw new IllegalStateException("屏幕共享已结束，请回到 App 重新启动搜题");
        DisplayMetrics m=metrics();
        try(ImageReader reader=ImageReader.newInstance(m.widthPixels,m.heightPixels,PixelFormat.RGBA_8888,2)){
            if(display==null) {
                display=projection.createVirtualDisplay("整屏文字识别",m.widthPixels,m.heightPixels,m.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,main);
            } else {
                display.resize(m.widthPixels,m.heightPixels,m.densityDpi);
                display.setSurface(reader.getSurface());
            }
            long deadline=SystemClock.elapsedRealtime()+2400;
            try {
                while(SystemClock.elapsedRealtime()<deadline) {
                    try(Image image=reader.acquireLatestImage()) {
                        if(image!=null) {
                            Image.Plane plane=image.getPlanes()[0]; ByteBuffer data=plane.getBuffer();
                            int width=image.getWidth(), height=image.getHeight();
                            Bitmap frame=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
                            int[] row=new int[width];
                            for(int y=0;y<height;y++){
                                int off=y*plane.getRowStride();
                                for(int x=0;x<width;x++,off+=plane.getPixelStride())
                                    row[x]=0xff000000|((data.get(off)&255)<<16)|((data.get(off+1)&255)<<8)|(data.get(off+2)&255);
                                frame.setPixels(row,0,width,0,y,width,1);
                            }
                            return frame;
                        }
                    }
                    Thread.sleep(35);
                }
            } finally { display.setSurface(null); }
        }
        throw new IllegalStateException("未获取到可读画面，请检查共享授权后重试");
    }
}
