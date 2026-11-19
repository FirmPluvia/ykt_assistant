package com.ykt.assistant;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;

/** Card close is a real cancellation, preserving cancellation and cleanup callbacks. */
final class CardDialog extends Dialog {
    private final int maxWidthDp;
    private ImageButton close;
    private boolean cancelAllowed=true;
    CardDialog(Context context) { this(context,520); }
    CardDialog(Context context,int maxWidthDp) {
        super(context);
        this.maxWidthDp=maxWidthDp;
        Window window=getWindow();
        float density=context.getResources().getDisplayMetrics().density;
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        window.setWindowAnimations(R.style.CardWindowAnimation);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        WindowManager.LayoutParams params=window.getAttributes();
        params.y=Math.round(12*density);
        params.dimAmount=.32f;
        window.setAttributes(params);
    }
    @Override protected void onStart() {
        super.onStart();
        Window window=getWindow();
        // Initializing the floating decor resets the window to wrap-content.
        window.getDecorView();
        android.util.DisplayMetrics metrics=getContext().getResources().getDisplayMetrics();
        int horizontalMargin=Math.round(24*metrics.density);
        int width=Math.min(Math.max(1,metrics.widthPixels-horizontalMargin),Math.round(maxWidthDp*metrics.density));
        window.setLayout(width,ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    @Override public void setContentView(View content) {
        FrameLayout card=new FrameLayout(getContext());
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
        shape.setColor(0xfff7f9f5); shape.setCornerRadius(22*getContext().getResources().getDisplayMetrics().density);
        card.setBackground(shape); card.setClipToOutline(true); card.setTag("card-rounded-viewport");
        View viewport=content;
        if(!(content instanceof ScrollView)) {
            ScrollView scroll=new ScrollView(getContext());
            scroll.addView(content,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            viewport=scroll;
        }
        card.addView(viewport,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        super.setContentView(card);
    }
    @Override public void setCancelable(boolean allowed) { super.setCancelable(allowed); cancelAllowed=allowed; sync(); }
    @Override public void cancel() { if(cancelAllowed) super.cancel(); }
    private void sync() { if(close!=null) { close.setEnabled(cancelAllowed); close.setAlpha(cancelAllowed?1f:.35f); } }
    static ImageButton closeButton(Context context,Dialog dialog,String title) {
        float density=context.getResources().getDisplayMetrics().density;
        ImageButton button=new ImageButton(context); button.setTag("card-close"); button.setContentDescription("关闭"+title); button.setFocusable(true); button.setMinimumWidth(Math.round(44*density)); button.setMinimumHeight(Math.round(44*density)); int padding=Math.round(12*density); button.setPadding(padding,padding,padding,padding); button.setStateListAnimator(null);
        button.setBackground(null);
        button.setImageDrawable(new Drawable() {
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            { paint.setColor(0xff2c5846); paint.setStrokeWidth(2*density); paint.setStrokeCap(Paint.Cap.ROUND); }
            @Override public void draw(Canvas canvas) { android.graphics.Rect b=getBounds(); float l=b.left+b.width()*.22f,r=b.left+b.width()*.78f,t=b.top+b.height()*.22f,d=b.top+b.height()*.78f; canvas.drawLine(l,t,r,d,paint); canvas.drawLine(r,t,l,d,paint); }
            @Override public void setAlpha(int alpha) {paint.setAlpha(alpha);invalidateSelf();}
            @Override public void setColorFilter(ColorFilter filter) {paint.setColorFilter(filter);invalidateSelf();}
            @Override public int getOpacity() {return PixelFormat.TRANSLUCENT;}
            @Override public int getIntrinsicWidth() {return Math.round(24*density);}
            @Override public int getIntrinsicHeight() {return Math.round(24*density);}
        });
        button.setOnClickListener(v->dialog.cancel());
        if(dialog instanceof CardDialog) { CardDialog card=(CardDialog)dialog; card.close=button; card.sync(); }
        return button;
    }
}
