package com.ykt.assistant;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class MultiAccountLocationsTest {
    AccountStore store;LocationCheckin expected;Set<String> pending=new HashSet<>();List<String> posts=new CopyOnWriteArrayList<>(),profiles=new CopyOnWriteArrayList<>();Map<String,LocationCheckin.Point> sent=new HashMap<>();boolean wrongIdentity,missingProof,mismatch,alreadyB,unconfirmedB,changedSource;CountDownLatch entered,release;
    @Before public void setup()throws Exception{android.content.Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("saved_accounts",0).edit().clear().commit();store=new AccountStore(c);for(String uid:new String[]{"self","b","c"})store.remember(0,RainApi.object("user_id",uid),"[]");expected=info(false,true,"session",31.2,5,false);}
    @After public void cleanup(){if(release!=null)release.countDown();}
    LocationCheckin info(boolean signed,boolean needed,String id,double lat,int round,boolean submitted)throws Exception{return LocationCheckin.parse(RainApi.object("data",RainApi.object("signed",signed,"needLocationSignIn",needed,"lat",lat,"lng",121.4,"sessionId",id)),null,"lesson",round,submitted);}
    RainApi open(AccountStore.Account a)throws Exception{return new RainApi(okhttp3.HttpUrl.get("https://www.yuketang.cn/"),new RainApiTest.Store()){
        @Override public JSONObject profile()throws Exception{profiles.add(a.uid);return RainApi.object("user_id",wrongIdentity?"wrong":a.uid);}@Override void promoteSession(CookieStorage storage){}@Override boolean restoreLesson(String lesson,java.util.function.BooleanSupplier stop){return !missingProof;}
        @Override LocationCheckin locationInfo()throws Exception{if(entered!=null&&a.uid.equals("b")){entered.countDown();release.await(5,TimeUnit.SECONDS);}return info(alreadyB&&a.uid.equals("b"),true,mismatch&&a.uid.equals("b")?"new-session":"session",31.2,0,false);}
        @Override LocationCheckin submitServerLocation(LocationCheckin own,LocationCheckin.Point point)throws Exception{assertEquals(0,own.round);assertEquals("session",own.sessionId);posts.add(a.uid);sent.put(a.uid,point);return info(!(unconfirmedB&&a.uid.equals("b")),true,"session",31.2,0,true);}
        @Override public void checkin(String lesson){fail("location synchronization must not implicitly join");}
    };}
    List<MultiAccountLocations.Result> run()throws Exception{MultiAccountLocations job=new MultiAccountLocations(store,this::open,pending);try{return job.run(Arrays.asList(store.get("0|b"),store.get("0|c")),0,"0|self",expected,new LocationCheckin.Point(35.1,120.2),()->changedSource?info(true,true,"different",31.2,5,false):expected,()->true,(a,s)->{});}finally{job.close();}}
    @Test public void usesEachAccountsOwnRoundAndSharesExplicitlySelectedCoordinates()throws Exception{List<MultiAccountLocations.Result> result=run();assertEquals(2,result.stream().filter(r->r.success).count());assertEquals(Arrays.asList("b","c"),posts);assertEquals(35.1,sent.get("b").lat,0);assertEquals(120.2,sent.get("c").lng,0);assertEquals("",store.active());}
    @Test public void alreadyConfirmedAccountIsNotSubmittedAgain()throws Exception{alreadyB=true;assertEquals(2,run().stream().filter(r->r.success).count());assertEquals(Collections.singletonList("c"),posts);}
    @Test public void missingProofAndIdentityMismatchCannotSubmit()throws Exception{missingProof=true;assertEquals(0,run().stream().filter(r->r.success).count());assertTrue(posts.isEmpty());missingProof=false;wrongIdentity=true;assertEquals(0,run().stream().filter(r->r.success).count());assertTrue(posts.isEmpty());}
    @Test public void differentLocationSessionRejectsOnlyThatAccount()throws Exception{mismatch=true;List<MultiAccountLocations.Result> result=run();assertFalse(result.get(0).success);assertTrue(result.get(1).success);assertEquals(Collections.singletonList("c"),posts);}
    @Test public void changedSourceDoesNotOpenOtherAccountSessions()throws Exception{changedSource=true;assertEquals(0,run().stream().filter(r->r.success).count());assertTrue(profiles.isEmpty());assertTrue(posts.isEmpty());}
    @Test public void unconfirmedPostIsNotRepeatedInSameLocalRound()throws Exception{unconfirmedB=true;List<MultiAccountLocations.Result> first=run();assertFalse(first.get(0).success);posts.clear();List<MultiAccountLocations.Result> second=run();assertFalse(second.get(0).success);assertTrue(posts.isEmpty());}
    @Test public void cancellingDuringStatusQueryStopsSubmissionsAndLaterAccounts()throws Exception{entered=new CountDownLatch(1);release=new CountDownLatch(1);MultiAccountLocations job=new MultiAccountLocations(store,this::open,pending);ExecutorService worker=Executors.newSingleThreadExecutor();try{Future<?> result=worker.submit(()->job.run(Arrays.asList(store.get("0|b"),store.get("0|c")),0,"0|self",expected,expected.point,()->expected,()->true,(a,s)->{}));assertTrue(entered.await(3,TimeUnit.SECONDS));job.close();release.countDown();result.get(3,TimeUnit.SECONDS);assertTrue(posts.isEmpty());assertEquals(Collections.singletonList("b"),profiles);}finally{release.countDown();job.close();worker.shutdownNow();}}
}
