package com.ykt.assistant;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="w320dp-h640dp-hdpi")
public class FlatButtonTest {
    Context context(){Context base=RuntimeEnvironment.getApplication();Configuration config=new Configuration(base.getResources().getConfiguration());config.fontScale=1.3f;return base.createConfigurationContext(config);}
    int px(Context c,int dp){return Math.round(dp*c.getResources().getDisplayMetrics().density);}
    void layout(FlatButton b,int width,int height){b.measure(View.MeasureSpec.makeMeasureSpec(px(b.getContext(),width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(px(b.getContext(),height),View.MeasureSpec.EXACTLY));b.layout(0,0,b.getMeasuredWidth(),b.getMeasuredHeight());}
    void assertVisible(FlatButton b){assertNotNull(b.getLayout());assertEquals(b.getText().length(),b.getLayout().getLineEnd(b.getLayout().getLineCount()-1));assertTrue(b.getLayout().getHeight()<=b.getHeight()-b.getCompoundPaddingTop()-b.getCompoundPaddingBottom()+1);for(int i=0;i<b.getLayout().getLineCount();i++){assertEquals(0,b.getLayout().getEllipsisCount(i));assertTrue(b.getLayout().getLineWidth(i)<=b.getWidth()-b.getCompoundPaddingLeft()-b.getCompoundPaddingRight()+1);}}
    @Test public void pressedAndFocusedStatesNeverAddElevation(){FlatButton b=new FlatButton(context());b.setText("提交");b.setPressed(true);b.refreshDrawableState();assertNull(b.getStateListAnimator());assertEquals(0,b.getElevation(),0);assertEquals(0,b.getTranslationZ(),0);}
    @Test public void longActionsFitNarrowScreenAtLargerFontScale(){Context c=context();for(String label:new String[]{"提交并同步 12 个其他账号","保存 AI 配置","检查所选账号答案","签到并进入课堂","使用 Cookie 登录","添加答案图片"}){FlatButton b=new FlatButton(c);b.setTextSize(13);b.setText(label);layout(b,260,46);assertVisible(b);}}
    @Test public void compactActionLabelsDoNotGetClipped(){Context c=context();for(String label:new String[]{"复制课堂 ID","移除","上一页","下一页"}){FlatButton b=new FlatButton(c);b.setTextSize(13);b.setText(label);layout(b,66,42);assertVisible(b);}}
    @Test public void labelCanReturnToRequestedSizeWhenWidthIncreases(){FlatButton b=new FlatButton(context());b.setTextSize(13);float requested=b.getTextSize();b.setText("签到并进入课堂");layout(b,70,42);assertTrue(b.getTextSize()<=requested);layout(b,260,48);assertEquals(requested,b.getTextSize(),.1f);assertVisible(b);}
    @Test public void longQuestionPreviewStaysFullyVisible() {FlatButton b=new FlatButton(context());b.setTextSize(13);b.setText("这是需要完整显示的课堂题目预览内容".repeat(3));layout(b,240,60);assertVisible(b);}
}
