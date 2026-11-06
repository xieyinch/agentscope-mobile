package cn.qwenpaw.android;

import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** Synthetic, credential-free exercise of the integrated native console. */
@RunWith(AndroidJUnit4.class)
public class IntegratedConsoleTest {
    private ChatController controller;
    private final AtomicReference<JSONObject> sent=new AtomicReference<>();
    private JSONObject spec;
    private Object payload(Request request) throws Exception {
        String path=request.url().encodedPath();
        if(path.equals("/api/agents"))return Json.obj("agents",Json.arr(Json.obj("id","default","name","示例智能体","enabled",true,"workspace_dir","/fixture")));
        if(path.equals("/api/models/active"))return Json.obj("active_llm",Json.obj("provider_id","fixture","model","fixture-model"),"effective_max_input_length",10000);
        if(path.equals("/api/models"))return Json.arr(Json.obj("id","fixture","name","测试供应商","models",Json.arr(Json.obj("id","fixture-model","name","测试模型"))));
        if(path.equals("/api/workspace/running-config"))return Json.obj("approval_level","AUTO","max_steps",50);
        if(path.equals("/api/loops"))return Json.arr(Json.obj("id","default","source","builtin","slash_command",""));
        if(path.equals("/api/chats")&&request.method().equals("POST")){okio.Buffer buffer=new okio.Buffer();request.body().writeTo(buffer);spec=Json.object(Json.parse(buffer.readUtf8()));Json.put(spec,"id","fixture-chat");return spec;}
        if(path.equals("/api/chats"))return spec==null?new JSONArray():Json.arr(spec);
        if(path.equals("/api/chats/fixture-chat/status"))return Json.obj("status","idle");
        if(path.equals("/api/chats/fixture-chat"))return Json.obj("messages",Json.arr(message()),"status","idle");
        if(path.endsWith("/thinking")||path.startsWith("/api/chats/thinking"))return Json.obj("model","fixture-model","control",Json.obj("efforts",Json.arr("low","high")),"value",Json.obj("level","inherit"));
        if(path.equals("/api/approval/list"))return Json.obj("pending_approvals",new JSONArray());
        if(path.equals("/api/skills")||path.equals("/api/tools")||path.equals("/api/skills/pool"))return new JSONArray();
        return new JSONObject();
    }
    private JSONObject message(){return Json.obj("object","message","id","fixture-reply","type","message","role","assistant","status","completed","content",Json.arr(Json.obj("type","text","text","原生 **Markdown** 回复\n\n- 表格、代码与工具过程\n- 会话状态独立保存")));}
    @Before public void setup() throws Exception {
        PawApplication app=(PawApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        ApiClient.useTransport(new OkHttpClient.Builder().addInterceptor(chain->{try{Request request=chain.request();assertEquals("Bearer fixture-instance-token",request.header("Authorization"));assertEquals("default",request.header("X-Agent-Id"));String body;
            if(request.url().encodedPath().equals("/api/console/chat")){okio.Buffer buffer=new okio.Buffer();request.body().writeTo(buffer);sent.set(Json.object(Json.parse(buffer.readUtf8())));body="data: "+message()+"\n\ndata: {\"object\":\"response\",\"status\":\"completed\"}\n\n";}
            else body=payload(request).toString();
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create(body,MediaType.get(request.url().encodedPath().equals("/api/console/chat")?"text/event-stream":"application/json"))).build();
        }catch(Exception error){throw new IOException(error);}}).build());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{controller=app.ensureChat();controller.bindManaged("https://console-fixture.invalid","fixture-instance-token","default");}catch(Exception error){throw new AssertionError(error);}});
    }
    @After public void clear(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{controller.forgetManaged();androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);});}
    private void await(java.util.function.BooleanSupplier condition) throws Exception {long deadline=System.currentTimeMillis()+7000;while(System.currentTimeMillis()<deadline){AtomicBoolean ready=new AtomicBoolean();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ready.set(condition.getAsBoolean()));if(ready.get())return;Thread.sleep(30);}fail("Native console state did not settle: "+controller.status+"; running="+controller.running+"; busy="+controller.busy+"; sent="+(sent.get()!=null)+"; rows="+controller.transcript.rows().size());}
    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();ContentValues values=new ContentValues();values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,name+".png");values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/agentscope-ui-preview");android.net.Uri uri=context.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);try(OutputStream out=context.getContentResolver().openOutputStream(uri)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    @Test public void managedSessionRendersCommandsAndPersistsChatAcrossActivityRecreation() throws Exception {
        try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(MainActivity.class)){
            await(()->controller.agents.length()>0&&controller.providers.length()>0);
            screenshot("14-integrated-console");
            scene.onActivity(a->{try{java.lang.reflect.Field field=MainActivity.class.getDeclaredField("input");field.setAccessible(true);EditText input=(EditText)field.get(a);input.requestFocus();input.setText("整合控制台输入测试");((android.view.inputmethod.InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);}catch(Exception error){throw new AssertionError(error);}});
            AtomicReference<MainActivity> visible=new AtomicReference<>();scene.onActivity(visible::set);
            await(()->{androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(visible.get().getWindow().getDecorView());return insets!=null&&insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime());});
            scene.onActivity(a->{try{java.lang.reflect.Field field=MainActivity.class.getDeclaredField("input");field.setAccessible(true);View input=(View)field.get(a);android.graphics.Rect frame=new android.graphics.Rect();a.getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);int[] position=new int[2];input.getLocationOnScreen(position);assertTrue("Integrated composer covered by IME",position[1]>=frame.top&&position[1]+input.getHeight()<=frame.bottom);}catch(Exception error){throw new AssertionError(error);}});
            screenshot("17-integrated-keyboard");
            scene.onActivity(a->((android.view.inputmethod.InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(a.getWindow().getDecorView().getWindowToken(),0));

            scene.onActivity(a->{controller.draft="你好，请帮我整理文档";controller.send();});
            await(()->sent.get()!=null&&!controller.running&&!controller.busy&&controller.transcript.rows().size()>0);
            assertEquals("user",sent.get().getJSONArray("input").getJSONObject(0).getString("role"));
            screenshot("15-integrated-messages");
            scene.onActivity(a->{controller.draft="保留未发送草稿";controller.saveDraft();});scene.recreate();
            scene.onActivity(a->assertEquals("保留未发送草稿",controller.draft));
            try(ActivityScenario<SettingsActivity> settings=ActivityScenario.launch(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),SettingsActivity.class).putExtra("page",0))){screenshot("16-integrated-settings");}
            scene.onActivity(a->androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES));
            await(()->Ui.BG==0xff000000);
            scene.onActivity(a->{assertEquals(0xff0a84ff,MainActivity.ACCENT);assertEquals(0xfff2f2f7,MainActivity.INK);});
            screenshot("18-integrated-dark");
            scene.onActivity(a->androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO));

        }
    }
}
