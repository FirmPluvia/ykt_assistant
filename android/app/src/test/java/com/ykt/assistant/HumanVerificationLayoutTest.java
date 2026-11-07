package com.ykt.assistant;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class HumanVerificationLayoutTest {
    private ActivityController<Activity> controller; private Activity activity; private Dialog dialog; private final AtomicInteger cancelled=new AtomicInteger(),verified=new AtomicInteger();
    @Before public void setup() { controller=Robolectric.buildActivity(Activity.class).setup(); activity=controller.get(); }
    @After public void cleanup() { if(dialog!=null&&dialog.isShowing()) dialog.dismiss(); ShadowLooper.idleMainLooper(); controller.pause().stop().destroy(); }
    private void open() { dialog=HumanVerification.show(activity,"www.yuketang.cn","Test UA",new HumanVerification.Callback() { @Override public void verified(HumanVerification.Proof proof) { verified.incrementAndGet(); } @Override public void cancelled(String reason) { cancelled.incrementAndGet(); } }); ShadowLooper.idleMainLooper(); }
    private WebView web(View root) { if(root instanceof WebView) return (WebView)root; if(root instanceof ViewGroup) { ViewGroup group=(ViewGroup)root; for(int n=0;n<group.getChildCount();n++) { WebView result=web(group.getChildAt(n)); if(result!=null) return result; } } return null; }
    private View label(View root,String value) { if(root instanceof TextView&&value.equals(((TextView)root).getText().toString())) return root; if(root instanceof ViewGroup) { ViewGroup group=(ViewGroup)root; for(int n=0;n<group.getChildCount();n++) { View result=label(group.getChildAt(n),value); if(result!=null) return result; } } return null; }
    private void layout(float width,float height) { float d=activity.getResources().getDisplayMetrics().density; View root=dialog.getWindow().getDecorView(); int actual=dialog.getWindow().getAttributes().width; if(actual<=0) actual=Math.round(width*d); root.measure(View.MeasureSpec.makeMeasureSpec(actual,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.round(height*d),View.MeasureSpec.EXACTLY)); root.layout(0,0,actual,Math.round(height*d)); }
    @Test @Config(qualifiers="w393dp-h851dp-xxxhdpi") public void highDensityDoesNotBecome640PhysicalPixelsWide() { open(); layout(393,851); float d=activity.getResources().getDisplayMetrics().density; WebView web=web(dialog.getWindow().getDecorView()); assertTrue(web.getMeasuredWidth()/d>=380); assertTrue(web.getMeasuredHeight()/d>=650); }
    @Test @Config(qualifiers="w320dp-h568dp-hdpi") public void compactPhoneHasFullUsableWidth() { open(); layout(320,568); float d=activity.getResources().getDisplayMetrics().density; WebView web=web(dialog.getWindow().getDecorView()); assertTrue(web.getMeasuredWidth()/d>=310); assertTrue(web.getMeasuredHeight()/d>=400); }
    @Test @Config(qualifiers="w393dp-h851dp-xxxhdpi") public void hostViewportChangesWithWindowSize() { open(); layout(393,851); WebView web=web(dialog.getWindow().getDecorView()); int first=web.getMeasuredWidth(); layout(700,500); assertTrue(web.getMeasuredWidth()>first); }
    @Test public void cancellingOnlyReportsOnceAndNeverCreatesProof() { open(); label(dialog.getWindow().getDecorView(),"取消验证").performClick(); ShadowLooper.idleMainLooper(); dialog.dismiss(); ShadowLooper.idleMainLooper(); assertEquals(1,cancelled.get()); assertEquals(0,verified.get()); }
}
