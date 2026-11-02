package com.ykt.assistant;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.EnumMap;
import java.util.Map;

/** Encodes the official qrContent verbatim; a login token is never used as QR content. */
final class QrCode {
    static BitMatrix encode(String content,int size) throws Exception {
        if(content==null||content.isEmpty()||content.length()>4096) throw new RainApi.Failure("二维码内容无效或过长，请重新获取",502);
        Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET,"UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN,4);
        return new QRCodeWriter().encode(content,BarcodeFormat.QR_CODE,size,size,hints);
    }
}
