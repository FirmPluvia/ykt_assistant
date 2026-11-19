package com.ykt.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Each selected account authenticates and checks in with its own session. */
final class MultiAccountCheckin implements AutoCloseable {
    interface Factory { RainApi open(AccountStore.Account account) throws Exception; }
    interface Progress { void update(AccountStore.Account account,String state); }
    static final class Result { final AccountStore.Account account; final String message; final boolean success; Result(AccountStore.Account account,String message,boolean success) { this.account=account; this.message=message; this.success=success; } }
    private final AtomicBoolean cancelled=new AtomicBoolean(),started=new AtomicBoolean(); private RainApi current;
    private final AccountStore store; private final Factory factory;
    MultiAccountCheckin(AccountStore store,Factory factory) { this.store=store; this.factory=factory; }
    List<Result> run(List<AccountStore.Account> selected,int service,String lesson,Progress progress) {
        if(!started.compareAndSet(false,true)) throw new IllegalStateException("操作已开始"); List<Result> results=new ArrayList<>(); java.util.Set<String> seen=new java.util.HashSet<>();
        for(AccountStore.Account account:selected) {
            if(cancelled.get()) break; if(account.service!=service||!seen.add(account.key)) { results.add(new Result(account,"账号服务器不一致或重复选择",false)); continue; }
            RainApi client=null; progress.update(account,"正在校验账号…");
            try {
                client=factory.open(account); synchronized(this) { if(cancelled.get()) { client.close(); break; } current=client; }
                JSONObject profile=client.profile(); if(!account.uid.equals(profile.optString("user_id"))) throw new Exception("账号身份不符，请重新登录该账号");
                if(cancelled.get()) break; client.promoteSession(store.storage(account.key));
                boolean restored=client.restoreLesson(lesson,cancelled::get); if(cancelled.get()) break;
                if(!restored) { progress.update(account,"正在签到入课…"); client.checkin(lesson); }
                String message=restored?"已复用本机课堂凭证":"签到入课成功"; results.add(new Result(account,message,true)); progress.update(account,message);
            } catch(Exception error) { if(cancelled.get()) break; String message=error instanceof RainApi.Failure?error.getMessage():"账号校验或入课失败，请重新登录后重试"; results.add(new Result(account,message,false)); progress.update(account,message); }
            finally { if(client!=null) client.close(); synchronized(this) { if(current==client) current=null; } }
        }
        return results;
    }
    boolean cancelled() { return cancelled.get(); }
    @Override public synchronized void close() { cancelled.set(true); if(current!=null) current.cancel(); }
}
