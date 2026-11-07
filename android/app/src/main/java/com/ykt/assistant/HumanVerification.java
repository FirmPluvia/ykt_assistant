package com.ykt.assistant;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only the official challenge runs in this viewport; native login secrets never enter HTML. */
final class HumanVerification {
    interface Callback { void verified(Proof proof); void cancelled(String reason); }
    static final class Proof {
        final String ticket,rand;
        Proof(String ticket,String rand) { LoginInput.proof(ticket,rand); this.ticket=ticket; this.rand=rand; }
    }
    private static final String HTML="<!doctype html><html lang='zh-CN'><head><meta name='viewport' content='width=device-width,initial-scale=1,viewport-fit=cover'><style>html,body{width:100%;height:100%;margin:0;padding:0;overflow:hidden;background:#f7f9f5}</style></head><body><script>function report(r){LoginChallenge.result(JSON.stringify(r));}function start(){try{new TencentCaptcha('2091064951',report,{needFeedBack:false}).show();}catch(e){report({ret:-1});}}</script><script src='https://turing.captcha.qcloud.com/TCaptcha.js' onload='start()' onerror='report({ret:-1})'></script></body></html>";
    static boolean allowed(Uri uri) {
        if(uri==null||!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||(uri.getPort()!=-1&&uri.getPort()!=443)) return false;
        String host=uri.getHost().toLowerCase(Locale.ROOT);
        return host.equals("captcha.qcloud.com")||host.endsWith(".captcha.qcloud.com")||host.equals("captcha.gtimg.com")||host.endsWith(".captcha.gtimg.com")||host.equals("ssl.captcha.qq.com")||host.equals("t.captcha.qq.com");
    }
    private static boolean inline(Uri uri) { return "data".equals(uri.getScheme())||"blob".equals(uri.getScheme())||"about:blank".equals(uri.toString()); }
    private static int dp(Activity activity,float value) { return Math.round(value*activity.getResources().getDisplayMetrics().density); }
    @SuppressLint("SetJavaScriptEnabled")
    static Dialog show(Activity activity,String host,String userAgent,Callback callback) {
        Dialog dialog=new Dialog(activity,android.R.style.Theme_Material_Light_NoActionBar); Handler main=new Handler(Looper.getMainLooper()); AtomicBoolean finished=new AtomicBoolean();
        LinearLayout layout=new LinearLayout(activity); layout.setOrientation(LinearLayout.VERTICAL); layout.setBackgroundColor(0xfff7f9f5);
        if(Build.VERSION.SDK_INT>=30) layout.setOnApplyWindowInsetsListener((v,insets) -> { android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()); v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets; }); else layout.setFitsSystemWindows(true);
        LinearLayout toolbar=new LinearLayout(activity); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(dp(activity,16),0,dp(activity,12),0);
        TextView title=new TextView(activity); title.setText("官方安全验证"); title.setTextSize(17); title.setTextColor(0xff2c5846); title.setIncludeFontPadding(false); toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button cancel=new Button(activity); cancel.setText("取消验证"); cancel.setAllCaps(false); cancel.setTextSize(13); cancel.setTextColor(0xff2c5846); cancel.setMinHeight(0); cancel.setMinimumHeight(0); cancel.setPadding(dp(activity,12),0,dp(activity,12),0); cancel.setStateListAnimator(null); GradientDrawable background=new GradientDrawable(); background.setColor(0xffedf3e9); background.setCornerRadius(dp(activity,12)); cancel.setBackground(background); cancel.setOnClickListener(v -> dialog.dismiss()); toolbar.addView(cancel,new LinearLayout.LayoutParams(dp(activity,96),dp(activity,44))); layout.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(activity,60)));
        WebView web=new WebView(activity); web.setContentDescription("腾讯官方安全验证内容"); web.setBackgroundColor(Color.TRANSPARENT); WebSettings settings=web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); settings.setUserAgentString(UserAgent.validate(userAgent)); settings.setSupportMultipleWindows(false); settings.setSaveFormData(false);
        class Bridge {
            @JavascriptInterface public void result(String raw) {
                if(raw==null||raw.length()>16384) return;
                main.post(() -> {
                    if(finished.get()||!dialog.isShowing()||activity.isFinishing()||activity.isDestroyed()) return;
                    Proof proof=null; String message="安全验证未完成，请重试";
                    try { JSONObject data=new JSONObject(raw); int ret=data.getInt("ret"); if(ret==0) proof=new Proof(data.getString("ticket"),data.getString("randstr")); else message=ret==2?"已取消安全验证":"安全验证加载失败，请检查网络后重试"; } catch(Exception ignored) {}
                    if(!finished.compareAndSet(false,true)) return;
                    dialog.dismiss(); if(proof!=null) callback.verified(proof); else callback.cancelled(message);
                });
            }
        }
        web.addJavascriptInterface(new Bridge(),"LoginChallenge"); web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) { return request.isForMainFrame()||(!allowed(request.getUrl())&&!inline(request.getUrl())); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) { return allowed(request.getUrl())||inline(request.getUrl())?null:new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0])); }
        });
        layout.addView(web,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1)); dialog.setContentView(layout);
        dialog.setOnDismissListener(d -> { web.stopLoading(); web.removeJavascriptInterface("LoginChallenge"); web.destroy(); if(finished.compareAndSet(false,true)) callback.cancelled("已取消安全验证"); });
        if(dialog.getWindow()!=null) {
            if(Build.VERSION.SDK_INT>=30) dialog.getWindow().setDecorFitsSystemWindows(false);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent); dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); dialog.getWindow().setStatusBarColor(0xfff7f9f5); dialog.getWindow().setNavigationBarColor(0xfff7f9f5); dialog.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        dialog.show(); if(dialog.getWindow()!=null) dialog.getWindow().setLayout(-1,-1); web.loadDataWithBaseURL("https://"+host+"/",HTML,"text/html","UTF-8",null); return dialog;
    }
}
