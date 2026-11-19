package com.ykt.assistant;

import android.content.Context;
import android.content.SharedPreferences;

/** Legacy plain Cookie drafts; read for compatibility and cleared on sign-out. New copy actions never write here. */
final class CookieSettings {
    private final SharedPreferences prefs;
    CookieSettings(Context context) { prefs=context.getSharedPreferences("native_cookie_settings",Context.MODE_PRIVATE); }
    String read(int service) { return prefs.getString(key(service),""); }
    void save(int service,String value) { if(!prefs.edit().putString(key(service),LoginCookies.normalize(value)).commit()) throw new IllegalStateException("Cookie 保存失败，请重试"); }
    void clear() { prefs.edit().clear().apply(); }
    private String key(int service) { if(service<0||service>=RainApi.HOSTS.length) throw new IllegalArgumentException("雨课堂版本无效"); return RainApi.HOSTS[service]; }
}
