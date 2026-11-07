package com.ykt.assistant;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Typed diagnostic records only. No arbitrary strings, bodies, cookies, passwords or QR tokens. */
final class DiagnosticLog implements AutoCloseable {
    private final File root;
    private final java.util.Map<String,String> servers=new java.util.concurrent.ConcurrentHashMap<>();
    void bind(String round,String host) { if(java.util.Arrays.asList("www.yuketang.cn","pro.yuketang.cn","changjiang.yuketang.cn","huanghe.yuketang.cn","share.note.youdao.com").contains(host)) { if(servers.size()>512) servers.clear(); servers.put(round(round),host); } }
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private static final long LIMIT=1024*1024,KEEP=7L*24*60*60*1000;
    DiagnosticLog(Context context) { this(new File(LessonCache.privateRoot(context),"logs")); }
    DiagnosticLog(File root) { this.root=root; }
    static String label(String value) { return value!=null&&value.matches("[A-Za-z0-9_.:/-]{1,96}")?value:"REDACTED"; }
    void event(String phase,String round,String outcome,int http,int code,long millis) { record(phase,round,outcome,http,code,millis,null,null,null); }
    void session(String phase,String round,boolean session,boolean csrf,String imageSource) { record(phase,round,"SESSION_METADATA",0,0,0,session,csrf,imageSource); }
    private static String phase(String value) { return java.util.Arrays.asList("APP","UPDATE","HUMAN","LOCATION","QR_CLIENT","QR_IMAGE","SESSION_STORE","QR_FETCH","QR_WAIT","PROFILE","ACCOUNT_LOGIN","SMS_SEND","SMS_VERIFY","QR_SESSION","QR_ROUND","QR_AUTH").contains(value)?value:"REDACTED"; }
    private static String round(String value) { return value!=null&&(value.equals("startup")||value.equals("none")||value.matches("(?:client-)?[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))?value:"REDACTED"; }
    private static String outcome(String value) { return java.util.Arrays.asList("OPEN","REQUEST","RESPONSE","OK","BUSINESS_ERROR","READ_TIMEOUT","CANCELLED","REQUEST_REJECTED","IO_ERROR","SESSION_METADATA","START","SCAN_TIMEOUT","NETWORK_TIMEOUT","FAILED","AUTHORIZED","ACCEPTED","RETRY","FORCED","AVAILABLE","CURRENT","EXIT_TO_PROJECT").contains(value)?value:"REDACTED"; }
    private void record(String phase,String round,String outcome,int http,int code,long millis,Boolean session,Boolean csrf,String source) {
        if(io.isShutdown()) return;
        final String p=phase(phase),r=round(round),o=outcome(outcome),s=source==null?null:source.equals("LOCAL_CONTENT")||source.equals("OFFICIAL_IMAGE")?source:"REDACTED"; final long now=System.currentTimeMillis(); final String service=servers.getOrDefault(r,"UNKNOWN");
        try { io.execute(() -> {
            try {
                File folder=new File(root,new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date(now))); if(!folder.isDirectory()&&!folder.mkdirs()) return;
                JSONObject line=new JSONObject().put("time",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.ROOT).format(new Date(now))).put("version",BuildConfig.VERSION_NAME).put("build",BuildConfig.VERSION_CODE).put("phase",p).put("round",r).put("outcome",o);
                line.put("server",service); if(http>0) line.put("http",http); if(code!=0) line.put("code",code); if(millis>=0) line.put("elapsed_ms",millis);
                if(session!=null) line.put("session_cookie_present",session); if(csrf!=null) line.put("csrf_cookie_present",csrf); if(s!=null) line.put("qr_source",s);
                File file=new File(folder,"app.log"),previous=new File(folder,"app.previous.log"); if(file.length()>LIMIT) { Files.deleteIfExists(previous.toPath()); Files.move(file.toPath(),previous.toPath()); }
                try(FileOutputStream out=new FileOutputStream(file,true)) { out.write((line+"\n").getBytes(StandardCharsets.UTF_8)); }
                File[] days=root.listFiles(); if(days!=null) for(File day:days) if(day.isDirectory()&&day.getName().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                    Date date=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).parse(day.getName()); if(date!=null&&now-date.getTime()>KEEP) { Files.deleteIfExists(new File(day,"app.log").toPath()); Files.deleteIfExists(new File(day,"app.previous.log").toPath()); day.delete(); }
                }
            } catch(Exception ignored) { /* Diagnostics must never break login. */ }
        }); } catch(java.util.concurrent.RejectedExecutionException ignored) {}
    }
    byte[] today() throws Exception {
        return io.submit(() -> {
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            File day=new File(root,new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date()));
            for(String name:new String[]{"app.previous.log","app.log"}) { File file=new File(day,name); if(file.isFile()&&file.length()<LIMIT*2) out.write(Files.readAllBytes(file.toPath())); }
            return out.toByteArray();
        }).get();
    }
    @Override public void close() { io.shutdown(); }
}
