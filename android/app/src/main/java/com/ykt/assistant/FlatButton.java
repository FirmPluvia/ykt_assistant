package com.ykt.assistant;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.widget.Button;

/** Shadow-free native button; fits fixed-height action rows without ellipsizing their labels. */
// Layout constants are API-26 compatible aliases; newer SDK IntDefs refer to API-29 LineBreaker.
@android.annotation.SuppressLint("WrongConstant")
public final class FlatButton extends Button {
    private float requestedPixels;private boolean ready;
    public FlatButton(Context context){this(context,null);}
    public FlatButton(Context context,AttributeSet attrs){this(context,attrs,android.R.attr.buttonStyle);}
    public FlatButton(Context context,AttributeSet attrs,int style){super(context,attrs,style);setStateListAnimator(null);setElevation(0);setTranslationZ(0);setAllCaps(false);setIncludeFontPadding(false);setGravity(Gravity.CENTER);setMinimumWidth(0);setMinWidth(0);setMinHeight(dp(44));setMinimumHeight(dp(44));setPadding(dp(10),dp(4),dp(10),dp(4));setSingleLine(false);setMaxLines(Integer.MAX_VALUE);setHorizontallyScrolling(false);setEllipsize(null);setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);requestedPixels=getTextSize();ready=true;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override public void setTextSize(int unit,float value){if(ready)requestedPixels=TypedValue.applyDimension(unit,value,getResources().getDisplayMetrics());super.setTextSize(unit,value);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        if(ready&&MeasureSpec.getMode(widthSpec)==MeasureSpec.EXACTLY&&MeasureSpec.getMode(heightSpec)==MeasureSpec.EXACTLY){int width=MeasureSpec.getSize(widthSpec)-getCompoundPaddingLeft()-getCompoundPaddingRight(),height=MeasureSpec.getSize(heightSpec)-getCompoundPaddingTop()-getCompoundPaddingBottom();if(width>0&&height>0){TextPaint paint=new TextPaint(getPaint());float size=requestedPixels,min=Math.min(size,8*getResources().getDisplayMetrics().scaledDensity);CharSequence label=getText();while(true){paint.setTextSize(size);StaticLayout layout=StaticLayout.Builder.obtain(label,0,label.length(),paint,width).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).build();boolean fits=layout.getHeight()<=height;for(int i=0;i<layout.getLineCount()&&fits;i++)fits=layout.getLineWidth(i)<=width+1;if(fits||size<=min)break;size=Math.max(min,size-1);}if(Math.abs(getTextSize()-size)>.1f)super.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);}}
        super.onMeasure(widthSpec,heightSpec);
    }
}
