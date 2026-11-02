package com.ykt.assistant;

import android.content.SharedPreferences;
import android.os.Build;

/** User-provided compatibility template, with real device metadata and safe HTTP header validation. */
final class UserAgent {
    static final String KEY="custom_user_agent";
    private UserAgent() {}
    static String deviceDefault() { return defaultFor(Build.VERSION.RELEASE,Build.MODEL,Build.ID); }
    static String defaultFor(String androidVersion,String model,String build) {
        return "Mozilla/5.0 (Linux; Android "+component(androidVersion,"unknown")+"; "+component(model,"Android Device")+" Build/"+component(build,"unknown")+"; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/153.0.8010.36 Mobile Safari/537.36 flutterApp Mobile flutterApp";
    }
    static String current(SharedPreferences prefs) { String custom=prefs.getString(KEY,null); if(custom!=null) try { return validate(custom); } catch(IllegalArgumentException ignored) {} return deviceDefault(); }
    static boolean isCustom(SharedPreferences prefs) { String custom=prefs.getString(KEY,null); if(custom==null) return false; try { validate(custom); return true; } catch(IllegalArgumentException e) { return false; } }
    static String component(String value,String fallback) {
        if(value==null) return fallback; StringBuilder safe=new StringBuilder();
        for(int i=0;i<value.length()&&safe.length()<128;i++) { char c=value.charAt(i); if(c>=32&&c<=126&&c!='('&&c!=')'&&c!=';') safe.append(c); }
        String result=safe.toString().trim(); return result.isEmpty()?fallback:result;
    }
    static String deviceBrand() { return component(Build.BRAND,component(Build.MANUFACTURER,"Android")); }
    static String deviceVersion() { return component(Build.VERSION.RELEASE,"unknown"); }
    static String validate(String raw) {
        if(raw==null||raw.length()>2048) throw new IllegalArgumentException("User-Agent 不能为空，且最多 2048 个字符");
        for(int i=0;i<raw.length();i++) if(raw.charAt(i)<32||raw.charAt(i)>126) throw new IllegalArgumentException("User-Agent 仅支持可打印的英文字符，不能包含换行或控制字符");
        String value=raw.trim(); if(value.isEmpty()) throw new IllegalArgumentException("请输入 User-Agent，或恢复设备默认值"); return value;
    }
}
