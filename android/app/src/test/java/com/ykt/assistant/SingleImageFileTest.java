package com.ykt.assistant;

import android.net.Uri;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
public class SingleImageFileTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void savesExactSourceBytesAndReportsActualProgress() throws Exception {
        byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a4n8AAAAASUVORK5CYII="); SingleImageFile file=SingleImageFile.from(png); File out=temp.newFile("image.png"); List<Integer> progress=new ArrayList<>(); file.save(RuntimeEnvironment.getApplication(),Uri.fromFile(out),() -> false,progress::add); assertArrayEquals(png,Files.readAllBytes(out.toPath())); assertEquals("image/png",file.mime); assertEquals(Integer.valueOf(100),progress.get(progress.size()-1));
    }
    @Test public void detectsActualImageEncodingAndRejectsHtml() throws Exception {
        assertEquals("image/jpeg",SingleImageFile.from(new byte[]{(byte)255,(byte)216,(byte)255,0}).mime); assertEquals("gif",SingleImageFile.from("GIF89aframe-data".getBytes(StandardCharsets.US_ASCII)).extension); assertEquals("webp",SingleImageFile.from("RIFF0000WEBPdata".getBytes(StandardCharsets.US_ASCII)).extension);
        try { SingleImageFile.from("<html>login</html>".getBytes(StandardCharsets.UTF_8)); fail(); } catch(java.io.IOException expected) {}
    }
    @Test public void cancellationPreventsWritingAnyImage() throws Exception {
        SingleImageFile file=SingleImageFile.from("GIF89aframe-data".getBytes(StandardCharsets.US_ASCII)); File out=temp.newFile("cancel.gif"); try { file.save(RuntimeEnvironment.getApplication(),Uri.fromFile(out),() -> true,p -> {}); fail(); } catch(java.io.InterruptedIOException expected) {} assertEquals(0,out.length());
    }
}
