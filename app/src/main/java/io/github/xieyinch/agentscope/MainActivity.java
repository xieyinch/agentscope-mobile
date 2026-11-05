package io.github.xieyinch.agentscope;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Native navigation around the live platform; no credentials or JS bridge in app code. */
public class MainActivity extends Activity {
    private static final String ORIGIN = "https://platform.agentscope.io";
    private static final String[] PATHS = {"/deploy", "/community", "/skills", "/plugins", "/settings"};
    private static final String[] TITLES = {"Agent", "社区", "技能", "插件", "我的"};
    private static final int FILE_PICKER = 100;
    private final WebView[] pages = new WebView[PATHS.length];
    private final Button[] tabs = new Button[PATHS.length];
    private final List<WebView> popups = new ArrayList<>();
    private FrameLayout content;
    private TextView title;
    private TextView error;
    private ProgressBar progress;
    private int selected;
    private boolean desktop;
    private ValueCallback<Uri[]> fileCallback;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        CookieManager.getInstance().setAcceptCookie(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246,247,251));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button back = button("‹", "返回上一页");
        back.setOnClickListener(v -> goBack());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        title = new TextView(this);
        title.setTextSize(17);
        title.setTextColor(Color.rgb(30,36,64));
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        title.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button menu = button("⋮", "更多选项");
        menu.setOnClickListener(this::showMenu);
        toolbar.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(toolbar);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        error = new TextView(this);
        error.setTextColor(Color.rgb(150,40,40));
        error.setPadding(dp(16), dp(8), dp(16), dp(8));
        error.setVisibility(View.GONE);
        error.setOnClickListener(v -> active().reload());
        root.addView(error);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout navigation = new LinearLayout(this);
        for (int i=0;i<TITLES.length;i++) {
            final int index = i;
            tabs[i] = button(TITLES[i], TITLES[i]);
            tabs[i].setTextSize(12);
            tabs[i].setOnClickListener(v -> select(index));
            navigation.addView(tabs[i], new LinearLayout.LayoutParams(0, dp(56), 1));
        }
        root.addView(navigation);
        setContentView(root);
        selected = state == null ? 0 : state.getInt("selected", 0);
        desktop = state != null && state.getBoolean("desktop", false);
        select(selected);
        if (state != null) {
            Bundle saved = state.getBundle("page");
            if (saved != null) pages[selected].restoreState(saved);
        }
    }

    private Button button(String text, String label) {
        Button b = new Button(this);
        b.setText(text);
        b.setContentDescription(label);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(0,0,0,0);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private WebView active() { return popups.isEmpty() ? pages[selected] : popups.get(popups.size()-1); }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createPage() {
        WebView web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportMultipleWindows(true);
        // Automatic popups remain blocked; user initiated Open QWENPAW works.
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equalsIgnoreCase(uri.getScheme())) return false;
                if ("about".equalsIgnoreCase(uri.getScheme())) return false;
                if (request.isForMainFrame()) {
                    new AlertDialog.Builder(MainActivity.this)
                        .setMessage("该页面请求打开其他应用，是否交给系统处理？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("打开", (d,w) -> openExternal(uri)).show();
                }
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                if (view == active()) { error.setVisibility(View.GONE); progress.setVisibility(View.VISIBLE); }
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                if (view == active()) progress.setVisibility(View.GONE);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError failure) {
                if (request.isForMainFrame() && view == active()) {
                    error.setText("页面加载失败，请检查网络后点此重试。错误码：" + failure.getErrorCode());
                    error.setVisibility(View.VISIBLE);
                    progress.setVisibility(View.GONE);
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                if (view == active()) { progress.setProgress(value); progress.setVisibility(value == 100 ? View.GONE : View.VISIBLE); }
            }
            @Override public boolean onCreateWindow(WebView source, boolean dialog, boolean gesture, Message result) {
                if (!gesture) return false;
                WebView child = createPage();
                popups.add(child);
                content.addView(child, new FrameLayout.LayoutParams(-1,-1));
                title.setText("Agent 控制台 / 新窗口");
                WebView.WebViewTransport transport = (WebView.WebViewTransport) result.obj;
                transport.setWebView(child);
                result.sendToTarget();
                return true;
            }
            @Override public void onCloseWindow(WebView window) { closePopup(window); }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try { startActivityForResult(params.createIntent(), FILE_PICKER); }
                catch (ActivityNotFoundException e) {
                    fileCallback.onReceiveValue(null); fileCallback = null;
                    Toast.makeText(MainActivity.this, "未找到系统文件选择器", Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });
        web.setDownloadListener((url, ua, disposition, mime, length) -> {
            // Never copy session cookies or authentication headers to another app.
            Uri uri = Uri.parse(url);
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                new AlertDialog.Builder(this).setMessage("使用系统浏览器下载此文件？需要登录的下载可能要求重新验证。")
                    .setNegativeButton("取消", null).setPositiveButton("下载", (d,w) -> openExternal(uri)).show();
            } else Toast.makeText(this, "此下载类型暂未支持", Toast.LENGTH_LONG).show();
        });
        return web;
    }

    private void select(int index) {
        if (!popups.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("切换栏目将关闭控制台弹出窗口，未提交的内容可能丢失。")
                .setNegativeButton("取消", null).setPositiveButton("切换", (d,w) -> { clearPopups(); showPage(index); }).show();
        } else showPage(index);
    }
    private void showPage(int index) {
        selected = index;
        boolean fresh = pages[index] == null;
        if (fresh) pages[index] = createPage();
        content.removeAllViews();
        content.addView(pages[index], new FrameLayout.LayoutParams(-1,-1));
        title.setText("AgentScope · " + TITLES[index]);
        error.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        for (int i=0;i<tabs.length;i++) tabs[i].setTextColor(i == index ? Color.rgb(82,97,216) : Color.DKGRAY);
        applyDesktop(pages[index]);
        if (fresh) pages[index].loadUrl(ORIGIN + PATHS[index]);
    }
    private void applyDesktop(WebView view) {
        String ua = WebSettings.getDefaultUserAgent(this);
        view.getSettings().setUserAgentString(desktop ? ua.replace("; wv", "").replace("Mobile", "") : ua);
        view.getSettings().setUseWideViewPort(true);
    }
    private void closePopup(WebView web) {
        if (!popups.remove(web)) return;
        content.removeView(web);
        web.destroy();
        title.setText(popups.isEmpty() ? "AgentScope · " + TITLES[selected] : "Agent 控制台 / 新窗口");
    }
    private void clearPopups() {
        while (!popups.isEmpty()) closePopup(popups.get(popups.size()-1));
    }
    private void openExternal(Uri uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "没有可处理此链接的应用", Toast.LENGTH_SHORT).show(); }
    }
    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0,1,0,"刷新当前页面");
        menu.getMenu().add(0,2,0,desktop ? "切换手机视图" : "切换桌面视图");
        if (!popups.isEmpty()) menu.getMenu().add(0,3,0,"关闭当前窗口");
        menu.getMenu().add(0,4,0,"关于与功能范围");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: active().reload(); break;
                case 2: desktop = !desktop; applyDesktop(active()); active().reload(); break;
                case 3: closePopup(active()); break;
                case 4: new AlertDialog.Builder(this).setTitle("AgentScope 随行 0.1.0")
                    .setMessage("非官方安卓客户端。当前为原生导航 + 官方网页控制台，尚未完成逐项原生复刻。\n\nAgent、社区、技能、插件和账户共用站内登录状态。聊天、终端、配置等实际能力由官方服务提供。\n\n语音采集、需登录的文件下载及部分第三方 OAuth 流程尚待适配。")
                    .setPositiveButton("知道了", null).show(); break;
                default: return false;
            }
            return true;
        });
        menu.show();
    }
    private void goBack() {
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        WebView current = active();
        if (current.canGoBack()) current.goBack();
        else if (!popups.isEmpty()) closePopup(current);
        else if (selected != 0) showPage(0);
        else new AlertDialog.Builder(this).setMessage("退出 AgentScope 随行？")
            .setNegativeButton("取消", null).setPositiveButton("退出", (d,w) -> finish()).show();
    }
    @Override public void onBackPressed() { goBack(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request == FILE_PICKER && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));
            fileCallback = null;
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("selected",selected);
        state.putBoolean("desktop",desktop);
        Bundle page = new Bundle();
        pages[selected].saveState(page);
        state.putBundle("page",page);
    }
    @Override protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); }
    @Override protected void onDestroy() {
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        clearPopups();
        content.removeAllViews();
        for (WebView web : pages) if (web != null) web.destroy();
        super.onDestroy();
    }
}
