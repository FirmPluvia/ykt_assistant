package com.ykt.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import okhttp3.Call;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/** Direct mobile -> official Rain Classroom HTTP and WSS. No local HTTP server or relay. */
public class RainApi implements AutoCloseable {
    public static final String[] SERVICES={"雨课堂","荷塘雨课堂","长江雨课堂","黄河雨课堂"};
    public static final String[] HOSTS={"www.yuketang.cn","pro.yuketang.cn","changjiang.yuketang.cn","huanghe.yuketang.cn"};
    private static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
    public interface CookieStorage { String read() throws Exception; void write(String value) throws Exception; default void authenticated() throws Exception {} }
    public interface LessonStorage {
        JSONObject read(String host,String uid,String lesson) throws Exception;
        void write(String host,String uid,String lesson,JSONObject credentials) throws Exception;
        void remove(String host,String uid,String lesson) throws Exception;
    }
    private LessonStorage lessonStorage;
    private DiagnosticLog diagnosticLog;
    private String diagnosticRound="startup";
    void setDiagnostics(DiagnosticLog log,String round) { diagnosticLog=log; diagnosticRound=DiagnosticLog.label(round); if(log!=null) log.bind(diagnosticRound,base.host()); }
    void trace(String phase,String result,int httpStatus,int businessCode,long elapsed) { DiagnosticLog log=diagnosticLog; if(log!=null) log.event(phase,diagnosticRound,result,httpStatus,businessCode,elapsed); }
    void traceSession(String source) { traceCookies("QR_SESSION",base.resolve("/api/v3/user/login"),source); }
    private void traceCookies(String phase,HttpUrl url,String source) { DiagnosticLog log=diagnosticLog; if(log==null) return; boolean session=false,csrf=false; for(Cookie cookie:jar.loadForRequest(url)) { session|=cookie.name().equals("sessionid"); csrf|=cookie.name().equals("csrftoken"); } log.session(phase,diagnosticRound,session,csrf,source); }
    private static String authPhase(String path) {
        if(path.equals("/api/v3/user/login/pre-info")) return "QR_FETCH";
        if(path.equals("/api/v3/user/login")) return "QR_WAIT";
        if(path.equals("/v/course_meta/user_info")) return "PROFILE";
        if(path.equals("/api/v3/user/login/app")) return "ACCOUNT_LOGIN";
        if(path.equals("/api/v3/user/code/send")) return "SMS_SEND";
        if(path.equals("/api/v3/user/code/verify")) return "SMS_VERIFY";
        if(path.startsWith("/api/v3/lesson/location-checkin/")) return "LOCATION";
        return null;
    }
    void configure(LessonStorage storage) { lessonStorage=storage; }
    private volatile String userAgent="Android",deviceBrand="Android",deviceVersion="Android";
    public void setUserAgent(String value) { userAgent=UserAgent.validate(value); }
    void setDeviceInfo(String brand,String version) { deviceBrand=UserAgent.component(brand,"Android"); deviceVersion=UserAgent.component(version,"Android"); }
    public interface Events { void receive(JSONObject event); }
    public static class Failure extends IOException {
        public final int code;
        Failure(String message,int code) { super(message); this.code=code; }
    }
    private final HttpUrl base;
    private final Jar jar;
    private final CookieStorage storage;
    private final Object lifecycle=new Object();
    private final Object locationSubmitLock=new Object();
    private JSONObject locationEvent;
    private String locationEventLesson;
    private final Set<String> submittedLocations=new java.util.HashSet<>();
    private int locationRound;
    private boolean locationEnded;
    private final Set<Call> pending=ConcurrentHashMap.newKeySet();
    private final OkHttpClient http,assets,locationHttp;
    private volatile JSONObject user;
    private volatile String lessonId,bearer,lessonToken,presentationId;
    private volatile int livePage=1;
    private volatile boolean closed;
    private volatile String storageError;
    private WebSocket socket;
    private volatile int socketEpoch;
    public RainApi(int service,CookieStorage storage) throws Exception {
        this(HttpUrl.get("https://"+HOSTS[service]+"/"),storage);
    }
    RainApi(HttpUrl base,CookieStorage storage) throws Exception {
        this(base,storage,new OkHttpClient.Builder());
    }
    RainApi(HttpUrl base,CookieStorage storage,OkHttpClient.Builder transport) throws Exception {
        this.base=base; this.storage=storage; jar=new Jar(storage);
        http=transport.cookieJar(jar).followRedirects(false).followSslRedirects(false)
            .connectTimeout(20,TimeUnit.SECONDS).readTimeout(40,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).pingInterval(25,TimeUnit.SECONDS).build();
        locationHttp=http.newBuilder().retryOnConnectionFailure(false).build();
        assets=http.newBuilder().cookieJar(CookieJar.NO_COOKIES).callTimeout(30,TimeUnit.SECONDS).build();
    }
    public String host() { return base.host(); }
    public JSONObject user() { return user; }
    public String lesson() { return lessonId; }
    public String presentation() { return presentationId; }
    public int livePage() { return livePage; }
    public String storageWarning() { return storageError; }
    public boolean hasCookies() { return jar.hasCookies(); }
    public boolean isClosed() { return closed; }
    public void rememberLogin() throws Exception { if(closed) throw new Failure("登录会话已退出，请重新扫码",401); storage.authenticated(); }
    public boolean restoreLesson(String id) { return restoreLesson(id,() -> false); }
    boolean restoreLesson(String id,java.util.function.BooleanSupplier cancelled) {
        JSONObject account=user; LessonStorage cache=lessonStorage;
        if(cancelled.getAsBoolean()||closed||cache==null||account==null||account.optString("user_id").isEmpty()) return false;
        try {
            JSONObject saved=cache.read(host(),account.optString("user_id"),id);
            if(saved==null) { return false; }
            String httpToken=saved.getString("bearer"),wsToken=saved.getString("lessonToken"); if(httpToken.isEmpty()||wsToken.isEmpty()) return false;
            synchronized(this) { if(cancelled.getAsBoolean()||closed||user!=account) return false; disconnect(); synchronized(lifecycle) { if(cancelled.getAsBoolean()||closed||user!=account) return false; lessonId=id; bearer=httpToken; lessonToken=wsToken; presentationId=null; livePage=1; } }
            return true;
        } catch(Exception e) { return false; }
    }
    private void saveLesson() {
        JSONObject account=user; LessonStorage cache=lessonStorage;
        if(cache==null||account==null||account.optString("user_id").isEmpty()) return;
        try {
            String id; JSONObject data;
            synchronized(lifecycle) { if(closed||lessonId==null) return; id=lessonId; data=object("bearer",bearer,"lessonToken",lessonToken); }
            cache.write(host(),account.optString("user_id"),id,data); 
        } catch(Exception e) { storageError="课堂凭证未能保存，下次需重新入课"; }
    }
    private void removeSavedLesson(String id) {
        JSONObject account=user;
        if(lessonStorage!=null&&account!=null&&id!=null) try { lessonStorage.remove(host(),account.optString("user_id"),id); } catch(Exception ignored) {}
        
    }
    private void invalidateLesson(String expectedId,String expectedBearer) {
        JSONObject account=user;
        synchronized(this) {
            synchronized(lifecycle) {
                if(java.util.Objects.equals(expectedId,lessonId)&&java.util.Objects.equals(expectedBearer,bearer)) { lessonId=null; bearer=null; lessonToken=null; presentationId=null; disconnect(); }
            }
        }
        if(lessonStorage!=null&&account!=null&&expectedId!=null) try { lessonStorage.remove(host(),account.optString("user_id"),expectedId); } catch(Exception ignored) {}
        
    }
    private Call prepare(Request request,OkHttpClient client) throws IOException {
        synchronized(lifecycle) {
            if(closed) throw new Failure("登录会话已退出，请重新扫码",401);
            java.util.function.BooleanSupplier operation=request.tag(java.util.function.BooleanSupplier.class);
            if(operation!=null&&operation.getAsBoolean()) throw new java.io.InterruptedIOException("下载已取消");
            Call call=client.newCall(request); pending.add(call); return call;
        }
    }
    public static JSONObject object(Object... entries) throws Exception {
        JSONObject j=new JSONObject(); for(int i=0;i<entries.length;i+=2) j.put((String)entries[i],entries[i+1]); return j;
    }
    private Request.Builder headers(HttpUrl url,boolean classroom) throws Exception {
        Request.Builder r=new Request.Builder().url(url).header("User-Agent",userAgent).header("brand",deviceBrand)
            .header("buildnumber","1610").header("xtua","client=app&tag=1.3.3&platform=Android")
            .header("systemversion",deviceVersion).header("isphysicaldevice","true").header("xtbz","ykt").header("x-client","app").header("Accept","application/json");
        for(Cookie c:jar.loadForRequest(url)) {
            if(c.name().equals("csrftoken")) r.header("x-csrftoken",c.value());
            if(c.name().equals("sessionid")) r.header("sessionid",c.value());
        }
        JSONObject account=user; if(account!=null) r.header("x-uid",account.optString("user_id"));
        if(classroom) { if(bearer==null||lessonId==null) throw new Failure("请先签到进入课堂",409); r.header("Authorization","Bearer "+bearer); }
        return r;
    }
    private static final class Result {
        final JSONObject json; final String auth;
        Result(JSONObject json,String auth) { this.json=json; this.auth=auth; }
    }
    private Result call(String path,JSONObject body,boolean classroom) throws Exception { return call(path,body,classroom,null,null,null); }
    private Result call(String path,JSONObject body,boolean classroom,String expectedLesson,String expectedBearer) throws Exception { return call(path,body,classroom,expectedLesson,expectedBearer,null); }
    private Result call(String path,JSONObject body,boolean classroom,String expectedLesson,String expectedBearer,Integer expectedRound) throws Exception {
        if(closed) throw new Failure("会话已关闭，请重新登录",401);
        if(!path.startsWith("/")||path.startsWith("//")) throw new Failure("接口路径无效",400);
        HttpUrl url=base.resolve(path); if(url==null) throw new Failure("接口地址无效",400);
        Request.Builder r; String requestedLesson,requestedBearer;
        synchronized(lifecycle) { if(expectedRound!=null&&expectedRound!=locationRound) throw new Failure("位置签到轮次已变化，请重新查询",409); if(expectedLesson!=null&&(!expectedLesson.equals(lessonId)||!java.util.Objects.equals(expectedBearer,bearer))) throw new Failure("课堂已切换，请重新查询位置签到",409); requestedLesson=lessonId; requestedBearer=bearer; r=headers(url,classroom); }
        if(body!=null) r.post(RequestBody.create(body.toString(),JSON));
        Call call=prepare(r.build(),expectedRound==null?http:locationHttp);
        String phase=authPhase(path); long started=System.nanoTime(); if(phase!=null) { trace(phase,"REQUEST",0,0,0); traceCookies(phase,url,null); }
        try(Response response=call.execute()) {
            if(phase!=null) trace(phase,"RESPONSE",response.code(),0,(System.nanoTime()-started)/1000000);
            if(response.code()==401||response.code()==403) { if(classroom) { invalidateLesson(requestedLesson,requestedBearer); throw new Failure("课堂凭证已失效，请返回首页重新进入课堂",response.code()); } throw new Failure("登录已失效或没有访问权限，请重新登录",response.code()); }
            if(!response.isSuccessful()) throw new Failure("雨课堂 HTTP "+response.code(),response.code());
            if(response.body()==null) throw new Failure("雨课堂返回空数据",502);
            String raw=new String(readLimit(response.body().byteStream(),16*1024*1024),java.nio.charset.StandardCharsets.UTF_8);
            JSONObject data;
            try { data=new JSONObject(raw); } catch(Exception e) { throw new Failure("雨课堂返回的数据格式无法解析",502); }
            int code=data.optInt("code",0); if(phase!=null) trace(phase,code==0?"OK":"BUSINESS_ERROR",response.code(),code,(System.nanoTime()-started)/1000000);
            if(code!=0) {
                String message=code==50001?"扫码等待超时，请刷新二维码":code==50070?"此课堂需要老师的动态扫码签到，请使用官方雨课堂按要求入课":code==51203?"签到二维码已过期，请取得新的二维码":data.optString("msg","雨课堂业务错误 "+code);
                throw new Failure(message,code);
            }
            if(closed) throw new Failure("登录会话已退出，请重新扫码",401);
            return new Result(data,response.header("set-auth"));
        } catch(java.net.SocketTimeoutException e) { if(phase!=null) trace(phase,"READ_TIMEOUT",0,504,(System.nanoTime()-started)/1000000); throw new Failure("连接雨课堂超时，请重试",504); }
        catch(java.io.IOException e) { if(phase!=null) trace(phase,closed?"CANCELLED":e instanceof Failure?"REQUEST_REJECTED":"IO_ERROR",0,e instanceof Failure?((Failure)e).code:0,(System.nanoTime()-started)/1000000); throw e; }
        finally { pending.remove(call); }
    }
    public JSONObject profile() throws Exception {
        JSONObject p=call("/v/course_meta/user_info",null,false).json.optJSONObject("data");
        p=p==null?null:p.optJSONObject("user_profile");
        if(p==null||p.isNull("user_id")||p.optString("user_id").isEmpty()) throw new Failure("未获取有效用户资料，请重新登录",401);
        synchronized(lifecycle) { if(closed) throw new Failure("会话已关闭",401); user=p; } return p;
    }
    private final String loginDeviceId=java.util.UUID.randomUUID().toString();
    public JSONObject sendLoginCode(String phone,String ticket,String rand) throws Exception {
        String number=LoginInput.phone(phone); LoginInput.proof(ticket,rand);
        return call("/api/v3/user/code/send",object("phoneNumber",number,"email","","ticket",ticket,"rand",rand),false).json;
    }
    private JSONObject credentialBody(int type,String identity,String secret,String ticket,String rand) throws Exception {
        LoginInput.proof(ticket,rand); boolean email=type==2;
        return object("type",type,"phoneNumber",email?"":identity,"email",email?identity:"","password",type==3?"":secret,"code",type==3?secret:"","pushDeviceId",loginDeviceId,"ticket",ticket,"rand",rand);
    }
    public JSONObject passwordLogin(String account,String password,String ticket,String rand) throws Exception {
        String identity=LoginInput.account(account),secret=LoginInput.password(password);
        call("/api/v3/user/login/app",credentialBody(identity.contains("@")?2:1,identity,secret,ticket,rand),false); return profile();
    }
    public JSONObject smsLogin(String phone,String code,String ticket,String rand) throws Exception {
        String number=LoginInput.phone(phone),digits=LoginInput.code(code); LoginInput.proof(ticket,rand);
        call("/api/v3/user/code/verify",object("phoneNumber",number,"email","","code",digits),false);
        call("/api/v3/user/login/app",credentialBody(3,number,digits,ticket,rand),false); return profile();
    }
    public JSONObject qr() throws Exception {
        JSONObject d=call("/api/v3/user/login/pre-info",null,false).json.getJSONObject("data");
        if(d.optString("token").isEmpty()||(qrContent(d).isEmpty()&&d.optString("qrImage").isEmpty())) throw new Failure("登录二维码数据不完整",502);
        return d;
    }
    static String qrContent(JSONObject data) {
        Object content=data.opt("qrContent");
        return content instanceof String?((String)content).trim():"";
    }
    void awaitQrAuthorization(String token) throws Exception { require(token,"二维码 token"); call("/api/v3/user/login",object("token",token),false); }
    public JSONObject pollQr(String token) throws Exception { awaitQrAuthorization(token); return profile(); }
    private void require(String value,String name) throws Failure { if(value==null||value.trim().isEmpty()) throw new Failure("请填写"+name,400); }
    public JSONObject courses() throws Exception {
        JSONArray list=call("/v/course_meta/learning_list/",null,false).json.optJSONArray("data");
        JSONObject live=call("/api/v3/classroom/on-lesson",null,false).json.optJSONObject("data");
        if(list==null||live==null||live.optJSONArray("onLessonClassrooms")==null) throw new Failure("课程返回结构与文档不一致",502);
        return object("courses",list,"live",live.getJSONArray("onLessonClassrooms"));
    }
    /** Read-only history calls use account cookies, never classroom Bearer or check-in. */
    public JSONArray activities() throws Exception { return activities(null); }
    public JSONArray activities(String searchAfter) throws Exception {
        JSONObject body=object("size",10,"type",new JSONArray(),"beginTime",JSONObject.NULL,"endTime",JSONObject.NULL);
        if(searchAfter!=null&&!searchAfter.trim().isEmpty()) body.put("searchAfter",searchAfter);
        JSONArray list=call("/api/v3/activities/received/",body,false).json.optJSONArray("data");
        if(list==null) throw new Failure("动态列表结构与抓包不一致",502); return list;
    }
    private JSONObject report(String path,String id) throws Exception {
        require(id,"课堂 ID");
        HttpUrl url=base.resolve(path).newBuilder().addQueryParameter("lesson_id",id).addQueryParameter("front_time",String.valueOf(System.currentTimeMillis())).build();
        JSONObject result=call(url.encodedPath()+"?"+url.encodedQuery(),null,false).json.optJSONObject("data");
        if(result==null) throw new Failure("课堂报告数据不完整",502);
        if(result.has("lessonId")&&!id.equals(result.optString("lessonId"))) throw new Failure("课堂报告与所选课堂不一致",409);
        return result;
    }
    public JSONObject lessonInfo(String id) throws Exception { return report("/api/v3/classroom-report/student/lesson-info",id); }
    public JSONObject lessonDetail(String id) throws Exception { return report("/api/v3/classroom-report/student/detail",id); }
    public JSONObject lessonReview(String id) throws Exception { return report("/api/v3/classroom-report/student/review",id); }
    public void checkin(String id) throws Exception {
        require(id,"课堂 ID");
        Result result=call("/api/v3/lesson/checkin",object("source",21,"lessonId",id,"joinIfNotIn",true),false);
        String token=result.json.getJSONObject("data").optString("lessonToken");
        if(result.auth==null||result.auth.isEmpty()||token.isEmpty()) throw new Failure("签到未返回完整课堂凭证，请重试",502);
        synchronized(this) { disconnect();
            synchronized(lifecycle) {
                if(closed) throw new Failure("会话已关闭",401);
                lessonId=id; bearer=result.auth.replaceFirst("(?i)^Bearer\\s+",""); lessonToken=token; presentationId=null; livePage=1;
            }
        }
        saveLesson();
    }
    public JSONObject locationStatus() throws Exception { return call("/api/v3/lesson/location-checkin/eligibility",null,true).json; }
    private LocationCheckin locationInfo(String id,String auth) throws Exception {
        int round; synchronized(lifecycle) { round=locationRound; }
        JSONObject raw=call("/api/v3/lesson/location-checkin/eligibility",null,true,id,auth).json;
        synchronized(lifecycle) {
            if(!java.util.Objects.equals(id,lessonId)||!java.util.Objects.equals(auth,bearer)||closed) throw new Failure("课堂已变更，请重新查询位置签到",409);
            if(round!=locationRound) throw new Failure("位置签到轮次已变化，请重新查询",409);
            LocationCheckin info=LocationCheckin.parse(raw,id.equals(locationEventLesson)?locationEvent:null,id,locationRound,false);
            if(id.equals(locationEventLesson)&&locationEnded&&!info.signed) throw new Failure("本轮位置签到已经结束",409);
            return info.submitted(submittedLocations.contains(info.key()));
        }
    }
    LocationCheckin locationInfo() throws Exception {
        String id,auth; synchronized(lifecycle) { id=lessonId; auth=bearer; } if(id==null||auth==null) throw new Failure("请先进入课堂",409); return locationInfo(id,auth);
    }
    LocationCheckin submitServerLocation(LocationCheckin expected) throws Exception {
        synchronized(locationSubmitLock) {
            String id,auth; synchronized(lifecycle) { id=lessonId; auth=bearer; }
            if(expected==null||id==null||!id.equals(expected.lesson)) throw new Failure("课堂已切换，请重新查询位置签到",409);
            LocationCheckin fresh=locationInfo(id,auth); if(fresh.signed) return fresh; fresh.requireAvailable();
            if(!fresh.sameTarget(expected)) throw new Failure("签到轮次或位置已变化，请重新查询确认",409);
            if(fresh.submitted) return fresh;
            call("/api/v3/lesson/location-checkin/submit",object("lat",fresh.point.lat,"lng",fresh.point.lng,"lessonId",id,"source",54,"address",fresh.address),true,id,auth,fresh.round);
            synchronized(lifecycle) { submittedLocations.add(fresh.key()); }
            return locationInfo(id,auth);
        }
    }
    private void locationEvent(String id,JSONObject event) throws Exception {
        String op=event.optString("op"); boolean end=op.equals("locationcheckinend")||op.equals("lessonfinished")||event.optString("message").equals("lesson finished");
        if(!op.equals("locationcheckinext")&&!end) return;
        synchronized(lifecycle) {
            if(!id.equals(lessonId)||closed) return; locationRound++; locationEventLesson=id; submittedLocations.clear(); locationEnded=end; locationEvent=null;
            if(!end) { JSONObject data=event.optJSONObject("data"); if(data==null) data=event; locationEvent=object("centerLat",data.opt("centerLat"),"centerLng",data.opt("centerLng"),"address",data.optString("address","")); }
        }
    }
    public JSONObject fetchPresentation() throws Exception {
        String id=presentationId; if(id==null) throw new Failure("等待老师展示课件，或点击重新连接课堂",409);
        return fetchDeck(id,true);
    }
    /** Historical originals are read with account cookies; never join a lesson to obtain them. */
    public JSONObject originalPresentation(String lesson,String id) throws Exception {
        require(lesson,"课堂 ID"); require(id,"课件 ID");
        HttpUrl url=base.resolve("/api/v3/classroom-report/student/ppt").newBuilder()
            .addQueryParameter("lesson_id",lesson).addQueryParameter("presentationId",id).build();
        JSONObject data;
        try { data=call(url.encodedPath()+"?"+url.encodedQuery(),null,false).json.optJSONObject("data"); }
        catch(Failure e) { if(e.code==403) throw new Failure("当前账号无权查看这份课件，请确认课程权限或教师开放设置",403); throw e; }
        if(data==null||data.optJSONArray("slideList")==null) throw new Failure("原版课件未返回页面列表，请刷新重试",502);
        if(data.has("lessonId")&&!lesson.equals(data.optString("lessonId"))) throw new Failure("课件与所选课堂不一致",409);
        if(data.has("presentationId")&&!id.equals(data.optString("presentationId"))) throw new Failure("返回课件与所选课件不一致",409);
        JSONArray raw=data.getJSONArray("slideList"),slides=new JSONArray();
        for(int i=0;i<raw.length();i++) {
            JSONObject slide=raw.optJSONObject(i); if(slide==null) throw new Failure("原版课件页面数据不完整",502);
            Object visible=slide.opt("visible");
            // Match the official student view: only pages explicitly visible to students.
            if(Boolean.TRUE.equals(visible)||(visible instanceof Number&&((Number)visible).intValue()!=0)||"true".equals(visible)) slides.put(slide);
        }
        if(slides.length()==0) throw new Failure("这份课件暂无对学生开放的页面",409);
        data.put("id",id); data.put("lessonId",lesson); data.put("slides",slides); data.put("source","classroom-report/student/ppt"); return data;
    }
    private JSONObject fetchDeck(String id,boolean classroom) throws Exception {
        HttpUrl url=base.newBuilder().addPathSegments("api/v3/lesson/presentation/fetch").addQueryParameter("presentation_id",id).build();
        JSONObject p=call(url.encodedPath()+"?"+url.encodedQuery(),null,classroom).json.optJSONObject("data");
        if(p==null||p.optJSONArray("slides")==null) throw new Failure("课件数据结构与文档不一致",502);
        if(p.has("id")&&!id.equals(p.optString("id"))) throw new Failure("返回课件与所选课件不一致",409);
        p.put("id",id); return p;
    }
    public synchronized void connect(Events events) throws Exception {
        if(lessonId==null||lessonToken==null||user==null) throw new Failure("请先签到进入课堂",409);
        disconnect(); final int epoch=socketEpoch; final String id=lessonId,token=lessonToken,httpToken=bearer,uid=user.optString("user_id");
        Request request=headers(base.resolve("/wsapp/"),false).build();
        socket=http.newWebSocket(request,new WebSocketListener() {
            private boolean active() { return !closed&&epoch==socketEpoch; }
            @Override public void onOpen(WebSocket ws,Response response) {
                if(!active()) { ws.cancel(); return; }
                
                try { ws.send(object("op","hello","userid",uid,"role","student","auth",token,"lessonid",id).toString()); events.receive(object("op","connection","state","connected")); } catch(Exception ignored) {}
            }
            @Override public void onMessage(WebSocket ws,String text) {
                if(!active()) return;
                try {
                    JSONObject event=new JSONObject(text);
                    if(event.optInt("code",0)==401||event.optInt("code",0)==403) { invalidateLesson(id,httpToken); events.receive(object("op","connection","state","credentials-expired")); return; }
                    if(event.optString("op").equals("lessonfinished")||event.optString("message").equals("lesson finished")) { removeSavedLesson(id); }
                    locationEvent(id,event);
                    String pres=event.optString("presentation","");
                    JSONArray timeline=event.optJSONArray("timeline");
                    if(timeline!=null && (pres.isEmpty() || event.optString("op").equals("hello"))) for(int i=timeline.length()-1;i>=0;i--) { JSONObject e=timeline.optJSONObject(i); if(e!=null&&e.optString("type").equals("slide")&&!e.optString("pres").isEmpty()) { pres=e.optString("pres"); break; } }
                    if(!pres.isEmpty()) presentationId=pres;
                    if(event.has("slideindex")) livePage=Math.max(1,event.optInt("slideindex",1));
                    JSONObject slide=event.optJSONObject("slide"); if(slide!=null&&slide.has("si")) livePage=Math.max(1,slide.optInt("si",1));
                    events.receive(object("op",event.optString("op"),"presentationId",presentationId,"slide",livePage,"finished",event.optString("op").equals("lessonfinished")||event.optString("message").equals("lesson finished")));
                } catch(Exception ignored) {}
            }
            @Override public void onFailure(WebSocket ws,Throwable error,Response response) { if(active()) try { boolean denied=response!=null&&(response.code()==401||response.code()==403); if(denied) invalidateLesson(id,httpToken); events.receive(object("op","connection","state",denied?"credentials-expired":"error")); } catch(Exception ignored) {} }
            @Override public void onClosed(WebSocket ws,int code,String reason) { if(active()) try { if(code==1008) invalidateLesson(id,httpToken); events.receive(object("op","connection","state",code==1008?"credentials-expired":"closed")); } catch(Exception ignored) {} }
            @Override public void onClosing(WebSocket ws,int code,String reason) { ws.close(code,reason); }
        });
    }
    public synchronized void disconnect() {
        synchronized(lifecycle) { locationEvent=null; locationEventLesson=null; locationEnded=false; locationRound++; submittedLocations.clear(); }
        socketEpoch++;
        if(socket!=null) { try { socket.send(object("op","leavelesson","lessonid",lessonId).toString()); } catch(Exception ignored) {} socket.close(1000,"leave"); socket.cancel(); socket=null; }
    }
    public byte[] qrImage(String raw) throws Exception {
        if(raw.startsWith("data:image/")) { int i=raw.indexOf(','); if(i<0||!raw.substring(0,i).endsWith(";base64")||raw.length()>3*1024*1024) throw new Failure("二维码图片格式不支持",502); return java.util.Base64.getDecoder().decode(raw.substring(i+1)); }
        HttpUrl url=base.resolve(raw); return resource(url,0,true);
    }
    public byte[] slideImage(JSONObject slide) throws Exception { return slideImage(slide,null); }
    public byte[] slideImage(JSONObject slide,java.util.function.BooleanSupplier operation) throws Exception {
        Exception last=null;
        for(String key:Arrays.asList("cover","coverAlt","thumbnail")) {
            if(operation!=null&&operation.getAsBoolean()) throw new java.io.InterruptedIOException("下载已取消");
            String url=slide.optString(key); if(!url.isEmpty()) try { return resource(HttpUrl.parse(url),0,false,operation); } catch(Exception e) { last=e; }
        }
        throw last==null?new Failure("本页没有图片地址",404):last;
    }
    public void cancelOperation(java.util.function.BooleanSupplier operation) {
        synchronized(lifecycle) { for(Call call:pending) if(call.request().tag(java.util.function.BooleanSupplier.class)==operation) call.cancel(); }
    }
    private boolean sameOrigin(HttpUrl url) { return url.scheme().equals(base.scheme())&&url.host().equals(base.host())&&url.port()==base.port(); }
    static boolean allowedAsset(HttpUrl url) {
        if(url==null||!url.isHttps()||!url.username().isEmpty()||!url.password().isEmpty()||url.port()!=443) return false;
        for(String domain:Arrays.asList("yuketang.cn","xuetangx.com","qiniucdn.com","clouddn.com","qbox.me")) if(url.host().equals(domain)||url.host().endsWith("."+domain)) return true;
        return false;
    }
    // WeChat login QR images have a separate scope from presentation image downloads.
    static boolean allowedQrImage(HttpUrl url) {
        return allowedAsset(url)||(url!=null&&url.isHttps()&&url.port()==443&&url.username().isEmpty()&&url.password().isEmpty()
            &&url.host().equals("mp.weixin.qq.com")&&url.encodedPath().equals("/cgi-bin/showqrcode"));
    }
    private byte[] resource(HttpUrl url,int redirects) throws Exception { return resource(url,redirects,false); }
    private byte[] resource(HttpUrl url,int redirects,boolean qr) throws Exception { return resource(url,redirects,qr,null); }
    private byte[] resource(HttpUrl url,int redirects,boolean qr,java.util.function.BooleanSupplier operation) throws Exception {
        if(operation!=null&&operation.getAsBoolean()) throw new java.io.InterruptedIOException("下载已取消");
        if(!(qr?allowedQrImage(url):allowedAsset(url))) throw new Failure(qr?"登录二维码图片地址不受支持":"图片地址不在雨课堂与已知 CDN 域名范围",400);
        Request.Builder request=new Request.Builder().url(url).header("User-Agent",userAgent);
        if(operation!=null) request.tag(java.util.function.BooleanSupplier.class,operation);
        if(sameOrigin(url)) { StringBuilder cookies=new StringBuilder(); for(Cookie c:jar.loadForRequest(url)) { if(cookies.length()>0) cookies.append("; "); cookies.append(c.name()).append('=').append(c.value()); } if(cookies.length()>0) request.header("Cookie",cookies.toString()); }
        Call call=prepare(request.build(),assets); long imageStarted=System.nanoTime();
        try(Response response=call.execute()) {
            if(qr) trace("QR_IMAGE","RESPONSE",response.code(),0,(System.nanoTime()-imageStarted)/1000000);
            if(Arrays.asList(301,302,303,307,308).contains(response.code())) {
                String next=response.header("Location"); if(next==null||redirects>=3) throw new Failure("课件图片重定向异常",502);
                return resource(url.resolve(next),redirects+1,qr,operation);
            }
            if(!response.isSuccessful()||response.body()==null) throw new Failure("图片读取失败 HTTP "+response.code(),502);
            String mime=response.header("Content-Type","").split(";")[0];
            if(!Arrays.asList("image/jpeg","image/png","image/webp","image/gif").contains(mime)) throw new Failure("图片格式不支持",502);
            return readLimit(response.body().byteStream(),15*1024*1024);
        } finally { pending.remove(call); }
    }
    static byte[] readLimit(InputStream input,int limit) throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int n;
        while((n=input.read(buffer))!=-1) { if(output.size()+n>limit) throw new Failure("响应过大，请分次下载",413); output.write(buffer,0,n); } return output.toByteArray();
    }
    /** Cancels network work without waiting for cookie encryption or persistence. */
    public void cancel() {
        synchronized(lifecycle) { closed=true; }
        for(Call call:pending) call.cancel();
        http.dispatcher().cancelAll();
        disconnect(); user=null; lessonId=null; bearer=null; lessonToken=null; presentationId=null;
        synchronized(jar) { jar.cookies.clear(); jar.revision++; }
    }
    @Override public void close() {
        cancel(); http.connectionPool().evictAll();
        synchronized(jar) { jar.cookies.clear(); }
    }
    public void clearCookies() throws Exception { jar.clear(); }
    final class Jar implements CookieJar {
        private final CookieStorage storage;
        private final Map<String,Cookie> cookies=new LinkedHashMap<>();
        Jar(CookieStorage storage) throws Exception {
            this.storage=storage;
            JSONArray saved=new JSONArray(storage.read());
            for(int i=0;i<saved.length();i++) {
                JSONObject item=saved.getJSONObject(i); Cookie c=Cookie.parse(base,item.getString("raw"));
                if(c!=null&&item.optLong("expires",Long.MAX_VALUE)>System.currentTimeMillis()) cookies.put(key(c),c);
            }
        }
        synchronized boolean hasCookies() { return !cookies.isEmpty(); }
        private String key(Cookie c) { return c.domain()+"|"+c.path()+"|"+c.name(); }
        private final Object persistLock=new Object();
        private long revision;
        @Override public void saveFromResponse(HttpUrl url,List<Cookie> values) {
            String snapshot; long version;
            synchronized(this) {
                if(closed) return;
                for(Cookie c:values) { if(c.expiresAt()<=System.currentTimeMillis()) cookies.remove(key(c)); else cookies.put(key(c),c); }
                version=++revision;
                try { snapshot=serialized(); } catch(Exception e) { storageError="会话数据保存失败"; return; }
            }
            synchronized(persistLock) {
                synchronized(this) { if(closed||version!=revision) return; }
                try { storage.write(snapshot); } catch(Exception e) { storageError="会话仅保留在内存：本机加密存储失败"; trace("SESSION_STORE","FAILED",0,0,0); }
            }
        }
        @Override public synchronized List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> list=new ArrayList<>(); if(closed) return list; for(Cookie c:cookies.values()) if(c.matches(url)&&c.expiresAt()>System.currentTimeMillis()) list.add(c); return list;
        }
        private String serialized() throws Exception {
            JSONArray saved=new JSONArray(); for(Cookie c:cookies.values()) saved.put(object("raw",c.toString(),"expires",c.expiresAt())); return saved.toString();
        }
        synchronized void clear() throws Exception { cookies.clear(); storage.write("[]"); }
    }
}
