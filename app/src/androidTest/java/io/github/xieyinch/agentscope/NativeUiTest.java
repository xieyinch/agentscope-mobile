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

/** Offline native UI smoke checks. Synthetic fixture is confined to the test APK. */
@RunWith(AndroidJUnit4.class)
public class NativeUiTest {
    private View find(View v,String text){
        if(v instanceof TextView && ((TextView)v).getText().toString().equals(text))return v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),text);if(found!=null)return found;}}
        return null;
    }
    private void tap(MainActivity a,String text){View v=find(a.getWindow().getDecorView(),text);assertNotNull("Missing "+text,v);View target=v;while(target!=null&&!target.isClickable())target=target.getParent() instanceof View?(View)target.getParent():null;assertNotNull(target);target.performClick();}
    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        File dir=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"ui-preview");assertTrue(dir.mkdirs()||dir.isDirectory());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    @Test public void preservesPerTabNavigationAndCapturesNativeScreens() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{new SecretStore(a).clear();});
            screenshot("01-overview");
            scenario.onActivity(a->tap(a,"连接你的 Agent"));
            scenario.onActivity(a->assertNotNull(find(a.getWindow().getDecorView(),"验证并连接")));
            scenario.onActivity(a->tap(a,"社区"));
            scenario.onActivity(a->tap(a,"总览"));
            scenario.onActivity(a->assertNotNull("Tab must restore its connection subpage",find(a.getWindow().getDecorView(),"验证并连接")));
            screenshot("02-connect");
            scenario.onActivity(a->tap(a,"我的"));scenario.onActivity(a->tap(a,"登录账户"));
            screenshot("03-login");
            scenario.onActivity(a->{try{new SecretStore(a).put("instance","https://example.invalid");}catch(Exception e){throw new AssertionError(e);}tap(a,"管理");});
            screenshot("04-agent-settings");
            scenario.onActivity(a->new SecretStore(a).clear());
        }
    }
}
