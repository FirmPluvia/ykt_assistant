package com.ykt.assistant;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Each recipient queries and submits its own location round, using its own session and proof. */
final class MultiAccountLocations implements AutoCloseable {
    interface Factory{RainApi open(AccountStore.Account account)throws Exception;}
    interface Source{LocationCheckin read()throws Exception;}
    interface Progress{void update(AccountStore.Account account,String state);}
    static final class Result{final AccountStore.Account account;final boolean success;final String message;Result(AccountStore.Account a,boolean s,String m){account=a;success=s;message=m;}}
    private final AccountStore store;private final Factory factory;private final Set<String> submitted;private final AtomicBoolean cancelled=new AtomicBoolean(),started=new AtomicBoolean();private volatile RainApi current;
    MultiAccountLocations(AccountStore store,Factory factory,Set<String> submitted){this.store=store;this.factory=factory;this.submitted=submitted;}
    List<Result> run(List<AccountStore.Account> accounts,int service,String active,LocationCheckin expected,LocationCheckin.Point selected,Source source,BooleanSupplier valid,Progress progress){
        if(!started.compareAndSet(false,true))throw new IllegalStateException("操作已开始");List<Result> results=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(AccountStore.Account account:accounts){
            if(cancelled.get()||!valid.getAsBoolean())break;RainApi api=null;
            try{
                LocationCoordinates.validate(selected);if(expected==null||expected.point==null)throw new Exception("缺少本轮位置签到目标，请重新查询");
                if(account.service!=service||account.key.equals(active)||!seen.add(account.key)||store.get(account.key)==null)throw new Exception("账号已移除、服务器不一致或重复选择");
                LocationCheckin origin=source.read();if(origin.point==null&&!origin.signed)throw new Exception("当前课堂未返回可确认的位置签到目标");if(!expected.lesson.equals(origin.lesson)||origin.round!=expected.round||origin.point!=null&&!expected.point.same(origin.point)||!origin.sessionId.isEmpty()&&!origin.sessionId.equals(expected.sessionId))throw new Exception("当前课堂位置签到轮次已变化，请重新确认");
                if(cancelled.get()||!valid.getAsBoolean())break;progress.update(account,"正在校验账号…");api=factory.open(account);current=api;if(cancelled.get()){api.cancel();break;}
                JSONObject profile=api.profile();if(!account.uid.equals(profile.optString("user_id")))throw new Exception("账号身份不符，请重新登录");api.promoteSession(store.storage(account.key));
                if(cancelled.get()||!valid.getAsBoolean())break;if(!api.restoreLesson(expected.lesson,cancelled::get))throw new Exception("缺少该账号的课堂凭证，请先签到入课");
                if(cancelled.get()||!valid.getAsBoolean())break;LocationCheckin info=api.locationInfo();String key=account.key+"|"+expected.key();String message;boolean success;
                if(info.signed){success=true;message="位置签到已确认，已跳过";}
                else{info.requireAvailable();if(!expected.point.same(info.point)||!expected.sessionId.isEmpty()&&!expected.sessionId.equals(info.sessionId))throw new Exception("该账号的位置签到目标或轮次不一致");boolean pending;synchronized(submitted){pending=submitted.contains(key);}if(info.submitted||pending){success=false;message="本轮已提交，尚未确认，未重复发送";}
                    else{if(cancelled.get()||!valid.getAsBoolean()||store.get(account.key)==null)break;progress.update(account,"正在位置签到…");LocationCheckin result=api.submitServerLocation(info,selected);if(result.submitted||result.signed)synchronized(submitted){submitted.add(key);}success=result.signed;message=success?"位置签到成功":"位置已提交，尚未确认";}}
                results.add(new Result(account,success,message));progress.update(account,message);
            }catch(Exception e){if(cancelled.get()||!valid.getAsBoolean())break;String message=e instanceof RainApi.Failure?e.getMessage():e.getMessage()!=null&&(e.getMessage().contains("账号")||e.getMessage().contains("签到")||e.getMessage().contains("坐标")||e.getMessage().contains("纬度")||e.getMessage().contains("经度"))?e.getMessage():"账号校验或位置签到失败，请重新查询";results.add(new Result(account,false,message));progress.update(account,message);}
            finally{if(api!=null)api.close();current=null;}
        }
        return results;
    }
    boolean cancelled(){return cancelled.get();}
    @Override public void close(){cancelled.set(true);RainApi api=current;if(api!=null)api.cancel();}
}
