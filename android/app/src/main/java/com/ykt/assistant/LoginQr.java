package com.ykt.assistant;

import android.graphics.Bitmap;
import com.google.zxing.common.BitMatrix;
import org.json.JSONObject;

final class LoginQr {
    static Bitmap image(RainApi api,JSONObject data) throws Exception {
        String content=RainApi.qrContent(data);
        if(!content.isEmpty()) {
            BitMatrix matrix=QrCode.encode(content,768);
            int w=matrix.getWidth(),h=matrix.getHeight();
            int[] pixels=new int[w*h];
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) pixels[y*w+x]=matrix.get(x,y)?0xff000000:0xffffffff;
            return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
        }
        // Older responses can omit qrContent; keep the official HTTPS image path as a fallback.
        try { return SlideExporter.decode(api.qrImage(data.getString("qrImage")),1024); }
        catch(Exception e) { throw new RainApi.Failure("下载登录二维码失败："+(e.getMessage()==null?"请检查网络后重试":e.getMessage()),502); }
    }
}
