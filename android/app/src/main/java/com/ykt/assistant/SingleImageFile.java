package com.ykt.assistant;

import android.content.Context;
import android.net.Uri;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Preserve the actual downloaded file, including animation and its original encoding. */
final class SingleImageFile {
    final byte[] bytes;
    final String mime,extension;
    private SingleImageFile(byte[] bytes,String mime,String extension) { this.bytes=bytes; this.mime=mime; this.extension=extension; }
    static SingleImageFile from(byte[] bytes) throws IOException {
        if(bytes==null) throw new IOException("没有可保存的图片");
        if(bytes.length>=8&&Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10})) return new SingleImageFile(bytes,"image/png","png");
        if(bytes.length>=3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255) return new SingleImageFile(bytes,"image/jpeg","jpg");
        if(bytes.length>=6) { String type=new String(bytes,0,6,StandardCharsets.US_ASCII); if(type.equals("GIF87a")||type.equals("GIF89a")) return new SingleImageFile(bytes,"image/gif","gif"); }
        if(bytes.length>=12&&new String(bytes,0,4,StandardCharsets.US_ASCII).equals("RIFF")&&new String(bytes,8,4,StandardCharsets.US_ASCII).equals("WEBP")) return new SingleImageFile(bytes,"image/webp","webp");
        throw new IOException("返回内容不是支持的图片文件，请刷新本页后重试");
    }
    void save(Context context,Uri destination,BooleanSupplier cancelled,IntConsumer progress) throws Exception {
        if(cancelled.getAsBoolean()) throw new InterruptedIOException("保存已取消");
        try(OutputStream output=context.getContentResolver().openOutputStream(destination,"w")) {
            if(output==null) throw new IOException("无法打开图片保存位置");
            for(int offset=0;offset<bytes.length;) {
                if(cancelled.getAsBoolean()) throw new InterruptedIOException("保存已取消");
                int count=Math.min(32*1024,bytes.length-offset); output.write(bytes,offset,count); offset+=count; progress.accept((int)(offset*100L/bytes.length));
            }
            output.flush(); if(cancelled.getAsBoolean()) throw new InterruptedIOException("保存已取消");
        }
    }
}
