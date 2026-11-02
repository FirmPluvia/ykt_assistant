package com.ykt.assistant;

import android.content.SharedPreferences;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class FeedStoreTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private SharedPreferences prefs; private LessonCache.Crypto crypto; private FeedStore store;
    static LessonCache.Crypto crypto() throws Exception {
        KeyGenerator generator=KeyGenerator.getInstance("AES"); generator.init(128); SecretKey key=generator.generateKey();
        return new LessonCache.Crypto() {
            public byte[] seal(byte[] plain,byte[] aad) throws Exception { Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key); c.updateAAD(aad); byte[] body=c.doFinal(plain),out=new byte[12+body.length]; System.arraycopy(c.getIV(),0,out,0,12); System.arraycopy(body,0,out,12,body.length); return out; }
            public byte[] open(byte[] encoded,byte[] aad) throws Exception { Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOf(encoded,12))); c.updateAAD(aad); return c.doFinal(Arrays.copyOfRange(encoded,12,encoded.length)); }
        };
    }
    @Before public void create() throws Exception { prefs=RuntimeEnvironment.getApplication().getSharedPreferences("feed-store-test",0); prefs.edit().clear().commit(); crypto=crypto(); store=new FeedStore(new File(temp.getRoot(),"activities"),prefs,crypto); }
    private JSONObject body() throws Exception { return RainApi.object("version",1,"marker","PRIVATE-ACTIVITY-FIXTURE","nested",RainApi.object("a",1,"b",2)); }
    @Test public void encryptedSnapshotSurvivesRestartAndIsScopedByAccountAndServer() throws Exception {
        assertTrue(store.session("server-a","user-a").write(body())); byte[] file=Files.readAllBytes(store.directory.listFiles()[0].toPath()); assertFalse(new String(file,StandardCharsets.UTF_8).contains("PRIVATE-ACTIVITY-FIXTURE")); FeedStore restarted=new FeedStore(store.directory,prefs,crypto); assertEquals("PRIVATE-ACTIVITY-FIXTURE",restarted.session("server-a","user-a").read().getString("marker")); assertNull(restarted.session("server-b","user-a").read()); assertNull(restarted.session("server-a","user-b").read());
    }
    @Test public void equalPayloadWithDifferentKeyOrderDoesNotRewriteCiphertext() throws Exception {
        FeedStore.Session session=store.session("host","uid"); assertTrue(session.write(body())); File file=store.directory.listFiles()[0]; byte[] old=Files.readAllBytes(file.toPath()); JSONObject reordered=RainApi.object("nested",RainApi.object("b",2,"a",1),"marker","PRIVATE-ACTIVITY-FIXTURE","version",1); assertFalse(new FeedStore(store.directory,prefs,crypto).session("host","uid").write(reordered)); assertArrayEquals(old,Files.readAllBytes(file.toPath())); assertTrue(session.write(body().put("marker","changed")));
    }
    @Test public void logoutRevokesOldReadersAndLateWriters() throws Exception {
        FeedStore.Session old=store.session("host","uid"); old.write(body()); store.revoke(); assertNull(old.read()); assertFalse(old.write(body().put("marker","late"))); FeedStore.Session fresh=new FeedStore(store.directory,prefs,crypto).session("host","uid"); assertNull(fresh.read()); fresh.write(body()); store.purgeRevoked(); assertEquals(1,store.directory.listFiles().length); assertNotNull(fresh.read());
    }
    @Test public void atomicBackupCanRecoverAndTamperedDataIsDiscarded() throws Exception {
        store.session("host","uid").write(body()); File file=store.directory.listFiles()[0],backup=new File(file.getPath()+".bak"); assertTrue(file.renameTo(backup)); assertNotNull(new FeedStore(store.directory,prefs,crypto).session("host","uid").read()); byte[] damaged=Files.readAllBytes(file.toPath()); damaged[damaged.length-1]^=1; Files.write(file.toPath(),damaged); assertNull(new FeedStore(store.directory,prefs,crypto).session("host","uid").read()); assertFalse(file.exists());
    }
}
