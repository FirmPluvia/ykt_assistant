package com.ykt.assistant;

import android.text.Html;
import okhttp3.HttpUrl;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Public JSON configuration extracted from the shared note, never executable instructions. */
final class UpdateInfo {
    static final String PROJECT_URL="https://github.com/FirmPluvia/ykt_assistant";
    final int versionCode; final String versionName,title,description,downloadUrl,githubUrl;
    final boolean forceUpdate;
    private UpdateInfo(JSONObject data) throws Exception {
        if(data.getInt("schemaVersion")!=1) throw new Exception("更新配置版本不支持"); Object code=data.get("versionCode");
        if(!(code instanceof Integer||code instanceof Long)||((Number)code).longValue()<1||((Number)code).longValue()>Integer.MAX_VALUE) throw new Exception("更新版本号无效"); versionCode=((Number)code).intValue();
        versionName=data.getString("versionName").trim(); if(versionName.isEmpty()||versionName.length()>80) throw new Exception("更新版本名称无效");
        Object force=data.get("forceUpdate"); if(!(force instanceof Boolean)) throw new Exception("强制更新字段无效"); forceUpdate=(Boolean)force;
        title=data.optString("title","发现新版本"); description=data.optString("description",""); if(title.length()>120||description.length()>8000) throw new Exception("更新说明过长");
        downloadUrl=url(data.optString("downloadUrl","")); githubUrl=url(data.optString("githubUrl",""));
    }
    private static String url(String raw) throws Exception { String value=raw.trim(); if(value.isEmpty()) return ""; HttpUrl url=HttpUrl.parse(value); if(url==null||!url.isHttps()||!url.username().isEmpty()||!url.password().isEmpty()) throw new Exception("更新链接不是有效 HTTPS 地址"); return url.toString(); }
    boolean newer(int current) { return versionCode>current; }
    boolean required(int current) { return newer(current)&&forceUpdate; }
    static UpdateInfo fromNote(String raw) throws Exception {
        if(raw.length()>1024*1024) throw new Exception("更新配置过大");
        String html=new JSONObject(raw).getString("content"),plain=Html.fromHtml(html,Html.FROM_HTML_MODE_LEGACY).toString();
        Matcher matcher=Pattern.compile("(?:［([^］]*)］|\\[([^\\]]*)\\])").matcher(plain); int count=0;
        while(matcher.find()&&count++<32) {
            String encoded=(matcher.group(1)!=null?matcher.group(1):matcher.group(2)).replaceAll("[\\s\\u00a0\\u200b\\ufeff]","");
            try { return new UpdateInfo(new JSONObject(new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8))); } catch(Exception ignored) {}
        }
        throw new Exception("未找到有效的 Base64 更新 JSON");
    }
}
