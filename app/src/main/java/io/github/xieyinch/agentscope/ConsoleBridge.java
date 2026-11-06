package io.github.xieyinch.agentscope;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import cn.qwenpaw.android.ApiClient;
import cn.qwenpaw.android.PawApplication;
import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Connects the native console to the existing instance session, never the platform Bearer token. */
public final class ConsoleBridge {
    private ConsoleBridge(){}
    private static final java.util.List<java.lang.ref.WeakReference<Api>> sessions=new java.util.ArrayList<>();
    private static final java.util.List<java.lang.ref.WeakReference<OkHttpClient>> clients=new java.util.ArrayList<>();
    public static OkHttpClient transport(Api api){
        return api.http.newBuilder().readTimeout(90,TimeUnit.SECONDS).writeTimeout(90,TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .addInterceptor(chain->{Request request=chain.request();Response response=chain.proceed(request);
                HttpUrl instance=HttpUrl.parse(api.base());String appId=api.secrets.get("appId");
                if(response.code()!=401||instance==null||appId.isEmpty()||!ConsoleSession.sameOrigin(instance,request.url()))return response;
                response.close();synchronized(api){try{api.connectFromApp(appId);}catch(Exception error){throw new IOException("平台工作空间会话刷新失败，请返回工作空间重新连接",error);}}
                HttpUrl renewed=HttpUrl.parse(api.base());if(renewed==null||!ConsoleSession.sameOrigin(instance,renewed))throw new IOException("实例地址已变化，请返回工作空间重新连接");
                Request.Builder retry=request.newBuilder().removeHeader("Authorization");String token=api.secrets.get("instanceToken");if(!token.isEmpty())retry.header("Authorization","Bearer "+token);
                return chain.proceed(retry.build());
            }).build();
    }
    public static void open(Activity activity){if(activity instanceof MainActivity shell){shell.showPlatformTab(1);return;}open(activity,cn.qwenpaw.android.MainActivity.class,-1);}
    public static void prepare(Activity activity) throws Exception {
        prepare(activity,new Api(new SecretStore(activity)));
    }
    static void prepare(Activity activity,Api api) throws Exception {
        if(!api.connected())throw new IOException("请先连接工作空间");
        OkHttpClient transport=transport(api);
        synchronized(ConsoleBridge.class){sessions.removeIf(ref->ref.get()==null);clients.removeIf(ref->ref.get()==null);sessions.add(new java.lang.ref.WeakReference<>(api));clients.add(new java.lang.ref.WeakReference<>(transport));}
        ((PawApplication)activity.getApplication()).ensureChat().bindManaged(api.base(),api.secrets.get("instanceToken"),activity.getSharedPreferences("MainActivity",0).getString("agent","default"));
        ApiClient.useTransport(transport);
    }
    public static void open(Activity activity,Class<?> page,int section){
        Api api=new Api(new SecretStore(activity));if(!api.connected()){Toast.makeText(activity,"请先连接工作空间",Toast.LENGTH_SHORT).show();return;}
        try{
            prepare(activity);Intent intent=new Intent(activity,page);if(section>=0)intent.putExtra("page",section);if(page==cn.qwenpaw.android.SkillsActivity.class&&section==1)intent.putExtra("pool",true);activity.startActivity(intent);
        }catch(Exception error){Toast.makeText(activity,"控制台暂时无法打开："+error.getMessage(),Toast.LENGTH_LONG).show();}
    }
    public static boolean busy(Context context){PawApplication app=(PawApplication)context.getApplicationContext();return app.chat!=null&&(app.chat.running||app.chat.busy||app.chat.uploads>0);}
    public static void selectAgent(Context context,String base,String agent){PawApplication app=(PawApplication)context.getApplicationContext();if(app.chat!=null&&app.chat.loggedIn&&app.chat.base.equals(base)&&!app.chat.agent.equals(agent))app.chat.selectAgent(agent,true);}
    public static void disconnect(Context context){
        PawApplication app=(PawApplication)context.getApplicationContext();Runnable clear=()->{if(app.chat!=null)app.chat.closeManaged();app.chat=null;};if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())clear.run();else new android.os.Handler(android.os.Looper.getMainLooper()).post(clear);
        synchronized(ConsoleBridge.class){for(java.lang.ref.WeakReference<OkHttpClient> ref:clients){OkHttpClient client=ref.get();if(client!=null){client.dispatcher().cancelAll();client.connectionPool().evictAll();}}for(java.lang.ref.WeakReference<Api> ref:sessions){Api session=ref.get();if(session!=null)session.clearMemorySession();}sessions.clear();clients.clear();}
        ApiClient.useTransport(new OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(90,TimeUnit.SECONDS).writeTimeout(90,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build());
    }
}
