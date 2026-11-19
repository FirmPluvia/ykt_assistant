package com.ykt.assistant;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class AccountStoreTest {
    private AccountStore store;
    @Before public void setup() { RuntimeEnvironment.getApplication().getSharedPreferences("saved_accounts",0).edit().clear().commit(); store=new AccountStore(RuntimeEnvironment.getApplication()); }
    @Test public void multipleAccountsSurviveRestartAndSeparateServersAndUserIds() throws Exception {
        String first=store.remember(0,new JSONObject("{\"user_id\":\"a\",\"name\":\"同学甲\"}"),"cookies-a"); String second=store.remember(0,new JSONObject("{\"user_id\":\"b\"}"),"cookies-b"); String other=store.remember(1,new JSONObject("{\"user_id\":\"a\"}"),"other-server"); store.active(second); AccountStore restored=new AccountStore(RuntimeEnvironment.getApplication()); assertEquals(3,restored.list().size()); assertEquals("cookies-a",restored.get(first).cookies); assertEquals("cookies-b",restored.get(second).cookies); assertEquals("other-server",restored.get(other).cookies); assertEquals(second,restored.active());
    }
    @Test public void cookieUpdatesStayBoundToAccountEvenWhenActiveAccountChanges() throws Exception {
        String first=store.remember(0,new JSONObject("{\"user_id\":\"a\"}"),"old-a"),second=store.remember(0,new JSONObject("{\"user_id\":\"b\"}"),"old-b"); RainApi.CookieStorage a=store.storage(first); store.active(second); a.write("fresh-a"); assertEquals("fresh-a",store.get(first).cookies); assertEquals("old-b",store.get(second).cookies); assertEquals(second,store.active());
    }
    @Test public void removedAccountCannotBeRecreatedOrOverwrittenByStaleClient() throws Exception {
        String key=store.remember(0,new JSONObject("{\"user_id\":\"a\"}"),"old"); RainApi.CookieStorage old=store.storage(key); store.active(key); store.remove(key); old.write("late"); assertNull(store.get(key)); assertEquals("",store.active()); store.remember(0,new JSONObject("{\"user_id\":\"a\"}"),"new"); old.write("late"); assertEquals("new",store.get(key).cookies);
    }
    @Test public void activeMirrorUpdatesBothSessionAndOnlyItsSavedAccount() throws Exception {
        String key=store.remember(0,new JSONObject("{\"user_id\":\"a\"}"),"old"); RainApiTest.Store session=new RainApiTest.Store(); RainApi.CookieStorage mirror=store.mirror(session,key); mirror.write("fresh"); assertEquals("fresh",session.data); assertEquals("fresh",store.get(key).cookies);
    }
}
