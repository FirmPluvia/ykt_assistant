package com.ykt.assistant;

import okhttp3.HttpUrl;

/** OpenAI-compatible endpoint, intentionally separate from all Rain authentication. */
final class AiConfig {
    final HttpUrl endpoint;
    final String model,key;
    final boolean jsonMode,includeImage,autoFill;
    AiConfig(String address,String model,String key,boolean jsonMode,boolean includeImage,boolean autoFill) {
        HttpUrl url=HttpUrl.parse(address==null?"":address.trim());
        if(url==null||!"https".equals(url.scheme())||!url.username().isEmpty()||!url.password().isEmpty()||url.query()!=null||url.fragment()!=null) throw new IllegalArgumentException("请输入无用户名、查询参数或片段的 HTTPS 接口地址");
        String path=url.encodedPath().replaceAll("/+$","");
        if(path.isEmpty()) path="/v1";
        if(!path.endsWith("/chat/completions")) path+="/chat/completions";
        endpoint=url.newBuilder().encodedPath(path).build();
        this.model=model==null?"":model.trim();
        if(this.model.isEmpty()||this.model.length()>256||this.model.chars().anyMatch(c->c<32||c==127)) throw new IllegalArgumentException("请输入有效的模型名称，最多 256 个字符");
        this.key=key==null?"":key.trim();
        if(this.key.length()>4096||this.key.chars().anyMatch(c->c<33||c>126)) throw new IllegalArgumentException("API Key 不能包含空格、换行或非英文字符");
        this.jsonMode=true;this.includeImage=includeImage;this.autoFill=autoFill;
    }
    String origin() {return endpoint.host()+(endpoint.port()==443?"":":"+endpoint.port());}
}
