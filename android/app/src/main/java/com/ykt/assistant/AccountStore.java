package com.ykt.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Explicitly accepted accounts. Plain Cookies remain private and never contain passwords. */
final class AccountStore {
    static final class Account {
        final String key,uid,name,school,cookies; final int service;
        Account(String key,JSONObject data) throws Exception { this.key=key; service=data.getInt("service"); if(service<0||service>=RainApi.HOSTS.length) throw new Exception("账号服务器无效"); uid=data.getString("uid"); name=data.optString("name","同学"); school=data.optString("school",""); cookies=data.optString("cookies","[]"); }
    }
    private static final Object LOCK=new Object();
    private final SharedPreferences prefs;
    AccountStore(Context context) { prefs=context.getSharedPreferences("saved_accounts",Context.MODE_PRIVATE); }
    static String key(int service,String uid) { return service+"|"+uid; }
    private JSONObject records() throws Exception { return new JSONObject(prefs.getString("accounts","{}")); }
    private void save(JSONObject records) throws Exception { if(!prefs.edit().putString("accounts",records.toString()).commit()) throw new Exception("账号保存失败，请重试"); }
    List<Account> list() { synchronized(LOCK) { List<Account> result=new ArrayList<>(); try { JSONObject data=records(); java.util.Iterator<String> keys=data.keys(); while(keys.hasNext()) { String key=keys.next(); try { result.add(new Account(key,data.getJSONObject(key))); } catch(Exception ignored) {} } } catch(Exception ignored) {} return result; } }
    Account get(String key) { for(Account account:list()) if(account.key.equals(key)) return account; return null; }
    String remember(int service,JSONObject profile,String cookies) throws Exception {
        String uid=profile.optString("user_id",""); if(uid.isEmpty()) throw new Exception("账号资料不完整"); String key=key(service,uid);
        synchronized(LOCK) { JSONObject data=records(); data.put(key,RainApi.object("service",service,"uid",uid,"name",profile.optString("name","同学"),"school",profile.optString("school",""),"cookies",cookies)); save(data); } return key;
    }
    String active() { return prefs.getString("active",""); }
    void active(String key) { prefs.edit().putString("active",key).apply(); }
    void remove(String key) throws Exception { synchronized(LOCK) { JSONObject data=records(); data.remove(key); save(data); prefs.edit().putLong("generation:"+key,prefs.getLong("generation:"+key,0)+1).apply(); if(active().equals(key)) active(""); } }
    RainApi.CookieStorage storage(String key) {
        final long lease=prefs.getLong("generation:"+key,0);
        return new RainApi.CookieStorage() {
            public String read() { synchronized(LOCK) { Account account=get(key); return lease==prefs.getLong("generation:"+key,0)&&account!=null?account.cookies:"[]"; } }
            public void write(String cookies) throws Exception { synchronized(LOCK) { if(lease!=prefs.getLong("generation:"+key,0)) return; JSONObject data=records(); JSONObject account=data.optJSONObject(key); if(account==null) return; account.put("cookies",cookies); save(data); } }
        };
    }
    RainApi.CookieStorage mirror(RainApi.CookieStorage active,String key) {
        RainApi.CookieStorage saved=storage(key);
        return new RainApi.CookieStorage() {
            public String read() throws Exception { return active.read(); }
            public void write(String value) throws Exception { active.write(value); saved.write(value); }
            public void authenticated() throws Exception { active.authenticated(); }
        };
    }
}
