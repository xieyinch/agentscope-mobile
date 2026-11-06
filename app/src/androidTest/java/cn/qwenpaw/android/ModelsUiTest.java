package cn.qwenpaw.android;

import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ModelsUiTest {
    private ChatController chat;
    private final java.util.concurrent.CountDownLatch languageGate=new java.util.concurrent.CountDownLatch(1);
    private JSONObject provider;
    private final AtomicReference<JSONObject> saved=new AtomicReference<>();
    private final AtomicReference<JSONObject> imported=new AtomicReference<>();
    @Before public void setup(){
        provider=Json.obj("id","fixture","name","测试供应商","base_url","https://provider.example/v1","api_key","configured","enabled",true,"support_model_discovery",true,"models",Json.arr(Json.obj("id","base-model","name","基础模型","generate_kwargs",Json.obj("custom_option",true))),"extra_models",new JSONArray());
        ApiClient.useTransport(new OkHttpClient.Builder().addInterceptor(chain->{Request r=chain.request();String path=r.url().encodedPath();int code=200;Object data=new JSONObject();
            try{
                if(path.equals("/api/workspace/running-config"))data=Json.obj("shell_command_timeout",60,"shell_command_executable","/bin/bash","memory_manager_backend","simple","auto_title_config",Json.obj("enabled",true));
                else if(path.equals("/api/workspace/language")){languageGate.await(5,java.util.concurrent.TimeUnit.SECONDS);data=Json.obj("language","zh");}
                else if(path.equals("/api/agents/memory/backends"))data=Json.arr(Json.obj("id","simple","label","基础记忆","available",true));
                else if(path.equals("/api/config/user-timezone")){code=404;data=Json.obj("detail","unsupported");}
                else if(path.equals("/api/models"))data=Json.arr(provider);
                else if(path.equals("/api/models/active"))data=Json.obj("active_llm",Json.obj("provider_id","fixture","model","base-model"));
                else if(path.endsWith("/test"))data=Json.obj("success",false,"message","连接测试失败：密钥无效");
                else if(path.endsWith("/discover")){assertEquals("false",r.url().queryParameter("save"));data=Json.obj("success",true,"models",Json.arr(Json.obj("id","vendor/new-model","name","发现的新模型")));}
                else if(path.equals("/api/models/fixture/models")&&r.method().equals("POST")){okio.Buffer b=new okio.Buffer();r.body().writeTo(b);JSONObject body=Json.object(Json.parse(b.readUtf8()));imported.set(body);provider.optJSONArray("extra_models").put(body);data=provider;}
                else if(path.equals("/api/models/fixture/config")){okio.Buffer b=new okio.Buffer();r.body().writeTo(b);saved.set(Json.object(Json.parse(b.readUtf8())));data=provider;}
                else if(path.equals("/api/envs"))data=Json.arr(Json.obj("key","EXAMPLE_VARIABLE","value","fixture-value"));
                else if(path.equals("/api/envs/catalog")){code=404;data=Json.obj("detail","unsupported");}
                return new Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message(code==200?"OK":"Not Found").header("Content-Type","application/json").body(ResponseBody.create(data.toString(),MediaType.get("application/json"))).build();
            }catch(Exception e){throw new IOException(e);}
        }).build());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{chat=((PawApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext()).ensureChat();chat.bindManaged("https://models-fixture.invalid","fixture-runtime-token","default");}catch(Exception e){throw new AssertionError(e);}});
    }
    @After public void clear(){languageGate.countDown();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->chat.forgetManaged());}
    private View find(View root,String label){if(label.equals(String.valueOf(root.getContentDescription()))||root instanceof TextView t&&label.contentEquals(t.getText()))return root;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),label);if(found!=null)return found;}return null;}
    private ListView list(View root){if(root instanceof ListView l)return l;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){ListView result=list(g.getChildAt(i));if(result!=null)return result;}return null;}
    private void await(java.util.function.BooleanSupplier ready)throws Exception{for(int i=0;i<180;i++){AtomicBoolean ok=new AtomicBoolean();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ok.set(ready.getAsBoolean()));if(ok.get())return;Thread.sleep(30);}fail("Model UI did not settle");}
    private AlertDialog editor(ModelsActivity a){try{java.lang.reflect.Field f=ModelsActivity.class.getDeclaredField("editor");f.setAccessible(true);return (AlertDialog)f.get(a);}catch(Exception e){throw new AssertionError(e);}}
    private void screenshot(String name)throws Exception{InstrumentationRegistry.getInstrumentation().waitForIdleSync();android.os.SystemClock.sleep(300);Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();ContentValues v=new ContentValues();v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,name+".png");v.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/agentscope-ui-preview");android.net.Uri uri=c.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);try(OutputStream out=c.getContentResolver().openOutputStream(uri)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    @Test public void failedConnectionPreservesInputAndDiscoveryImportsOnlySelectedModels()throws Exception{
        AtomicReference<ModelsActivity> activity=new AtomicReference<>();
        try(ActivityScenario<ModelsActivity> scene=ActivityScenario.launch(ModelsActivity.class)){
            scene.onActivity(activity::set);await(()->{ListView l=list(activity.get().getWindow().getDecorView());return l!=null&&l.getAdapter().getCount()==1&&find(activity.get().getWindow().getDecorView(),"刷新模型列表").isEnabled();});
            scene.onActivity(a->{ListView l=list(a.getWindow().getDecorView());l.performItemClick(l.getChildAt(0),0,0);});screenshot("21-model-provider");
            scene.onActivity(a->{find(a.getWindow().getDecorView(),"连接设置").performClick();AlertDialog d=editor(a);((EditText)find(d.getWindow().getDecorView(),"新 API Key（留空保留现有密钥）")).setText("fixture-unsaved-key");find(d.getWindow().getDecorView(),"测试连接").performClick();});
            await(()->find(activity.get().getWindow().getDecorView(),"连接测试失败：密钥无效")!=null);
            scene.onActivity(a->{AlertDialog d=editor(a);assertTrue(d.isShowing());assertEquals("fixture-unsaved-key",((EditText)find(d.getWindow().getDecorView(),"新 API Key（留空保留现有密钥）")).getText().toString());((EditText)find(d.getWindow().getDecorView(),"新 API Key（留空保留现有密钥）")).setText("");find(d.getWindow().getDecorView(),"保存连接设置").performClick();});
            await(()->saved.get()!=null&&editor(activity.get())==null&&find(activity.get().getWindow().getDecorView(),"自动发现").isEnabled());assertFalse(saved.get().has("api_key"));assertFalse(saved.get().optBoolean("auto_discover",true));
            scene.onActivity(a->find(a.getWindow().getDecorView(),"自动发现").performClick());
            AtomicReference<AlertDialog> preview=new AtomicReference<>();await(()->{try{java.lang.reflect.Field f=ModelsActivity.class.getDeclaredField("discoveryDialog");f.setAccessible(true);preview.set((AlertDialog)f.get(activity.get()));return preview.get()!=null&&preview.get().isShowing();}catch(Exception e){throw new AssertionError(e);}});
            scene.onActivity(a->find(preview.get().getWindow().getDecorView(),"全选").performClick());screenshot("22-model-discovery");scene.onActivity(a->preview.get().getButton(AlertDialog.BUTTON_POSITIVE).performClick());await(()->imported.get()!=null);assertEquals("vendor/new-model",imported.get().optString("id"));await(()->find(activity.get().getWindow().getDecorView(),"已添加 1 个模型")!=null);scene.onActivity(a->assertEquals(2,list(a.getWindow().getDecorView()).getAdapter().getCount()));screenshot("24-model-imported");
        }
    }
    @Test public void optionalConfigurationResultsDoNotReplaceFocusedRuntimeEdits()throws Exception{
        AtomicReference<SettingsActivity> activity=new AtomicReference<>();AtomicReference<EditText> input=new AtomicReference<>();
        try(ActivityScenario<SettingsActivity> scene=ActivityScenario.launch(SettingsActivity.class)){
            scene.onActivity(activity::set);await(()->activity.get().getWindow().getDecorView().findViewWithTag("shell_command_timeout")!=null);
            scene.onActivity(a->{EditText field=(EditText)a.getWindow().getDecorView().findViewWithTag("shell_command_timeout");input.set(field);field.requestFocus();field.setText("321");});languageGate.countDown();
            await(()->{try{java.lang.reflect.Field f=SettingsActivity.class.getDeclaredField("extrasPending");f.setAccessible(true);return f.getInt(activity.get())==0;}catch(Exception e){throw new AssertionError(e);}});
            scene.onActivity(a->{assertSame(input.get(),a.getWindow().getDecorView().findViewWithTag("shell_command_timeout"));assertEquals("321",input.get().getText().toString());assertTrue(input.get().hasFocus());assertNotNull(find(a.getWindow().getDecorView(),"用户时区：当前服务器未提供"));});
        }
    }
    @Test public void environmentVariablesRemainUsableWhenOptionalCatalogIsUnsupported()throws Exception{
        Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),SettingsActivity.class).putExtra("page",1);AtomicReference<SettingsActivity> activity=new AtomicReference<>();
        try(ActivityScenario<SettingsActivity> scene=ActivityScenario.launch(intent)){scene.onActivity(activity::set);await(()->find(activity.get().getWindow().getDecorView(),"EXAMPLE_VARIABLE")!=null);scene.onActivity(a->{assertNotNull(find(a.getWindow().getDecorView(),"当前服务器未提供分类目录，仅显示已配置的变量。"));assertNotNull(find(a.getWindow().getDecorView(),"编辑"));});screenshot("23-environment-compatibility");}
    }
}
