package com.ykt.assistant;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class DiagnosticLogTest {
    @Test public void recordsOnlyAllowedMetadataAndRejectsUnexpectedValues() throws Exception {
        File root=new File(RuntimeEnvironment.getApplication().getCacheDir(),"log-"+UUID.randomUUID()); DiagnosticLog log=new DiagnosticLog(root); String id=UUID.randomUUID().toString();
        log.event("QR_WAIT",id,"BUSINESS_ERROR",200,50001,30_000); log.session("QR_SESSION",id,true,true,"LOCAL_CONTENT"); log.event("password=supersecret","sessionid=real-secret","privateToken123",403,0,1); log.session("QR_SESSION","private-token",true,false,"qrContent-private-token");
        String text=new String(log.today(),StandardCharsets.UTF_8); assertFalse(text.contains("supersecret")); assertFalse(text.contains("real-secret")); assertFalse(text.contains("private-token")); assertFalse(text.contains("privateToken123")); assertTrue(text.contains("50001")); assertTrue(text.contains("LOCAL_CONTENT")); for(String line:text.split("\n")) new JSONObject(line); log.close();
    }
    @Test public void prunesOnlyExpiredLogFilesAndKeepsApplicationData() throws Exception {
        File root=new File(RuntimeEnvironment.getApplication().getCacheDir(),"log-"+UUID.randomUUID()),old=new File(root,"2000-01-01"),other=new File(root,"classrooms"); old.mkdirs(); other.mkdirs(); Files.write(new File(old,"app.log").toPath(),"old".getBytes(StandardCharsets.UTF_8)); Files.write(new File(other,"credentials.bin").toPath(),"keep".getBytes(StandardCharsets.UTF_8));
        DiagnosticLog log=new DiagnosticLog(root); log.event("APP","startup","OPEN",0,0,0); assertTrue(log.today().length>0); assertFalse(new File(old,"app.log").exists()); assertTrue(new File(other,"credentials.bin").exists()); log.close();
    }
}
