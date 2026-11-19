package com.ykt.assistant;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Parses a Cookie request header only. Imported values never become arbitrary HTTP headers. */
final class LoginCookies {
    static String normalize(String raw) {
        String value=raw==null?"":raw.trim(); if(value.regionMatches(true,0,"Cookie:",0,7)) value=value.substring(7).trim();
        if(value.isEmpty()) throw new IllegalArgumentException("请输入 Cookie");
        if(value.length()>32768) throw new IllegalArgumentException("Cookie 过长，请仅粘贴 Cookie 请求头内容");
        for(int n=0;n<value.length();n++) if(value.charAt(n)<32||value.charAt(n)>126) throw new IllegalArgumentException("Cookie 不能包含换行或非英文字符");
        LinkedHashMap<String,String> pairs=new LinkedHashMap<>();
        for(String part:value.split(";",-1)) {
            String pair=part.trim(); if(pair.isEmpty()) continue; int split=pair.indexOf('=');
            if(split<1) throw new IllegalArgumentException("Cookie 格式应为 name=value; name=value");
            String name=pair.substring(0,split).trim(),content=pair.substring(split+1).trim();
            if(!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")||content.contains(" ")) throw new IllegalArgumentException("Cookie 名称或内容格式不正确");
            if(name.equalsIgnoreCase("Domain")||name.equalsIgnoreCase("Path")||name.equalsIgnoreCase("Expires")||name.equalsIgnoreCase("Max-Age")||name.equalsIgnoreCase("SameSite")) throw new IllegalArgumentException("请粘贴 Cookie 请求头，不要粘贴 Set-Cookie 响应头");
            pairs.put(name,content);
        }
        if(pairs.isEmpty()) throw new IllegalArgumentException("请输入有效 Cookie");
        List<String> rows=new ArrayList<>(); for(java.util.Map.Entry<String,String> entry:pairs.entrySet()) rows.add(entry.getKey()+"="+entry.getValue()); return String.join("; ",rows);
    }
    static List<Cookie> parse(HttpUrl base,String header) {
        List<Cookie> rows=new ArrayList<>();
        for(String pair:normalize(header).split("; ")) { int split=pair.indexOf('='); Cookie.Builder builder=new Cookie.Builder().name(pair.substring(0,split)).value(pair.substring(split+1)).hostOnlyDomain(base.host()).path("/"); if(base.isHttps()) builder.secure(); rows.add(builder.build()); }
        return rows;
    }
    static final class Memory implements RainApi.CookieStorage {
        private String value="[]";
        public synchronized String read() { return value; }
        public synchronized void write(String next) { value=next; }
    }
}
