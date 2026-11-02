package com.ykt.assistant;

import android.content.SharedPreferences;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class LocalStorageTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private SharedPreferences prefs;
    private LessonCache.Crypto crypto;
    private AtomicLong clock;
    private File directory;
    private LessonCache cache;
    @Before public void create() throws Exception {
        prefs=RuntimeEnvironment.getApplication().getSharedPreferences("cache-test",0); prefs.edit().clear().commit(); directory=new File(temp.getRoot(),"classrooms"); clock=new AtomicLong(Instant.parse("2026-10-31T12:00:00Z").toEpochMilli());
        KeyGenerator generator=KeyGenerator.getInstance("AES"); generator.init(128); SecretKey key=generator.generateKey();
        crypto=new LessonCache.Crypto() {
            public byte[] seal(byte[] plain,byte[] aad) throws Exception { Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key); c.updateAAD(aad); byte[] body=c.doFinal(plain),out=new byte[12+body.length]; System.arraycopy(c.getIV(),0,out,0,12); System.arraycopy(body,0,out,12,body.length); return out; }
            public byte[] open(byte[] encoded,byte[] aad) throws Exception { Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOf(encoded,12))); c.updateAAD(aad); return c.doFinal(Arrays.copyOfRange(encoded,12,encoded.length)); }
        };
        cache=new LessonCache(directory,prefs,crypto,clock::get);
    }
    private JSONObject credentials() throws Exception { return RainApi.object("bearer","private-bearer-fixture","lessonToken","private-socket-fixture"); }
    @Test public void encryptedCredentialsSurviveNewInstanceAndStayScoped() throws Exception {
        cache.session().write("changjiang.yuketang.cn","account-a","lesson-a",credentials()); File[] files=directory.listFiles(); assertEquals(1,files.length); String disk=new String(Files.readAllBytes(files[0].toPath()),StandardCharsets.UTF_8); assertFalse(disk.contains("private-bearer-fixture")); assertFalse(disk.contains("private-socket-fixture"));
        RainApi.LessonStorage restored=new LessonCache(directory,prefs,crypto,clock::get).session(); assertEquals("private-bearer-fixture",restored.read("changjiang.yuketang.cn","account-a","lesson-a").getString("bearer")); assertNull(restored.read("pro.yuketang.cn","account-a","lesson-a")); assertNull(restored.read("changjiang.yuketang.cn","account-b","lesson-a")); assertNull(restored.read("changjiang.yuketang.cn","account-a","lesson-b"));
    }
    @Test public void expiredAndFutureDatedCredentialsAreRemoved() throws Exception {
        RainApi.LessonStorage store=cache.session(); store.write("host","account","lesson",credentials()); clock.addAndGet(LessonCache.MAX_AGE); assertNull(store.read("host","account","lesson")); assertEquals(0,directory.listFiles().length);
        store.write("host","account","lesson",credentials()); clock.decrementAndGet(); assertNull(store.read("host","account","lesson"));
    }
    @Test public void LogoutRevocationSurvivesRestartAndRejectsLateWrites() throws Exception {
        RainApi.LessonStorage old=cache.session(); old.write("host","account","lesson",credentials()); cache.revoke(); old.write("host","account","other",credentials()); assertNull(old.read("host","account","lesson"));
        RainApi.LessonStorage fresh=new LessonCache(directory,prefs,crypto,clock::get).session(); assertNull(fresh.read("host","account","lesson")); fresh.write("host","account","new",credentials()); cache.purgeRevoked(); assertNotNull(fresh.read("host","account","new")); assertEquals(1,directory.listFiles().length);
    }
    @Test public void TamperedCredentialCannotBeRestored() throws Exception {
        RainApi.LessonStorage store=cache.session(); store.write("host","account","lesson",credentials()); File file=directory.listFiles()[0]; byte[] bytes=Files.readAllBytes(file.toPath()); bytes[bytes.length-1]^=1; Files.write(file.toPath(),bytes); assertNull(store.read("host","account","lesson")); assertFalse(file.exists());
    }
    @Test public void retiredLogCleanupPreservesClassroomCredentials() throws Exception {
        RainApi.LessonStorage store=cache.session(); store.write("host","account","lesson",credentials());
        File logs=new File(temp.getRoot(),"logs"),day=new File(logs,"2026-10-31"); assertTrue(day.mkdirs()); Files.write(new File(day,"app.log").toPath(),"retired-log".getBytes(StandardCharsets.UTF_8));
        LegacyLogCleanup.remove(logs); assertFalse(logs.exists()); assertNotNull(store.read("host","account","lesson"));
    }
}
