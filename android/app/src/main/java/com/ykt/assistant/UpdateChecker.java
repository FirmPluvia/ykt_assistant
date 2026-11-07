package com.ykt.assistant;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.util.concurrent.TimeUnit;

/** Independent transport: Rain session Cookie/Bearer never goes to the note service. */
final class UpdateChecker implements AutoCloseable {
    static final String ENDPOINT="https://share.note.youdao.com/yws/api/note/ddc0a3355abb04b3213b3bd2409a94da?sev=j1";
    private final OkHttpClient http; private final HttpUrl url;
    private Call call; private boolean closed;
    UpdateChecker() { this(HttpUrl.get(ENDPOINT),new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(10,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS).callTimeout(15,TimeUnit.SECONDS).build()); }
    UpdateChecker(HttpUrl url,OkHttpClient http) { this.url=url; this.http=http; }
    UpdateInfo fetch(String userAgent,DiagnosticLog log) throws Exception {
        long start=System.nanoTime(); if(log!=null) log.bind("startup",url.host()); if(log!=null) log.event("UPDATE","startup","REQUEST",0,0,0);
        Call current=http.newCall(new Request.Builder().url(url).get().header("User-Agent",UserAgent.validate(userAgent)).header("Accept","application/json").header("brand",UserAgent.deviceBrand()).header("systemversion",UserAgent.deviceVersion()).header("Cache-Control","no-cache").build());
        synchronized(this) { if(closed) throw new Exception("更新请求已结束"); call=current; }
        try(Response response=current.execute()) {
            if(log!=null) log.event("UPDATE","startup","RESPONSE",response.code(),0,(System.nanoTime()-start)/1000000);
            if(!response.isSuccessful()||response.body()==null) throw new Exception("更新请求失败 HTTP "+response.code());
            String raw=new String(RainApi.readLimit(response.body().byteStream(),1024*1024),java.nio.charset.StandardCharsets.UTF_8);
            UpdateInfo info=UpdateInfo.fromNote(raw); if(log!=null) log.event("UPDATE","startup",info.required(BuildConfig.VERSION_CODE)?"FORCED":info.newer(BuildConfig.VERSION_CODE)?"AVAILABLE":"CURRENT",response.code(),0,(System.nanoTime()-start)/1000000); return info;
        } catch(Exception e) { if(log!=null) log.event("UPDATE","startup","FAILED",0,0,(System.nanoTime()-start)/1000000); throw e; }
        finally { synchronized(this) { if(call==current) call=null; } }
    }
    @Override public synchronized void close() { closed=true; if(call!=null) call.cancel(); http.connectionPool().evictAll(); }
}
