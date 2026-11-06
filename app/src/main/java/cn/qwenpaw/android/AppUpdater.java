package cn.qwenpaw.android;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import io.github.xieyinch.agentscope.BuildConfig;
public final class AppUpdater {
    private final Activity activity;
    public AppUpdater(Activity activity){this.activity=activity;}
    public String version(){return BuildConfig.VERSION_NAME;}
    public void check(boolean manual){if(manual)activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/xieyinch/idea-lab/releases")));}
    public void resume(){}
    public void close(){}
}
