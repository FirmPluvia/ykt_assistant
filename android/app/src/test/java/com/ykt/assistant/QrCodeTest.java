package com.ykt.assistant;

import org.junit.Test;
import static org.junit.Assert.*;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

public class QrCodeTest {
    @Test public void locallyGeneratedQrDecodesToOfficialContentVerbatim() throws Exception {
        // This is content, not a URL fetched by the application (the official content uses http).
        String content="http://weixin.qq.com/q/test+ABC_123?value=a%2Bb&x=1";
        BitMatrix matrix=QrCode.encode(content,768);
        int w=matrix.getWidth(),h=matrix.getHeight(); int[] pixels=new int[w*h];
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) pixels[y*w+x]=matrix.get(x,y)?0xff000000:0xffffffff;
        Result result=new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w,h,pixels))));
        assertEquals(content,result.getText());
        assertFalse(matrix.get(0,0)); assertFalse(matrix.get(w-1,h-1));
    }
    @Test public void qrDoesNotEncodeEmptyOrExcessivelyLargeValues() throws Exception {
        for(String content:new String[]{null,"",new String(new char[4097]).replace('\0','x')}) {
            try { QrCode.encode(content,768); fail(); } catch(RainApi.Failure e) { assertEquals(502,e.code); }
        }
    }
}
