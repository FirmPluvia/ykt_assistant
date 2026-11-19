package com.ykt.assistant;
import android.graphics.Bitmap;
import android.net.Uri;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="w393dp-h851dp-hdpi") @LooperMode(LooperMode.Mode.PAUSED)
public class AnswerImageUiTest {
    AiUiTest f;PhotoApi api;Uri uri;ClassroomQuizDialog sheet;
    static class PhotoApi extends RainApi {int uploads,submitted;JSONObject payload;CountDownLatch entered,release;PhotoApi()throws Exception{super(okhttp3.HttpUrl.get("https://www.yuketang.cn/"),new RainApiTest.Store());}@Override public AnswerImage uploadAnswerImage(AnswerImage photo,java.util.function.BooleanSupplier stop)throws Exception{uploads++;if(entered!=null){entered.countDown();release.await(5,TimeUnit.SECONDS);}if(stop.getAsBoolean())throw new Exception("已取消");return photo.uploaded("https://qn-scd1.yuketang.cn/photo.jpg");}@Override public JSONObject submitAnswer(LessonQuiz.Submission s)throws Exception{submitted++;payload=s.payload();return RainApi.object("code",0);}}
    @Before public void setup()throws Exception{f=new AiUiTest();f.setup();f.q.spec.put("problemType",5);f.q.text="";api=new PhotoApi();File file=new File(f.activity.getCacheDir(),"answer-test.png");Bitmap bitmap=Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888);try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();uri=Uri.fromFile(file);}
    @After public void cleanup(){if(api.release!=null)api.release.countDown();if(sheet!=null)sheet.close();api.close();f.cleanup();}
    void show(List<Uri> uris){sheet=new ClassroomQuizDialog(f.activity,api,f.quiz,f.worker,f.current::get);sheet.setImagePicker(done->done.accept(uris));sheet.show("q");}
    @Test public void selectedPhotoUploadsAndImageOnlyAnswerSerializesPics()throws Exception{show(Collections.singletonList(uri));f.click("添加答案图片");f.waitFor(()->!f.q.imageBusy&&f.q.images.size()==1);f.click("提交答案");f.waitFor(()->f.q.submitted);assertEquals(1,api.uploads);assertEquals("",api.payload.getJSONObject("result").getString("content"));assertEquals(1,api.payload.getJSONObject("result").getJSONArray("pics").length());}
    @Test public void removePhotoUpdatesDraftWithoutSubmitting()throws Exception{show(Collections.singletonList(uri));f.click("添加答案图片");f.waitFor(()->!f.q.imageBusy&&f.q.images.size()==1);f.click("移除");assertTrue(f.q.images.isEmpty());assertEquals(0,api.submitted);}
    @Test public void pendingUploadDisablesSubmitAndCloseDropsLateResult()throws Exception{api.entered=new CountDownLatch(1);api.release=new CountDownLatch(1);show(Collections.singletonList(uri));f.click("添加答案图片");assertTrue(api.entered.await(3,TimeUnit.SECONDS));assertFalse(f.find("提交答案").isEnabled());sheet.close();api.release.countDown();f.worker.submit(()->{}).get(3,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();assertFalse(f.q.imageBusy);assertTrue(f.q.images.isEmpty());assertEquals(0,api.submitted);}
    @Test public void overLimitSelectionDoesNotReadOrUploadAnyImage()throws Exception{show(Collections.nCopies(7,uri));f.click("添加答案图片");assertEquals(0,api.uploads);assertFalse(f.q.imageBusy);assertTrue(f.q.lastError.contains("6 张"));}
    @Test public void preparedImageStripsMetadataAndKeepsAspectRatio()throws Exception{AnswerImage photo=AnswerImage.read(f.activity,uri);assertNull(photo.url);assertTrue(photo.bytes().length>0);android.graphics.Bitmap decoded=android.graphics.BitmapFactory.decodeByteArray(photo.bytes(),0,photo.bytes().length);assertEquals(2,decoded.getWidth()/decoded.getHeight());decoded.recycle();}
}
