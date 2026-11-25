package com.xuebutong.app.screen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import ai.onnxruntime.*;
import java.io.*;
import java.nio.FloatBuffer;
import java.util.*;

/** PP-OCR 的文字行检测和逐行转录，在设备上处理本次屏幕快照。 */
public final class LocalOcr implements AutoCloseable {
    private final Context context;
    private OrtEnvironment env;
    private OrtSession detector, recognizer;
    private String[] alphabet;
    public LocalOcr(Context context) { this.context = context; }

    private byte[] asset(String name) throws IOException {
        try (InputStream in = context.getAssets().open("ocr/" + name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] block = new byte[32768];
            int n;
            while ((n = in.read(block)) >= 0) out.write(block, 0, n);
            return out.toByteArray();
        }
    }
    private void initialize() throws Exception {
        if (recognizer != null) return;
        env = OrtEnvironment.getEnvironment();
        alphabet = new String(asset("dict.txt"), "UTF-8").split("\n");
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);
            options.setInterOpNumThreads(1);
            if (detector == null) detector = env.createSession(asset("det.onnx"), options);
            recognizer = env.createSession(asset("rec.onnx"), options);
        }
    }
    public synchronized String read(Bitmap source) {
        try {
            initialize();
            int sw=source.getWidth(), sh=source.getHeight();
            float scale=Math.min(1f, 1536f/Math.max(sw,sh));
            int w=Math.max(32,32*Math.round(sw*scale/32)), h=Math.max(32,32*Math.round(sh*scale/32));
            List<Rect> lines;
            try (OrtSession.Result result = infer(detector, source, w, h, true)) {
                FloatBuffer scores=((OnnxTensor)result.get(0)).getFloatBuffer();
                lines=boxes(scores,w,h,sw,sh);
            }
            StringBuilder text=new StringBuilder();
            for (Rect box:lines) {
                Bitmap line=Bitmap.createBitmap(source,box.left,box.top,box.width(),box.height());
                try {
                    int width=Math.min(1920,Math.max(16,Math.round(48f*line.getWidth()/line.getHeight())));
                    try (OrtSession.Result result=infer(recognizer,line,width,48,false)) {
                        OnnxTensor output=(OnnxTensor)result.get(0);
                        String s=decode(output.getFloatBuffer(),(int)output.getInfo().getShape()[2]);
                        if (!s.isEmpty()) text.append(s).append('\n');
                    }
                } finally { if(line!=source) line.recycle(); }
            }
            return text.toString().trim();
        } catch (Exception | LinkageError e) {
            throw new IllegalStateException("本机识字失败，请重新启动屏幕文字模式", e);
        }
    }
    private OrtSession.Result infer(OrtSession session,Bitmap source,int w,int h,boolean det) throws OrtException {
        Bitmap sized=Bitmap.createScaledBitmap(source,w,h,true);
        int n=w*h;
        int[] pixels=new int[n];
        sized.getPixels(pixels,0,w,0,0,w,h);
        if(sized!=source) sized.recycle();
        float[] values=new float[n*3];
        float[] means={.485f,.456f,.406f}, std={.229f,.224f,.225f};
        for(int c=0;c<3;c++) {
            float mean=det?means[c]:.5f, divisor=det?std[c]:.5f;
            for(int i=0;i<n;i++) values[c*n+i]=(((pixels[i]>>(8*c))&255)/255f-mean)/divisor;
        }
        try (OnnxTensor input=OnnxTensor.createTensor(env,FloatBuffer.wrap(values),new long[]{1,3,h,w})) {
            return session.run(Collections.singletonMap(session.getInputNames().iterator().next(),input));
        }
    }
    private String decode(FloatBuffer scores,int classes) {
        StringBuilder line=new StringBuilder(); int last=-1, count=0; float confidence=0;
        while(scores.remaining()>=classes) {
            int best=0;float prob=scores.get();
            for(int i=1;i<classes;i++){float p=scores.get();if(p>prob){prob=p;best=i;}}
            if(best>0 && best!=last) {
                line.append(best<=alphabet.length?alphabet[best-1]:" ");
                count++; confidence+=prob;
            }
            last=best;
        }
        return count>0 && confidence/count>=.48f?line.toString().trim():"";
    }
    private static List<Rect> boxes(FloatBuffer scores,int w,int h,int sw,int sh) {
        int size=w*h;
        byte[] mask=new byte[size];
        for(int i=0;i<size;i++) if(scores.get()>.30f) mask[i]=1;
        int[] queue=new int[size];
        List<Rect> boxes=new ArrayList<>();
        for(int pos=0;pos<size;pos++) {
            if(mask[pos]==0) continue;
            int head=0,tail=1;queue[0]=pos;mask[pos]=0;
            int left=w,right=0,top=h,bottom=0;
            while(head<tail) {
                int p=queue[head++],x=p%w,y=p/w;
                left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
                for(int yy=Math.max(0,y-1);yy<=Math.min(h-1,y+1);yy++)
                    for(int xx=Math.max(0,x-1);xx<=Math.min(w-1,x+1);xx++) {
                        int next=yy*w+xx;
                        if(mask[next]!=0){mask[next]=0;queue[tail++]=next;}
                    }
            }
            int bh=bottom-top+1,bw=right-left+1;
            if(tail<12 || bh<3 || bw<3) continue;
            float margin=.75f*bw*bh/(bw+bh);
            boxes.add(new Rect(Math.max(0,(int)((left-margin)*sw/w)),Math.max(0,(int)((top-margin)*sh/h)),
                    Math.min(sw,(int)Math.ceil((right+1+margin)*sw/w)),Math.min(sh,(int)Math.ceil((bottom+1+margin)*sh/h))));
        }
        Collections.sort(boxes,(a,b)->Integer.compare(a.centerY(),b.centerY()));
        for(int i=0;i<boxes.size();) {
            Rect first=boxes.get(i);int end=i+1;
            while(end<boxes.size() && boxes.get(end).centerY()-first.centerY()<first.height()/2) end++;
            Collections.sort(boxes.subList(i,end),(a,b)->Integer.compare(a.left,b.left));i=end;
        }
        if(boxes.size()>180) throw new IllegalStateException("整屏文字过多，请单独打开当前题目");
        return boxes;
    }
    public synchronized void close() {
        try { if(recognizer!=null) recognizer.close(); if(detector!=null) detector.close(); }
        catch(OrtException ignored) {} finally { recognizer=null;detector=null; }
    }
}
