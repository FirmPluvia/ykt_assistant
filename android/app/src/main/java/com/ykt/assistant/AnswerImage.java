package com.ykt.assistant;

import android.content.Context;
import android.net.Uri;
import android.graphics.*;
import android.media.ExifInterface;
import java.io.*;
import java.util.UUID;
import org.json.JSONObject;

/** Bounded, re-encoded in-memory answer photo. No source filename, location metadata or URI is uploaded. */
final class AnswerImage {
    static final int MAX_COUNT=6,MAX_BYTES=4*1024*1024;
    final String id,url;private final byte[] jpeg;
    AnswerImage(String id,byte[] jpeg,String url){this.id=id;this.jpeg=jpeg.clone();this.url=url;}
    byte[] bytes(){return jpeg.clone();}
    AnswerImage uploaded(String url){return new AnswerImage(id,jpeg,url);}
    JSONObject payload()throws Exception{if(url==null||!url.startsWith("https://qn-scd1.yuketang.cn/"))throw new Exception("图片尚未上传完成");return RainApi.object("pic",url,"thumb",url+"?imageView2/2/w/568");}
    static AnswerImage read(Context context,Uri uri)throws Exception{try(InputStream stream=context.getContentResolver().openInputStream(uri)){if(stream==null)throw new Exception("无法读取所选图片");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];for(int n;(n=stream.read(buf))!=-1;){if(out.size()+n>16*1024*1024)throw new Exception("所选图片超过 16 MB，请缩小后重试");out.write(buf,0,n);}return prepare(out.toByteArray());}}
    static AnswerImage prepare(byte[] source)throws Exception{
        if(source==null||source.length==0||source.length>16*1024*1024)throw new Exception("图片为空或超过 16 MB");Bitmap decoded=SlideExporter.decode(source,2048),rotated=decoded;int orientation=1;try{orientation=new ExifInterface(new ByteArrayInputStream(source)).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(Exception ignored){}
        Matrix matrix=new Matrix();switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(270);matrix.postScale(-1,1);break;case 8:matrix.setRotate(270);break;}
        if(!matrix.isIdentity())rotated=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),matrix,true);
        float factor=Math.min(1,2048f/Math.max(rotated.getWidth(),rotated.getHeight()));int width=Math.max(1,Math.round(rotated.getWidth()*factor)),height=Math.max(1,Math.round(rotated.getHeight()*factor));Bitmap output=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(output);canvas.drawColor(Color.WHITE);canvas.drawBitmap(rotated,null,new Rect(0,0,width,height),new Paint(Paint.FILTER_BITMAP_FLAG));ByteArrayOutputStream data=new ByteArrayOutputStream();output.compress(Bitmap.CompressFormat.JPEG,88,data);if(data.size()>MAX_BYTES){data.reset();output.compress(Bitmap.CompressFormat.JPEG,65,data);}output.recycle();if(rotated!=decoded)rotated.recycle();decoded.recycle();if(data.size()>MAX_BYTES)throw new Exception("压缩后的图片超过 4 MB，请缩小后重试");return new AnswerImage(UUID.randomUUID().toString(),data.toByteArray(),null);
    }
    Bitmap thumbnail(){BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,options);options.inSampleSize=Math.max(1,Math.max(options.outWidth,options.outHeight)/160);options.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,options);}
}
