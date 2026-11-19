package com.ykt.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PlainCookieStorageTest {
    private Context context; private SharedPreferences prefs;
    @Before public void setup() { context=RuntimeEnvironment.getApplication(); prefs=context.getSharedPreferences("native_session",0); prefs.edit().clear().commit(); context.getSharedPreferences("native_cookie_settings",0).edit().clear().commit(); }
    @Test public void writesPlainCookiesAndRestoresNewInstanceWithoutKeystore() throws Exception {
        SessionVault vault=new SessionVault(context); RainApi.CookieStorage store=vault.session(); String raw="[{\"raw\":\"sessionid=fixture\",\"expires\":9999999999999}]"; store.write(raw); store.authenticated(); assertEquals(raw,prefs.getString("cookies",null)); assertFalse(prefs.contains("encrypted")); assertEquals(raw,new SessionVault(context).session().read());
    }
    @Test public void migratesLegacyOnceThenOnlyReadsPlainStorage() throws Exception {
        prefs.edit().putString("encrypted","legacy-fixture").commit(); java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger(); SessionVault vault=new SessionVault(context,value -> { assertEquals("legacy-fixture",value); calls.incrementAndGet(); return "[{\"raw\":\"sessionid=old-fixture\"}]"; }); String migrated=vault.session().read(); assertTrue(migrated.contains("old-fixture")); assertEquals(migrated,prefs.getString("cookies",null)); assertFalse(prefs.contains("encrypted")); assertEquals(migrated,vault.session().read()); assertEquals(1,calls.get());
    }
    @Test public void revokedLeaseCannotOverwriteNewPlainSessionOrReactivateLogout() throws Exception {
        SessionVault vault=new SessionVault(context); RainApi.CookieStorage old=vault.session(); old.write("old"); old.authenticated(); vault.clear(); assertEquals("[]",old.read()); old.write("late"); assertFalse(prefs.contains("cookies")); try { old.authenticated(); fail(); } catch(Exception expected) {}
        RainApi.CookieStorage next=vault.session(); next.write("new"); next.authenticated(); old.write("late"); assertEquals("new",next.read());
    }
    @Test public void signOutDuringLegacyReadCannotRestorePlainCookies() throws Exception {
        prefs.edit().putString("encrypted","old-fixture").commit(); java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1); SessionVault vault=new SessionVault(context,value -> { entered.countDown(); release.await(3,java.util.concurrent.TimeUnit.SECONDS); return "old-plain-fixture"; }); java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try { java.util.concurrent.Future<String> result=executor.submit(vault.session()::read); assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS)); vault.clear(); release.countDown(); assertEquals("[]",result.get(3,java.util.concurrent.TimeUnit.SECONDS)); assertFalse(prefs.contains("cookies")); assertTrue(prefs.getBoolean("signed_out",false)); } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test public void failedLegacyMigrationDoesNotPersistDamagedPlainData() throws Exception {
        prefs.edit().putString("encrypted","damaged").commit(); SessionVault vault=new SessionVault(context,value -> { throw new Exception("bad legacy data"); }); try { vault.session().read(); fail(); } catch(Exception expected) {} assertFalse(prefs.contains("cookies")); assertTrue(prefs.contains("encrypted")); vault.clear(); assertFalse(prefs.contains("encrypted"));
    }
    @Test public void manuallySavedCookiesArePlainServerScopedAndClearedTogether() {
        CookieSettings store=new CookieSettings(context); store.save(0,"Cookie: sessionid=one"); store.save(1,"sessionid=two"); assertEquals("sessionid=one",store.read(0)); assertEquals("sessionid=two",new CookieSettings(context).read(1)); assertEquals("",store.read(2)); store.clear(); assertEquals("",store.read(0)); assertEquals("",store.read(1));
    }
}
