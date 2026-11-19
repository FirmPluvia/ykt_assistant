package com.ykt.assistant;
import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ImageButton;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class CardDialogTest {
    ActivityController<Activity> host; Activity activity; CardDialog dialog; ImageButton close;
    @Before public void setup(){host=Robolectric.buildActivity(Activity.class).setup();activity=host.get();dialog=new CardDialog(activity);close=CardDialog.closeButton(activity,dialog,"测试卡片");LinearLayout panel=new LinearLayout(activity);panel.addView(close);dialog.setContentView(panel);dialog.show();}
    @After public void cleanup(){dialog.dismiss();host.pause().stop().destroy();}
    @Test public void iconIsAccessibleAndXDeliversCancelAndDismissCallbacks(){int[] calls={0,0};dialog.setOnCancelListener(d->calls[0]++);dialog.setOnDismissListener(d->calls[1]++);assertEquals("关闭测试卡片",close.getContentDescription());assertNotNull(close.getDrawable());assertNull(close.getBackground());close.performClick();ShadowLooper.idleMainLooper();assertFalse(dialog.isShowing());assertArrayEquals(new int[]{1,1},calls);}
    @Test public void busyCardDisablesXAndRejectsProgrammaticCancel(){dialog.setCancelable(false);assertFalse(close.isEnabled());close.performClick();dialog.cancel();ShadowLooper.idleMainLooper();assertTrue(dialog.isShowing());dialog.setCancelable(true);assertTrue(close.isEnabled());close.performClick();ShadowLooper.idleMainLooper();assertFalse(dialog.isShowing());}
    @Test public void successfulDismissDoesNotInvokeCancellation(){int[] calls={0};dialog.setOnCancelListener(d->calls[0]++);dialog.dismiss();ShadowLooper.idleMainLooper();assertEquals(0,calls[0]);}
    @Test @Config(sdk={28,35}, qualifiers="w320dp-h568dp-xhdpi")
    public void shortContentStillUsesPhoneWidthAfterWindowIsShown() {
        android.util.DisplayMetrics metrics=activity.getResources().getDisplayMetrics();
        assertEquals(metrics.widthPixels-Math.round(24*metrics.density),dialog.getWindow().getAttributes().width);
    }
    @Test @Config(qualifiers="w800dp-h1000dp-mdpi")
    public void wideScreenKeepsTheDefaultAndLoginWidthCaps() {
        assertEquals(520,dialog.getWindow().getAttributes().width);
        CardDialog login=new CardDialog(activity,460);
        try {
            login.setContentView(new android.widget.ScrollView(activity));
            login.show();
            assertEquals(460,login.getWindow().getAttributes().width);
        } finally {
            login.dismiss();
        }
    }
    @Test public void scrollViewportKeepsFixedRoundedOutline() {
        android.widget.ScrollView scroll=new android.widget.ScrollView(activity);android.widget.TextView content=new android.widget.TextView(activity);content.setHeight(3000);scroll.addView(content);dialog.setContentView(scroll);View card=dialog.findViewById(android.R.id.content).findViewWithTag("card-rounded-viewport");assertNotNull(card);assertTrue("viewport must clip its children",card.getClipToOutline());card.layout(0,0,360,500);card.getBackground().setBounds(0,0,360,500);android.graphics.Outline before=new android.graphics.Outline();card.getBackground().getOutline(before);assertTrue("outline must stay rounded",before.getRadius()>0);scroll.scrollTo(0,900);android.graphics.Outline after=new android.graphics.Outline();card.getBackground().getOutline(after);assertEquals(before.getRadius(),after.getRadius(),0);assertEquals(0,card.getScrollY());
    }
    @Test public void cardWrapsShortContentAndLetsTallContentScrollAtBottom() {
        android.view.WindowManager.LayoutParams window=dialog.getWindow().getAttributes();
        assertEquals(android.view.Gravity.BOTTOM,window.gravity & android.view.Gravity.VERTICAL_GRAVITY_MASK);
        assertEquals(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,window.height);
        assertEquals(R.style.CardWindowAnimation,window.windowAnimations);

        View content=new View(activity);
        LinearLayout panel=new LinearLayout(activity);
        panel.addView(content,new LinearLayout.LayoutParams(-1,120));
        dialog.setContentView(panel);
        android.view.ViewGroup card=dialog.findViewById(android.R.id.content)
            .findViewWithTag("card-rounded-viewport");
        int width=View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY);
        int height=View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.AT_MOST);
        card.measure(width,height);
        assertEquals(120,card.getMeasuredHeight());

        content.setLayoutParams(new LinearLayout.LayoutParams(-1,900));
        card.measure(width,height);
        card.layout(0,0,360,card.getMeasuredHeight());
        assertEquals(500,card.getMeasuredHeight());
        assertTrue(card.getChildAt(0).canScrollVertically(1));
    }
}
