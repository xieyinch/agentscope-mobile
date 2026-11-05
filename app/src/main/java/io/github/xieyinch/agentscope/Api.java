package io.github.xieyinch.agentscope;

import android.net.Uri;
import org.json.*;
import java.io.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Native HTTP transport. Platform bearer credentials never leave the platform origin. */
final class Api {
    static final String PLATFORM="https://platform.agentscope.io";
    private static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
    final SecretStore secrets;
    final OkHttpClient http=new OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS)
        .readTimeout(60,TimeUnit.SECONDS).followRedirects(false).cookieJar(new CookieJar(){
        public void saveFromResponse(HttpUrl url,java.util.List<Cookie> cookies){ConsoleSession.OriginCookies jar=instanceCookies;if(jar!=null){jar.saveFromResponse(url,cookies);persistCookies(jar);}}
        public java.util.List<Cookie> loadForRequest(HttpUrl url){ConsoleSession.OriginCookies jar=instanceCookies;return jar==null?java.util.Collections.emptyList():jar.loadForRequest(url);}
    }).build();
    volatile String agent="default";
    final ThreadLocal<String> requestAgent=new ThreadLocal<>();
    private volatile ConsoleSession.OriginCookies instanceCookies;
    Api(SecretStore store) {
        secrets=store;
        HttpUrl saved=HttpUrl.parse(store.get("instance"));
        if(saved!=null){
            try{JSONArray values=new JSONArray(store.get("instanceCookies"));java.util.List<String> list=new java.util.ArrayList<>();for(int i=0;i<values.length();i++)list.add(values.getString(i));instanceCookies=ConsoleSession.OriginCookies.restore(saved,list);}catch(Exception ignored){}
        }
    }
    static JSONObject obj(Object... pairs) {
        JSONObject o=new JSONObject();
        try { for(int i=0;i<pairs.length;i+=2)o.put((String)pairs[i],pairs[i+1]); }
        catch(JSONException e){throw new IllegalArgumentException(e);} return o;
    }
    static JSONObject object(Object v) { return v instanceof JSONObject ? (JSONObject)v : new JSONObject(); }
    static String str(JSONObject o,String key) { return o.optString(key,""); }
    static Object unwrap(Object v) {
        if(v instanceof JSONObject && ((JSONObject)v).has("data"))return ((JSONObject)v).opt("data");
        return v;
    }
    static JSONArray array(Object v) {
        if(v instanceof JSONArray)return (JSONArray)v;
        JSONObject o=object(v);
        for(String key:new String[]{"items","list","apps","agents","articles","records","entries","data","backups","skills","plugins"}) {
            Object x=o.opt(key); if(x instanceof JSONArray)return (JSONArray)x;
        }
        return new JSONArray();
    }
    static String text(Object v) {
        if(v==null||v==JSONObject.NULL)return "";
        if(v instanceof String)return (String)v;
        if(v instanceof JSONArray) {StringBuilder b=new StringBuilder();JSONArray a=(JSONArray)v;for(int i=0;i<a.length();i++){String t=text(a.opt(i));if(!t.isEmpty())b.append(t).append('\n');}return b.toString().trim();}
        if(v instanceof JSONObject) {
            JSONObject o=(JSONObject)v;
            for(String k:new String[]{"text","content","message","description","body","title","name"})if(o.has(k))return text(o.opt(k));
            return "";
        }
        return String.valueOf(v);
    }
    Object platform(String method,String path,Object body) throws Exception {
        if(!path.startsWith("/api/"))throw new IOException("平台接口地址不正确");
        try {return call(PLATFORM+path,method,body,secrets.get("access"),false,null);}
        catch(HttpFailure e) {
            if(e.code!=401||path.startsWith("/api/v1/auth/login")||path.startsWith("/api/v1/auth/refresh"))throw e;
            synchronized(this){
                String refresh=secrets.get("refresh");
                if(refresh.isEmpty())throw new IOException("登录已过期，请重新登录");
                JSONObject auth=object(unwrap(call(PLATFORM+"/api/v1/auth/refresh","POST",obj("refreshToken",refresh),"",false,null)));
                saveTokens(auth);
            }
            return call(PLATFORM+path,method,body,secrets.get("access"),false,null);
        }
    }
    void login(String email,String password) throws Exception {
        JSONObject data=object(unwrap(call(PLATFORM+"/api/v1/auth/login","POST",obj("account",email,"password",password),"",false,null)));
        saveTokens(data); secrets.put("email",email);
    }
    private void saveTokens(JSONObject data) throws Exception {
        String access=str(data,"accessToken");
        if(access.isEmpty())throw new IOException("服务端未返回有效登录凭据");
        secrets.put("access",access);secrets.put("refresh",str(data,"refreshToken"));
    }
    String base() {return secrets.get("instance");}
    boolean connected(){return !base().isEmpty();}
    Object instance(String method,String path,Object body) throws Exception { return instance(method,path,body,null); }
    Object instance(String method,String path,Object body,String etag) throws Exception {
        if(!connected())throw new IOException("请先在控制台连接你的 Agent 实例");
        if(!path.startsWith("/")||path.startsWith("//"))throw new IOException("实例接口地址不正确");
        try{return call(base()+"/api"+path,method,body,secrets.get("instanceToken"),true,etag);}
        catch(HttpFailure expired){
            String app=secrets.get("appId");
            if(expired.code!=401||app.isEmpty())throw expired;
            synchronized(this){connectFromApp(app);}
            return call(base()+"/api"+path,method,body,secrets.get("instanceToken"),true,etag);
        }
    }
    void connectFromApp(String appId) throws Exception {
        JSONObject status=object(unwrap(platform("GET","/api/v1/app/get?appId="+Uri.encode(appId),null)));
        String access=str(status,"accessUrl");
        String path=ConsoleSession.platformAccessPath(access);
        JSONObject data=object(unwrap(platform("GET",path,null)));
        String console=str(data,"console_base_url");
        configureInstance(console,"",true);secrets.put("appId",appId);
    }
    private void persistCookies(ConsoleSession.OriginCookies jar){
        try{secrets.put("instanceCookies",new JSONArray(jar.serialize()).toString());}catch(Exception ignored){/* Keep the live session; reconnect obtains a fresh one. */}
    }
    void configureInstance(String address,String token) throws Exception {configureInstance(address,token,false);}
    private void configureInstance(String address,String token,boolean managed) throws Exception {
        ConsoleSession session=ConsoleSession.open(http,address,token,managed);
        String base=session.base.toString().replaceAll("/+$","");
        // Only replace a working connection after both gateway and runtime checks succeed.
        secrets.put("instance",base);secrets.put("instanceToken",session.token);
        instanceCookies=session.cookies;persistCookies(session.cookies);
        secrets.put("appId","");
    }
    Request request(String url,String method,Object body,String token,boolean scoped,String etag) {
        Request.Builder b=new Request.Builder().url(url).header("Accept","application/json");
        if(!token.isEmpty())b.header("Authorization","Bearer "+token);
        if(scoped)b.header("X-Agent-Id",requestAgent.get()==null?agent:requestAgent.get());
        if(etag!=null&&!etag.isEmpty())b.header("If-Match",etag);
        RequestBody data=body==null?null:RequestBody.create(body.toString(),JSON);
        if(data==null&&!method.equals("GET")&&!method.equals("HEAD"))data=RequestBody.create("",JSON);
        return b.method(method,data).build();
    }
    Object call(String url,String method,Object body,String token,boolean scoped,String etag) throws Exception {
        try(Response r=http.newCall(request(url,method,body,token,scoped,etag)).execute()) {
            String raw=r.body()==null?"":r.body().string();
            if(!r.isSuccessful())throw new HttpFailure(r.code(),errorMessage(raw,r.code()));
            if(raw.trim().isEmpty())return new JSONObject();
            Object parsed;
            if(raw.trim().startsWith("<"))throw new IOException("接口返回了访问入口页面，请重新连接 Agent 实例");
            try{parsed=new JSONTokener(raw).nextValue();}catch(JSONException e){throw new IOException("服务器响应无法识别");}
            if(parsed instanceof JSONObject) {
                JSONObject o=(JSONObject)parsed;
                if(o.optBoolean("success",true)==false)throw new IOException(text(o.opt("message")));
            }
            return parsed;
        }
    }
    private static String errorMessage(String raw,int code) {
        if(code==401)return "登录已失效或没有访问权限（401）";
        if(code==403)return "服务端拒绝访问（403）";
        if(code==404)return "此版本服务端不支持该功能（404）";
        if(code==409||code==412)return "配置或文件已变化，请刷新后重试（"+code+"）";
        try{JSONObject o=new JSONObject(raw);String msg=text(o.opt("detail"));if(msg.isEmpty())msg=text(o.opt("message"));if(!msg.isEmpty())return msg;}catch(JSONException ignored){}
        return "请求失败（"+code+"），请重试";
    }
    interface StreamListener {void event(JSONObject e);void done(Exception failure);}
    Call stream(JSONObject body,StreamListener listener) {
        OkHttpClient client=http.newBuilder().readTimeout(0,TimeUnit.SECONDS).build();
        Call call=client.newCall(request(base()+"/api/console/chat","POST",body,secrets.get("instanceToken"),true,null));
        call.enqueue(new Callback(){
            public void onFailure(Call c,IOException e){listener.done(e);}
            public void onResponse(Call c,Response response){
                try(Response r=response){
                    if(!r.isSuccessful()||r.body()==null)throw new IOException("对话请求失败（"+r.code()+"）");
                    BufferedReader reader=new BufferedReader(r.body().charStream());
                    String line;StringBuilder data=new StringBuilder();
                    while((line=reader.readLine())!=null){
                        if(line.isEmpty()){dispatch(data,listener);data.setLength(0);}
                        else if(line.startsWith("data:")){if(data.length()>0)data.append('\n');data.append(line.substring(5).trim());}
                    }
                    dispatch(data,listener);listener.done(null);
                }catch(Exception e){listener.done(e);}
            }
        });return call;
    }
    private static void dispatch(StringBuilder data,StreamListener listener) {
        String value=data.toString();if(value.isEmpty()||value.equals("[DONE]"))return;
        try{listener.event(new JSONObject(value));}catch(JSONException ignored){}
    }
    static final class HttpFailure extends IOException { final int code; HttpFailure(int c,String message){super(message);code=c;} }
}
