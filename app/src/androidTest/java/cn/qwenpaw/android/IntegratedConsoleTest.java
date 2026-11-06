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
    private OkHttpClient fixture;
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
        ApiClient.useTransport(fixture=new OkHttpClient.Builder().addInterceptor(chain->{try{Request request=chain.request();if(request.url().host().equals("console-fixture.invalid")){assertEquals("Bearer fixture-instance-token",request.header("Authorization"));assertEquals("default",request.header("X-Agent-Id"));}String body;
            if(request.url().encodedPath().equals("/api/console/chat")){okio.Buffer buffer=new okio.Buffer();request.body().writeTo(buffer);sent.set(Json.object(Json.parse(buffer.readUtf8())));body="data: "+message()+"\n\ndata: {\"object\":\"response\",\"status\":\"completed\"}\n\n";}
            else body=payload(request).toString();
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").header("Content-Type",request.url().encodedPath().equals("/api/console/chat")?"text/event-stream":"application/json").body(ResponseBody.create(body,MediaType.get(request.url().encodedPath().equals("/api/console/chat")?"text/event-stream":"application/json"))).build();
        }catch(Exception error){throw new IOException(error);}}).build());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{controller=app.ensureChat();controller.bindManaged("https://console-fixture.invalid","fixture-instance-token","default");}catch(Exception error){throw new AssertionError(error);}});
    }
    @After public void clear(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{controller.forgetManaged();io.github.xieyinch.agentscope.PlatformFixture.clear(InstrumentationRegistry.getInstrumentation().getTargetContext());androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);});}
    private void await(java.util.function.BooleanSupplier condition) throws Exception {long deadline=System.currentTimeMillis()+7000;while(System.currentTimeMillis()<deadline){AtomicBoolean ready=new AtomicBoolean();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ready.set(condition.getAsBoolean()));if(ready.get())return;Thread.sleep(30);}fail("Native console state did not settle: "+controller.status+"; running="+controller.running+"; busy="+controller.busy+"; sent="+(sent.get()!=null)+"; rows="+controller.transcript.rows().size());}
    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();java.util.concurrent.CountDownLatch frame=new java.util.concurrent.CountDownLatch(1);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{java.util.Collection<android.app.Activity> activities=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED);assertFalse(activities.isEmpty());View root=activities.iterator().next().getWindow().getDecorView();root.postOnAnimation(()->root.postOnAnimation(frame::countDown));});assertTrue(frame.await(3,TimeUnit.SECONDS));InstrumentationRegistry.getInstrumentation().waitForIdleSync();android.os.SystemClock.sleep(250);Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();ContentValues values=new ContentValues();values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,name+".png");values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/agentscope-ui-preview");android.net.Uri uri=context.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);try(OutputStream out=context.getContentResolver().openOutputStream(uri)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    private View find(View node,String label){
        if(label.equals(String.valueOf(node.getContentDescription()))||node instanceof TextView t&&label.contentEquals(t.getText()))return node;
        if(node instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),label);if(result!=null)return result;}
        return null;
    }
    @Test public void chatLivesInsidePlatformTabsAndRetainsDraftAcrossNavigationAndRecreation() throws Exception {
        try(ActivityScenario<io.github.xieyinch.agentscope.MainActivity> scene=ActivityScenario.launch(io.github.xieyinch.agentscope.MainActivity.class)){
            AtomicReference<io.github.xieyinch.agentscope.MainActivity> shell=new AtomicReference<>();
            scene.onActivity(a->{shell.set(a);try{io.github.xieyinch.agentscope.PlatformFixture.bind(a,fixture);}catch(Exception e){throw new AssertionError(e);}a.showPlatformTab(1);});
            await(()->controller.agents.length()>0&&controller.providers.length()>0);
            scene.onActivity(a->{assertNotNull(a.getSupportFragmentManager().findFragmentByTag("embedded-console"));for(String tab:new String[]{"总览","对话","管理","社区","我的"}){View v=find(a.getWindow().getDecorView(),tab);assertNotNull(tab,v);assertTrue(tab,v.isShown());}assertNull(find(a.getWindow().getDecorView(),"返回平台工作空间"));});
            screenshot("19-platform-chat-tab");
            scene.onActivity(a->{try{ConsoleFragment fragment=(ConsoleFragment)a.getSupportFragmentManager().findFragmentByTag("embedded-console");java.lang.reflect.Field field=ConsoleFragment.class.getDeclaredField("input");field.setAccessible(true);EditText input=(EditText)field.get(fragment);input.setText("跨标签保留草稿");input.requestFocus();((android.view.inputmethod.InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);}catch(Exception e){throw new AssertionError(e);}});
            await(()->{androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(shell.get().getWindow().getDecorView());return insets!=null&&insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime());});
            scene.onActivity(a->{try{ConsoleFragment fragment=(ConsoleFragment)a.getSupportFragmentManager().findFragmentByTag("embedded-console");java.lang.reflect.Field field=ConsoleFragment.class.getDeclaredField("input");field.setAccessible(true);View editor=(View)field.get(fragment);android.graphics.Rect frame=new android.graphics.Rect();a.getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);int[] position=new int[2];editor.getLocationOnScreen(position);assertTrue("Embedded input covered by keyboard",position[1]+editor.getHeight()<=frame.bottom);assertFalse(find(a.getWindow().getDecorView(),"社区").isShown());}catch(Exception e){throw new AssertionError(e);}});
            screenshot("20-platform-chat-keyboard");
            scene.onActivity(a->((android.view.inputmethod.InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(a.getWindow().getDecorView().getWindowToken(),0));
            await(()->{androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(shell.get().getWindow().getDecorView());return insets!=null&&!insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime());});
            for(int tab:new int[]{2,3,4,0,1})scene.onActivity(a->{a.showPlatformTab(tab);if(tab==1){assertEquals("跨标签保留草稿",controller.draft);assertTrue(find(a.getWindow().getDecorView(),"社区").isShown());}});
            scene.recreate();scene.onActivity(a->{shell.set(a);try{io.github.xieyinch.agentscope.PlatformFixture.bind(a,fixture);}catch(Exception e){throw new AssertionError(e);}assertNotNull(a.getSupportFragmentManager().findFragmentByTag("embedded-console"));assertEquals("跨标签保留草稿",controller.draft);assertTrue(find(a.getWindow().getDecorView(),"社区").isShown());a.onBackPressed();assertNotNull(find(a.getWindow().getDecorView(),"Agent 控制台"));io.github.xieyinch.agentscope.PlatformFixture.clear(a);});
        }
    }
    @Test public void managedSessionRendersCommandsAndPersistsChatAcrossActivityRecreation() throws Exception {
        try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(MainActivity.class)){
            await(()->controller.agents.length()>0&&controller.providers.length()>0);
            screenshot("14-integrated-console");
            scene.onActivity(a->{try{java.lang.reflect.Field field=ConsoleFragment.class.getDeclaredField("input");field.setAccessible(true);EditText input=(EditText)field.get(a.console());input.requestFocus();input.setText("整合控制台输入测试");((android.view.inputmethod.InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);}catch(Exception error){throw new AssertionError(error);}});
            AtomicReference<MainActivity> visible=new AtomicReference<>();scene.onActivity(visible::set);
            await(()->{androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(visible.get().getWindow().getDecorView());return insets!=null&&insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime());});
            scene.onActivity(a->{try{java.lang.reflect.Field field=ConsoleFragment.class.getDeclaredField("input");field.setAccessible(true);View input=(View)field.get(a.console());android.graphics.Rect frame=new android.graphics.Rect();a.getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);int[] position=new int[2];input.getLocationOnScreen(position);assertTrue("Integrated composer covered by IME",position[1]>=frame.top&&position[1]+input.getHeight()<=frame.bottom);}catch(Exception error){throw new AssertionError(error);}});
            screenshot("17-integrated-keyboard");
            scene.onActivity(a->((android.view.inputmethod.InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(a.getWindow().getDecorView().getWindowToken(),0));

            scene.onActivity(a->{controller.draft="你好，请帮我整理文档";controller.send();});
            await(()->sent.get()!=null&&!controller.running&&!controller.busy&&controller.transcript.rows().stream().anyMatch(row->row.role.equals("assistant")&&row.text.contains("Markdown")));
            assertEquals("已完成",controller.status);assertEquals("",controller.transcript.error);
            assertEquals("user",sent.get().getJSONArray("input").getJSONObject(0).getString("role"));
            screenshot("15-integrated-messages");
            scene.onActivity(a->{controller.draft="保留未发送草稿";controller.saveDraft();});scene.recreate();
            scene.onActivity(a->assertEquals("保留未发送草稿",controller.draft));
            AtomicReference<MainActivity> parent=new AtomicReference<>();scene.onActivity(parent::set);
            android.app.Instrumentation.ActivityMonitor monitor=InstrumentationRegistry.getInstrumentation().addMonitor(SettingsActivity.class.getName(),null,false);
            scene.onActivity(a->a.startActivity(new Intent(a,SettingsActivity.class).putExtra("page",0)));
            android.app.Activity settings=monitor.waitForActivityWithTimeout(5000);assertNotNull("Settings page did not open",settings);InstrumentationRegistry.getInstrumentation().removeMonitor(monitor);
            java.lang.reflect.Field config=SettingsActivity.class.getDeclaredField("config");config.setAccessible(true);
            await(()->{try{return config.get(settings)!=null;}catch(IllegalAccessException error){throw new AssertionError(error);}});
            screenshot("16-integrated-settings");
            InstrumentationRegistry.getInstrumentation().runOnMainSync(settings::finish);
            await(()->androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).contains(parent.get()));

        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES));
        try(ActivityScenario<MainActivity> dark=ActivityScenario.launch(MainActivity.class)){
            dark.onActivity(a->{assertEquals(0xff000000,Ui.BG);assertEquals(0xff0a84ff,ConsoleFragment.ACCENT);assertEquals(0xfff2f2f7,ConsoleFragment.INK);try{java.lang.reflect.Method ink=ConsoleFragment.class.getDeclaredMethod("levelInk",String.class),background=ConsoleFragment.class.getDeclaredMethod("levelBackground",String.class);ink.setAccessible(true);background.setAccessible(true);assertTrue(androidx.core.graphics.ColorUtils.calculateContrast((Integer)ink.invoke(a.console(),"AUTO"),(Integer)background.invoke(a.console(),"AUTO"))>=4.5);}catch(Exception error){throw new AssertionError(error);}});
            screenshot("18-integrated-dark");
        }
    }
}
