package com.ykt.assistant;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class SlideExporter {
    interface ImageSource { byte[] fetch(JSONObject slide) throws Exception; }
    interface Progress { void update(ExportProgress progress); }
    static Bitmap decode(byte[] data,int maxEdge) throws Exception {
        BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true; BitmapFactory.decodeByteArray(data,0,data.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>30000||bounds.outHeight>30000) throw new Exception("图片尺寸无效或过大");
        BitmapFactory.Options options=new BitmapFactory.Options(); options.inSampleSize=1;
        while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>maxEdge) options.inSampleSize*=2;
        Bitmap b=BitmapFactory.decodeByteArray(data,0,data.length,options); if(b==null) throw new Exception("图片无法解码"); return b;
    }
    static void save(Context context,ImageSource source,JSONObject p,String format,Uri dest,Progress progress,java.util.function.BooleanSupplier cancelled) throws Exception {
        active(cancelled);
        JSONArray slides=p.getJSONArray("slides");
        if(!format.equals("pptx")&&!format.equals("zip")) throw new IllegalArgumentException("不支持的导出格式");
        if(slides.length()==0||slides.length()>200) throw new Exception("仅支持导出 1–200 页的课件");
        File folder=new File(context.getCacheDir(),"export-"+java.util.UUID.randomUUID()); if(!folder.mkdirs()) throw new Exception("无法创建临时目录");
        List<File> files=new ArrayList<>(); long size=0; double firstImageRatio=16.0/9;
        try {
            progress.update(ExportProgress.fetching(0,slides.length()));
            for(int i=0;i<slides.length();i++) {
                active(cancelled); byte[] bytes=source.fetch(slides.getJSONObject(i)); active(cancelled); size+=bytes.length;
                if(size>150L*1024*1024) throw new Exception("课件超过 150MB，请分次保存页面");
                String ext=bytes.length>3&&bytes[0]==(byte)0x89?"png":bytes.length>3&&bytes[0]=='G'?"gif":bytes.length>12&&bytes[0]=='R'?"webp":"jpg";
                File file=new File(folder,(i+1)+"."+(format.equals("pptx")?"jpg":ext));
                try(FileOutputStream out=new FileOutputStream(file)) {
                    if(format.equals("pptx")) {
                        Bitmap decoded=decode(bytes,4096); if(i==0) firstImageRatio=(double)decoded.getWidth()/decoded.getHeight();
                        Bitmap rgb=Bitmap.createBitmap(decoded.getWidth(),decoded.getHeight(),Bitmap.Config.ARGB_8888);
                        Canvas canvas=new Canvas(rgb); canvas.drawColor(Color.WHITE); canvas.drawBitmap(decoded,0,0,null); decoded.recycle();
                        if(!rgb.compress(Bitmap.CompressFormat.JPEG,95,out)) throw new Exception("页面转换失败"); rgb.recycle();
                    } else out.write(bytes);
                }
                files.add(file); progress.update(ExportProgress.fetching(i+1,slides.length()));
            }
            active(cancelled); progress.update(ExportProgress.building(slides.length()));
            File result=new File(folder,"result."+format);
            try(OutputStream out=guard(new FileOutputStream(result),cancelled)) {
                if(out==null) throw new Exception("无法写入文件");
                if(format.equals("pptx")) {
                    double ratio=p.optDouble("width",0)/p.optDouble("height",0); if(!Double.isFinite(ratio)||ratio<=0) ratio=firstImageRatio;
                    PptxWriter.write(out,p.optString("title","雨课堂课件"),ratio,files);
                } else {
                    try(ZipOutputStream zip=new ZipOutputStream(out)) {
                        for(int i=0;i<files.size();i++) {
                            File f=files.get(i); String ext=f.getName().substring(f.getName().lastIndexOf('.'));
                            zip.putNextEntry(new ZipEntry(String.format(java.util.Locale.ROOT,"%03d",i+1)+ext));
                            try(FileInputStream input=new FileInputStream(f)) { byte[] buffer=new byte[8192]; int n; while((n=input.read(buffer))!=-1) zip.write(buffer,0,n); } zip.closeEntry();
                        }
                    }
                }
            }
            active(cancelled); long length=result.length(),written=0;
            progress.update(ExportProgress.saving(slides.length(),0,length));
            try(FileInputStream input=new FileInputStream(result); OutputStream out=guard(context.getContentResolver().openOutputStream(dest,"wt"),cancelled)) {
                byte[] buffer=new byte[32768]; int n;
                while((n=input.read(buffer))!=-1) { active(cancelled); out.write(buffer,0,n); written+=n; progress.update(ExportProgress.saving(slides.length(),written,length)); }
            }
        } finally { File[] cached=folder.listFiles(); if(cached!=null) for(File f:cached) if(!f.delete()) f.deleteOnExit(); if(!folder.delete()) folder.deleteOnExit(); }
    }
    private static void active(java.util.function.BooleanSupplier cancelled) throws java.io.InterruptedIOException {
        if(cancelled.getAsBoolean()) throw new java.io.InterruptedIOException("下载已取消");
    }
    private static OutputStream guard(OutputStream output,java.util.function.BooleanSupplier cancelled) throws Exception {
        if(output==null) throw new Exception("无法写入文件");
        return new java.io.FilterOutputStream(output) {
            @Override public void write(int b) throws java.io.IOException { active(cancelled); out.write(b); }
            @Override public void write(byte[] b,int off,int len) throws java.io.IOException { active(cancelled); out.write(b,off,len); }
        };
    }

}
