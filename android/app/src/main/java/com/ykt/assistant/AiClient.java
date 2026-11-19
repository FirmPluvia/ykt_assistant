package com.ykt.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import okhttp3.Call;
import okhttp3.CookieJar;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/** Independent client: no Rain CookieJar, user id, lesson token or interceptors. */
class AiClient implements AutoCloseable {
    interface Factory {AiClient open(AiConfig config,String userAgent);}
    private final AiConfig config;private final String userAgent;private final OkHttpClient http;
    private volatile Call pending;private volatile boolean cancelled;
    AiClient(AiConfig config,String userAgent){this(config,userAgent,new OkHttpClient.Builder());}
    AiClient(AiConfig config,String userAgent,OkHttpClient.Builder transport){this.config=config;this.userAgent=UserAgent.validate(userAgent);http=transport.cookieJar(CookieJar.NO_COOKIES).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).connectTimeout(20,TimeUnit.SECONDS).readTimeout(100,TimeUnit.SECONDS).callTimeout(120,TimeUnit.SECONDS).build();}
    AiQuestion.Result analyze(AiQuestion question,byte[] jpeg)throws Exception{
        if(cancelled)throw new Exception("AI 分析已取消");
        Object content=question.payload.toString();
        if(jpeg!=null){if(jpeg.length>2*1024*1024)throw new Exception("课件图片过大，未发送");content=new JSONArray().put(RainApi.object("type","text","text",question.payload.toString())).put(RainApi.object("type","image_url","image_url",RainApi.object("url","data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(jpeg))));}
        JSONObject body=RainApi.object("model",config.model,"stream",false,"messages",new JSONArray().put(RainApi.object("role","system","content",AiQuestion.SYSTEM_PROMPT)).put(RainApi.object("role","user","content",content)));
        body.put("response_format",RainApi.object("type","json_object"));
        RequestBody delegate=RequestBody.create(body.toString(),MediaType.get("application/json; charset=utf-8"));
        RequestBody once=new RequestBody(){@Override public MediaType contentType(){return delegate.contentType();}@Override public long contentLength()throws IOException{return delegate.contentLength();}@Override public void writeTo(okio.BufferedSink sink)throws IOException{delegate.writeTo(sink);}@Override public boolean isOneShot(){return true;}};
        Request.Builder request=new Request.Builder().url(config.endpoint).header("User-Agent",userAgent).header("Accept","application/json").post(once);
        if(!config.key.isEmpty())request.header("Authorization","Bearer "+config.key);
        Call call=http.newCall(request.build());pending=call;if(cancelled){call.cancel();throw new Exception("AI 分析已取消");}
        try(Response response=call.execute()){
            int code=response.code();
            if(code==401||code==403)throw new Exception("AI 鉴权失败，请检查 API Key 和模型权限");
            if(code==429)throw new Exception("AI 请求受限或额度不足，请稍后重试");
            if(code>=300&&code<400)throw new Exception("AI 接口要求重定向，请填写最终 HTTPS 接口地址");
            if(code==400)throw new Exception("AI 请求参数不被接受，请核对模型、图片能力，并确认接口支持 JSON 模式");
            if(!response.isSuccessful())throw new Exception("AI 服务返回 HTTP "+code+"，请稍后重试");
            if(response.body()==null)throw new Exception("AI 服务返回空内容");
            String raw=new String(readLimited(response.body().byteStream(),2*1024*1024),java.nio.charset.StandardCharsets.UTF_8);JSONObject data;
            try{data=new JSONObject(raw);}catch(Exception e){throw new Exception("AI 接口响应不是有效 JSON");}
            JSONArray choices=data.optJSONArray("choices");JSONObject first=choices==null?null:choices.optJSONObject(0);if(first==null)throw new Exception("AI 接口未返回 choices，请使用兼容 Chat Completions 的接口");
            if("length".equals(first.optString("finish_reason")))throw new Exception("AI 输出被截断，未填入；请调整模型或服务端输出限制");
            JSONObject message=first.optJSONObject("message");if(message==null||!message.optString("refusal","").isEmpty())throw new Exception("模型未提供可解析的答案");
            Object value=message.opt("content");String text=value instanceof String?(String)value:"";
            if(value instanceof JSONArray){StringBuilder joined=new StringBuilder();JSONArray parts=(JSONArray)value;for(int i=0;i<parts.length();i++){JSONObject part=parts.optJSONObject(i);if(part!=null&&"text".equals(part.optString("type")))joined.append(part.optString("text",""));}text=joined.toString();}
            if(cancelled)throw new Exception("AI 分析已取消");
            return question.parse(text);
        }catch(IOException e){throw new Exception(cancelled?"AI 分析已取消":"AI 连接失败或超时，请检查接口与网络");}finally{pending=null;}
    }
    private static byte[] readLimited(InputStream stream,int maximum)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];for(int n;(n=stream.read(buffer))!=-1;){if(out.size()+n>maximum)throw new IOException("response too large");out.write(buffer,0,n);}return out.toByteArray();}
    static byte[] prepareImage(byte[] raw)throws Exception{
        if(raw==null||raw.length>16*1024*1024)throw new Exception("课件图片不可用或过大");
        android.graphics.Bitmap decoded=SlideExporter.decode(raw,1280),scaled=decoded;float factor=Math.min(1,1280f/Math.max(decoded.getWidth(),decoded.getHeight()));
        if(factor<1)scaled=android.graphics.Bitmap.createScaledBitmap(decoded,Math.max(1,Math.round(decoded.getWidth()*factor)),Math.max(1,Math.round(decoded.getHeight()*factor)),true);
        android.graphics.Bitmap opaque=android.graphics.Bitmap.createBitmap(scaled.getWidth(),scaled.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(opaque);canvas.drawColor(android.graphics.Color.WHITE);canvas.drawBitmap(scaled,0,0,null);ByteArrayOutputStream output=new ByteArrayOutputStream();opaque.compress(android.graphics.Bitmap.CompressFormat.JPEG,82,output);opaque.recycle();if(scaled!=decoded)scaled.recycle();decoded.recycle();if(output.size()>2*1024*1024)throw new Exception("压缩后的课件图片仍过大，未发送");return output.toByteArray();
    }
    @Override public void close(){cancelled=true;Call call=pending;if(call!=null)call.cancel();}
}
