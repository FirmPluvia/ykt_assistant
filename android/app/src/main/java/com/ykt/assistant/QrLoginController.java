package com.ykt.assistant;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.InterruptedIOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Owns one QR/token/cookie client per round. Every callback is bound to that exact round. */
final class QrLoginController implements AutoCloseable {
    enum State { FETCHING, READY, EXPIRED, BACKOFF, AUTHORIZED, PROFILE_RETRY, PROFILE_FAILED, FAILED }
    interface Factory { RainApi create(int service,String round) throws Exception; }
    interface Listener {
        void state(State state,Bitmap image,String message,RainApi owner);
        void signedIn(RainApi owner,JSONObject profile);
    }
    interface Pause { void waitFor(long millis) throws InterruptedException; }
    interface Renderer { Bitmap image(RainApi client,JSONObject data) throws Exception; }
    private final Factory factory; private final Listener listener; private final DiagnosticLog log;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Pause pause; private final Renderer renderer;
    private Future<?> job; private long generation,roundNumber; private RainApi current;
    private boolean closed,authorized,profileRunning;
    private String round="none";
    QrLoginController(Factory factory,Listener listener,DiagnosticLog log) { this(factory,listener,log,Thread::sleep,LoginQr::image); }
    QrLoginController(Factory factory,Listener listener,DiagnosticLog log,Pause pause,Renderer renderer) { this.factory=factory; this.listener=listener; this.log=log; this.pause=pause; this.renderer=renderer; }
    synchronized void start(int service) {
        if(closed) return; generation++; roundNumber++; authorized=false; profileRunning=false;
        if(current!=null) { current.cancel(); current=null; } if(job!=null) job.cancel(true);
        long run=generation;
        Runnable clear=() -> { synchronized(QrLoginController.this) { if(closed||generation!=run) return; } listener.state(State.FETCHING,null,"旧码已停用，正在获取新的二维码…",null); };
        if(main.getLooper().isCurrentThread()) clear.run(); else main.post(clear);
        if(!closed) job=worker.submit(() -> rounds(run,service));
    }
    private synchronized boolean active(long run,long number) { return !closed&&run==generation&&number==roundNumber; }
    private void note(String phase,String outcome,int code) { if(log!=null) log.event(phase,round,outcome,0,code,0); }
    private void post(long run,long number,State state,Bitmap bitmap,String message,RainApi owner) { main.post(() -> { if(active(run,number)) listener.state(state,bitmap,message,owner); }); }
    private void rounds(long run,int service) {
        int failures=0;
        while(!Thread.currentThread().isInterrupted()) {
            final long number; synchronized(this) { if(closed||generation!=run) return; number=++roundNumber; round=UUID.randomUUID().toString(); }
            RainApi client=null; long waitStarted=0;
            try {
                post(run,number,State.FETCHING,null,"正在获取新的登录二维码…",null); note("QR_ROUND","START",0);
                synchronized(this) { if(!active(run,number)) return; client=factory.create(service,round); current=client; }
                JSONObject data=client.qr(); String token=data.getString("token"); client.traceSession(RainApi.qrContent(data).isEmpty()?"OFFICIAL_IMAGE":"LOCAL_CONTENT"); client.trace("QR_IMAGE","REQUEST",0,0,0); Bitmap image=renderer.image(client,data); client.trace("QR_IMAGE","OK",0,0,0);
                if(!active(run,number)) return;
                post(run,number,State.READY,image,"请使用微信或官方雨课堂扫码授权",client);
                waitStarted=System.nanoTime(); client.awaitQrAuthorization(token);
                if(!active(run,number)) return;
                synchronized(this) { if(!active(run,number)) return; authorized=true; profileRunning=true; }
                post(run,number,State.AUTHORIZED,null,"扫码授权成功，正在获取账号信息…",client); note("QR_AUTH","AUTHORIZED",0);
                profile(run,number,client); return;
            } catch(Exception error) {
                if(!active(run,number)||Thread.currentThread().isInterrupted()) return;
                int code=error instanceof RainApi.Failure?((RainApi.Failure)error).code:0;
                long waited=waitStarted==0?0:(System.nanoTime()-waitStarted)/1000000;
                note("QR_ROUND",code==50001?"SCAN_TIMEOUT":error instanceof InterruptedIOException?"NETWORK_TIMEOUT":"FAILED",code);
                if(client!=null) { client.cancel(); synchronized(this) { if(current==client) current=null; } }
                post(run,number,State.EXPIRED,null,"二维码已失效或等待已中断，请勿继续扫描旧码",null);
                if(code==50001&&waited>=5000) failures=0; else failures++;
                if(failures>=5) { post(run,number,State.FAILED,null,"二维码连续获取或等待失败，请检查网络后手动刷新。错误阶段："+(waitStarted==0?"获取二维码":"等待授权")+"；"+display(error),null); return; }
                long delay=code==50001&&waited>=5000?1000:Math.min(15_000L,1000L<<Math.min(4,failures));
                try { pause.waitFor(250); if(!active(run,number)) return; post(run,number,State.BACKOFF,null,"旧码已停用，"+Math.max(1,delay/1000)+" 秒后重新获取…",null); pause.waitFor(delay); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
        }
    }
    private void profile(long run,long number,RainApi client) {
        try {
            for(int attempt=0;attempt<3;attempt++) {
                if(!active(run,number)) return;
                try {
                    JSONObject profile=client.profile(); if(!active(run,number)) return;
                    main.post(() -> {
                        synchronized(QrLoginController.this) { if(!active(run,number)||current!=client) return; current=null; authorized=false; profileRunning=false; }
                        note("QR_AUTH","ACCEPTED",0); listener.signedIn(client,profile);
                    }); return;
                } catch(Exception error) {
                    if(!active(run,number)||Thread.currentThread().isInterrupted()) return;
                    int code=error instanceof RainApi.Failure?((RainApi.Failure)error).code:0; note("PROFILE","RETRY",code);
                    if(attempt==2) { post(run,number,State.PROFILE_FAILED,null,"扫码已授权，但获取账号信息失败；可重试账号信息，无需重新扫码。"+display(error),client); return; }
                    post(run,number,State.PROFILE_RETRY,null,"扫码已授权，正在重试获取账号信息（"+(attempt+1)+"/3）…",client);
                    try { pause.waitFor(500L<<attempt); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                }
            }
        } finally { synchronized(this) { if(run==generation&&number==roundNumber) profileRunning=false; } }
    }
    synchronized void retryProfile() {
        if(closed||!authorized||current==null||profileRunning) return;
        profileRunning=true; long run=generation,number=roundNumber; RainApi client=current;
        post(run,number,State.PROFILE_RETRY,null,"正在使用已授权会话重新获取账号信息…",client); job=worker.submit(() -> profile(run,number,client));
    }
    private static String display(Exception error) { return error instanceof RainApi.Failure?"（错误码 "+((RainApi.Failure)error).code+"）":"（网络或本机会话异常）"; }
    @Override public synchronized void close() { if(closed) return; note("QR_ROUND","CANCELLED",0); closed=true; generation++; authorized=false; if(current!=null) current.cancel(); current=null; if(job!=null) job.cancel(true); worker.shutdownNow(); }
}
