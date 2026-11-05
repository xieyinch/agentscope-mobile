package io.github.xieyinch.agentscope;

import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;

/** Native UI smoke checks without a signed-in account. Synthetic fixture is confined to the test APK. */
@RunWith(AndroidJUnit4.class)
public class NativeUiTest {
    private volatile View screenshotRoot;
    private View find(View v,String text){
        if(v instanceof TextView && ((TextView)v).getText().toString().equals(text))return v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),text);if(found!=null)return found;}}
        return null;
    }
    private void tap(MainActivity a,String text){View v=find(a.getWindow().getDecorView(),text);assertNotNull("Missing "+text,v);View target=v;while(target!=null&&!target.isClickable())target=target.getParent() instanceof View?(View)target.getParent():null;assertNotNull(target);target.performClick();}
    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        java.util.concurrent.CountDownLatch frame=new java.util.concurrent.CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->screenshotRoot.postOnAnimation(()->screenshotRoot.postOnAnimation(frame::countDown)));
        assertTrue("Native frame did not settle",frame.await(3,java.util.concurrent.TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        if(android.os.Build.VERSION.SDK_INT<29)throw new AssertionError("Screenshot worker requires API 29+");
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.ContentValues values=new android.content.ContentValues();
        values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,name+".png");
        values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");
        values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/agentscope-ui-preview");
        android.net.Uri output=context.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);assertNotNull(output);
        try(OutputStream out=context.getContentResolver().openOutputStream(output)){assertNotNull(out);bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    @Test public void expandsAndCollapsesFullComment() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{try{
                screenshotRoot=a.getWindow().getDecorView();
                java.lang.reflect.Method start=MainActivity.class.getDeclaredMethod("start",String.class);start.setAccessible(true);start.invoke(a,"回复交互测试");
                java.lang.reflect.Field field=MainActivity.class.getDeclaredField("body");field.setAccessible(true);LinearLayout body=(LinearLayout)field.get(a);
                java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("commentView",LinearLayout.class,org.json.JSONObject.class);render.setAccessible(true);
                render.invoke(a,body,Api.obj("author_name","测试回复","content","第一行\n第二行\n第三行\n第四行\n第五行\n最后一行：完整内容可复制"));
            }catch(Exception e){throw new AssertionError(e);}});
            screenshot("07-comment-collapsed");
            scenario.onActivity(a->{View toggle=find(a.getWindow().getDecorView(),"展开全文");assertNotNull(toggle);assertEquals(View.VISIBLE,toggle.getVisibility());tap(a,"展开全文");TextView content=(TextView)find(a.getWindow().getDecorView(),"第一行\n第二行\n第三行\n第四行\n第五行\n最后一行：完整内容可复制");assertEquals(Integer.MAX_VALUE,content.getMaxLines());assertTrue(content.isTextSelectable());});
            screenshot("08-comment-expanded");
            scenario.onActivity(a->{tap(a,"收起");assertNotNull(find(a.getWindow().getDecorView(),"展开全文"));});
        }
    }
    @Test public void preservesPerTabNavigationAndCapturesNativeScreens() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{screenshotRoot=a.getWindow().getDecorView();new SecretStore(a).clear();});
            screenshot("01-overview");
            scenario.onActivity(a->tap(a,"连接你的 Agent"));
            scenario.onActivity(a->assertNotNull(find(a.getWindow().getDecorView(),"验证并连接")));
            scenario.onActivity(a->tap(a,"社区"));
            scenario.onActivity(a->tap(a,"总览"));
            scenario.onActivity(a->assertNotNull("Tab must restore its connection subpage",find(a.getWindow().getDecorView(),"验证并连接")));
            screenshot("02-connect");
            scenario.onActivity(a->tap(a,"我的"));scenario.onActivity(a->tap(a,"登录账户"));scenario.onActivity(a->assertNotNull(find(a.getWindow().getDecorView(),"欢迎回来")));
            screenshot("03-login");
            scenario.onActivity(a->{try{new SecretStore(a).put("instance","https://example.invalid");}catch(Exception e){throw new AssertionError(e);}tap(a,"管理");});
            scenario.onActivity(a->assertNotNull(find(a.getWindow().getDecorView(),"运行配置")));
            screenshot("04-agent-settings");
            scenario.onActivity(a->{tap(a,"总览");a.onBackPressed();});
            screenshot("05-connected-overview");
            scenario.onActivity(a->{try{java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("deploymentActions",org.json.JSONObject.class);method.setAccessible(true);method.invoke(a,Api.obj("appId","synthetic-preview"));}catch(Exception e){throw new AssertionError(e);}});
            screenshot("06-deployment-management");
            scenario.onActivity(a->new SecretStore(a).clear());
        }
    }
}
