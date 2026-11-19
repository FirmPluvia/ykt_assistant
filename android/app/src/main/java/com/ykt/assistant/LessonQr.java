package com.ykt.assistant;

import okhttp3.HttpUrl;
import java.util.Arrays;

/** Classroom QR payload only. Never follows a scanned URL or treats course/classroom IDs as lesson IDs. */
final class LessonQr {
    static final class Payload {
        final String lessonId,url;
        private Payload(String id,String url) { lessonId=id; this.url=url; }
    }
    static boolean validId(String id) { return id!=null&&id.matches("[0-9]{1,20}")&&!id.matches("0+"); }
    static Payload parse(String raw,int service) {
        String value=raw==null?"":raw.trim(); if(value.isEmpty()||value.length()>8192) throw new IllegalArgumentException("二维码内容为空或过长，请重新扫描课堂二维码");
        for(int i=0;i<value.length();i++) if(value.charAt(i)<32) throw new IllegalArgumentException("二维码内容格式不正确");
        if(validId(value)) return new Payload(value,null);
        HttpUrl url=HttpUrl.parse(value); if(url==null||!url.isHttps()||!url.username().isEmpty()||!url.password().isEmpty()||url.port()!=443) throw new IllegalArgumentException("不是支持的雨课堂课堂二维码");
        int target=Arrays.asList(RainApi.HOSTS).indexOf(url.host()); if(target<0) throw new IllegalArgumentException("二维码地址不属于雨课堂");
        if(service<0||service>=RainApi.HOSTS.length||target!=service) throw new IllegalArgumentException("二维码属于"+RainApi.SERVICES[target]+"，请先切换到对应服务器的账号");
        String path=url.encodedPath(); if(path.contains("login")||path.contains("authorize")||path.contains("wechat")) throw new IllegalArgumentException("这是登录或授权二维码，请扫描课堂二维码");
        if(path.startsWith("/api/v3/lesson/check-in/")) return new Payload(null,value);
        String id=null; int count=0;
        for(String key:new String[]{"lessonId","lesson_id","lessonid"}) for(String candidate:url.queryParameterValues(key)) { count++; id=candidate; }
        if(count>0) { if(count!=1||!validId(id)) throw new IllegalArgumentException("二维码中的课堂 ID 无效或不唯一"); return new Payload(id,null); }
        if(path.startsWith("/v/lesson/")||path.startsWith("/m/v2/lesson/")||path.startsWith("/lesson/")) return new Payload(null,value);
        throw new IllegalArgumentException("未识别到课堂信息，请扫描课堂签到二维码");
    }
}
