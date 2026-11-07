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
    private android.app.AlertDialog modelDialog;
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
    @Test public void rendersOriginalCommentEmojiInline() throws Exception {
        byte[] gif = android.util.Base64.decode(
            "R0lGODlhFAAUAPAAAAB6/wAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQAFAAAACwAAAAAFAAUAAACEYSPqcvtD6OctNqLs968+68VACH5BAAUAAAALAAAAAAUABQAgP/MAAAAAAIRhI+py+0Po5y02ouz3rz7rxUAOw==",
            android.util.Base64.DEFAULT);
        Bitmap pngImage = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
        pngImage.eraseColor(android.graphics.Color.BLUE);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        pngImage.compress(Bitmap.CompressFormat.PNG, 100, encoded);
        pngImage.recycle();
        byte[] png = encoded.toByteArray();
        okhttp3.OkHttpClient images = new okhttp3.OkHttpClient.Builder().addInterceptor(chain -> {
            okhttp3.Request request = chain.request();
            assertEquals("img.alicdn.com", request.url().host());
            assertNull(request.header("Authorization"));
            assertNull(request.header("Cookie"));
            boolean animated = request.url().encodedPath().endsWith(".gif");
            return new okhttp3.Response.Builder().request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                .body(okhttp3.ResponseBody.create(animated ? gif : png,
                    okhttp3.MediaType.get(animated ? "image/gif" : "image/png"))).build();
        }).build();
        String displayed = "前文[表情]中间[表情] https://example.org [表情]";
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                try {
                    screenshotRoot = a.getWindow().getDecorView();
                    java.lang.reflect.Field emoji = MainActivity.class.getDeclaredField("commentEmoji");
                    emoji.setAccessible(true);
                    emoji.set(a, new CommentEmoji(images));
                    java.lang.reflect.Method start = MainActivity.class.getDeclaredMethod("start", String.class);
                    start.setAccessible(true);
                    start.invoke(a, "社区帖子");
                    java.lang.reflect.Field body = MainActivity.class.getDeclaredField("body");
                    body.setAccessible(true);
                    java.lang.reflect.Method render = MainActivity.class.getDeclaredMethod(
                        "commentView", LinearLayout.class, org.json.JSONObject.class);
                    render.setAccessible(true);
                    render.invoke(a, body.get(a), Api.obj("author_name", "测试回复", "content",
                        "前文[emoji:https://img.alicdn.com/imgextra/test.png]中间"
                        + "[emoji:https://img.alicdn.com/imgextra/test.gif] https://example.org "
                        + "[emoji:https://example.invalid/bad.png]"));
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            java.util.concurrent.atomic.AtomicReference<android.text.style.ImageSpan[]> loaded =
                new java.util.concurrent.atomic.AtomicReference<>(new android.text.style.ImageSpan[0]);
            for (int attempt = 0; attempt < 100 && loaded.get().length < 2; attempt++) {
                scenario.onActivity(a -> {
                    TextView comment = (TextView) find(screenshotRoot, displayed);
                    assertNotNull("Emoji URLs leaked into the displayed comment", comment);
                    android.text.Spanned text = (android.text.Spanned) comment.getText();
                    loaded.set(text.getSpans(0, text.length(), android.text.style.ImageSpan.class));
                    assertTrue(comment.isTextSelectable());
                });
                if (loaded.get().length < 2) Thread.sleep(50);
            }
            assertEquals(2, loaded.get().length);
            assertTrue(java.util.Arrays.stream(loaded.get()).anyMatch(
                span -> span.getDrawable() instanceof android.graphics.drawable.AnimatedImageDrawable));
            screenshot("25-community-comment-emoji");
        }
    }
    @Test public void searchesModelsAndShowsNativeChatComposer() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{try{
                screenshotRoot=a.getWindow().getDecorView();new SecretStore(a).put("instance","https://example.invalid");
                java.lang.reflect.Field apiField=MainActivity.class.getDeclaredField("api");apiField.setAccessible(true);Api api=(Api)apiField.get(a);java.lang.reflect.Field http=Api.class.getDeclaredField("http");http.setAccessible(true);http.set(api,api.http.newBuilder().addInterceptor(chain->{String path=chain.request().url().encodedPath();String response=path.equals("/api/models/active")?"{\"active_llm\":{\"provider_id\":\"p\",\"model\":\"custom\"}}":"{\"messages\":[{\"role\":\"user\",\"content\":\"请帮我分析这份文档\"},{\"role\":\"assistant\",\"content\":\"可以，添加附件后点击发送。这里是原生界面测试数据。\"}]}";return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK").body(okhttp3.ResponseBody.create(response,okhttp3.MediaType.get("application/json"))).build();}).build());
                java.lang.reflect.Method conversation=MainActivity.class.getDeclaredMethod("conversation",org.json.JSONObject.class);conversation.setAccessible(true);conversation.invoke(a,Api.obj("id","ui-chat","name","原生对话测试"));
                assertNotNull(find(screenshotRoot,"添加图片 / 文件"));assertNotNull(find(screenshotRoot,"发送"));
                java.lang.reflect.Method pending=MainActivity.class.getDeclaredMethod("pendingAttachments",String.class);pending.setAccessible(true);((org.json.JSONArray)pending.invoke(a,"ui-chat")).put(ChatContent.attachment("/media/test.pdf","测试文档.pdf","application/pdf"));
                java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("renderAttachments",String.class);render.setAccessible(true);render.invoke(a,"ui-chat");
            }catch(Exception e){throw new AssertionError(e);}});
            java.util.concurrent.CountDownLatch historyReady=new java.util.concurrent.CountDownLatch(1);
            scenario.onActivity(a->screenshotRoot.postOnAnimation(new Runnable(){@Override public void run(){if(find(screenshotRoot,"可以，添加附件后点击发送。这里是原生界面测试数据。")!=null)historyReady.countDown();else screenshotRoot.postOnAnimation(this);}}));
            assertTrue("Chat history did not render",historyReady.await(5,java.util.concurrent.TimeUnit.SECONDS));
            screenshot("09-chat-composer");
            java.util.concurrent.CountDownLatch keyboardReady=new java.util.concurrent.CountDownLatch(1);
            scenario.onActivity(a->{try{java.lang.reflect.Field compose=MainActivity.class.getDeclaredField("activeCompose");compose.setAccessible(true);EditText editor=(EditText)compose.get(a);editor.requestFocus();editor.setText("Keyboard visibility regression");((android.view.inputmethod.InputMethodManager)a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).showSoftInput(editor,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                screenshotRoot.postOnAnimation(new Runnable(){@Override public void run(){androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(screenshotRoot);if(insets!=null&&insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()))keyboardReady.countDown();else screenshotRoot.postOnAnimation(this);}});
            }catch(Exception e){throw new AssertionError(e);}});
            assertTrue("Keyboard did not open",keyboardReady.await(6,java.util.concurrent.TimeUnit.SECONDS));
            scenario.onActivity(a->{try{java.lang.reflect.Field compose=MainActivity.class.getDeclaredField("activeCompose");compose.setAccessible(true);View editor=(View)compose.get(a);android.graphics.Rect frame=new android.graphics.Rect();screenshotRoot.getWindowVisibleDisplayFrame(frame);int[] position=new int[2];editor.getLocationOnScreen(position);assertTrue("Input is covered by keyboard",position[1]+editor.getHeight()<=frame.bottom);View send=find(screenshotRoot,"发送");send.getLocationOnScreen(position);assertTrue("Send is covered by keyboard",position[1]+send.getHeight()<=frame.bottom);}catch(Exception e){throw new AssertionError(e);}});
            screenshot("11-chat-keyboard");
            scenario.onActivity(a->((android.view.inputmethod.InputMethodManager)a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(screenshotRoot.getWindowToken(),0));

            scenario.onActivity(a->{try{
                java.lang.reflect.Field streaming=MainActivity.class.getDeclaredField("streaming");streaming.setAccessible(true);streaming.set(a,new okhttp3.OkHttpClient().newCall(new okhttp3.Request.Builder().url("https://example.invalid").build()));
                java.lang.reflect.Field cid=MainActivity.class.getDeclaredField("streamingChat");cid.setAccessible(true);cid.set(a,"ui-chat");
                java.lang.reflect.Method start=MainActivity.class.getDeclaredMethod("start",String.class);start.setAccessible(true);start.invoke(a,"其他页面");
                java.lang.reflect.Method output=MainActivity.class.getDeclaredMethod("updateLiveOutput",String.class,String.class);output.setAccessible(true);output.invoke(a,"ui-chat","离开页面期间收到的流式回复");
                java.lang.reflect.Method conversation=MainActivity.class.getDeclaredMethod("conversation",org.json.JSONObject.class);conversation.setAccessible(true);conversation.invoke(a,Api.obj("id","ui-chat","name","原生对话测试"));
                assertNotNull(find(screenshotRoot,"离开页面期间收到的流式回复"));assertFalse(find(screenshotRoot,"发送").isEnabled());streaming.set(a,null);
            }catch(Exception e){throw new AssertionError(e);}});

            scenario.onActivity(a->{tap(a,"附件 · 测试文档.pdf  × 移除");assertNull(find(screenshotRoot,"附件 · 测试文档.pdf  × 移除"));try{
                java.lang.reflect.Method picker=MainActivity.class.getDeclaredMethod("showModelPicker",org.json.JSONArray.class,Object.class,Runnable.class);picker.setAccessible(true);
                modelDialog=(android.app.AlertDialog)picker.invoke(a,new org.json.JSONArray().put(Api.obj("id","p","name","示例供应商","models",new org.json.JSONArray().put(Api.obj("id","base","name","基础模型")),"extra_models",new org.json.JSONArray().put(Api.obj("id","custom","name","我的模型")))),Api.obj("active_llm",Api.obj("provider_id","p","model","custom")),(Runnable)()->{});
            }catch(Exception e){throw new AssertionError(e);}});
            scenario.onActivity(a->{ViewGroup panel=(ViewGroup)modelDialog.getWindow().getDecorView();java.util.ArrayDeque<View> queue=new java.util.ArrayDeque<>();queue.add(panel);EditText search=null;ListView models=null;while(!queue.isEmpty()){View view=queue.remove();if(view instanceof EditText)search=(EditText)view;if(view instanceof ListView)models=(ListView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)queue.add(group.getChildAt(i));}}assertNotNull(search);assertNotNull(models);assertEquals(2,models.getAdapter().getCount());search.setText("custom");assertEquals(1,models.getAdapter().getCount());assertTrue(models.getAdapter().getItem(0).toString().startsWith("✓"));});
            scenario.onActivity(a->screenshotRoot=modelDialog.getWindow().getDecorView());
            screenshot("10-model-picker");
            // The picker is a separate native dialog window.
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{});
            scenario.onActivity(a->{modelDialog.dismiss();new SecretStore(a).clear();});
        }
    }
    @Test public void rendersUnavailableAndMeasuredResourceSnapshots() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{try{screenshotRoot=a.getWindow().getDecorView();java.lang.reflect.Method start=MainActivity.class.getDeclaredMethod("start",String.class);start.setAccessible(true);start.invoke(a,"资源监控测试");java.lang.reflect.Field body=MainActivity.class.getDeclaredField("body");body.setAccessible(true);java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("renderMetrics",LinearLayout.class,org.json.JSONObject.class);render.setAccessible(true);render.invoke(a,(LinearLayout)body.get(a),Api.obj("memoryUsageMbMax",org.json.JSONObject.NULL,"vcpuCoresAvg",org.json.JSONObject.NULL));assertNotNull(find(screenshotRoot,"暂无采样"));assertNull(find(screenshotRoot,"memoryUsageMbMax"));}catch(Exception e){throw new AssertionError(e);}});
            screenshot("12-resources-unavailable");
            scenario.onActivity(a->{try{java.lang.reflect.Field body=MainActivity.class.getDeclaredField("body");body.setAccessible(true);java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("renderMetrics",LinearLayout.class,org.json.JSONObject.class);render.setAccessible(true);render.invoke(a,(LinearLayout)body.get(a),Api.obj("memoryUsageMbMax",256,"memoryUsageMbAvg",128.5,"vcpuCoresMax",0.5,"vcpuCoresAvg",0,"vcpuUtilizationMax",25,"memoryUtilizationMax",12.5));assertNotNull(find(screenshotRoot,ResourceMetrics.format(Api.obj("memoryUsageMbMax",256),"memoryUsageMbMax","MB")));}catch(Exception e){throw new AssertionError(e);}});
            screenshot("13-resources-sample");
        }
    }
    @Test public void removingConfigurationFieldPreservesOtherValues() throws Exception {
        try(ActivityScenario<MainActivity> scene=ActivityScenario.launch(MainActivity.class)){scene.onActivity(a->{try{NativeForm form=new NativeForm(a,Api.obj("Authorization","fixture-secret","X-Mode","custom","max_tokens",128),true);form.removeField("Authorization");org.json.JSONObject result=form.read();assertFalse(result.has("Authorization"));assertEquals("custom",result.getString("X-Mode"));assertEquals(128,result.getInt("max_tokens"));}catch(Exception error){throw new AssertionError(error);}});}
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
