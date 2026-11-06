package cn.qwenpaw.android;

import android.os.Bundle;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;

/** Standalone entry for direct runtime connections. The platform shell hosts the same fragment. */
public final class MainActivity extends AppCompatActivity {
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);FrameLayout container=new FrameLayout(this);
        container.setId(io.github.xieyinch.agentscope.R.id.console_container);setContentView(container);
        if(saved==null)getSupportFragmentManager().beginTransaction().add(container.getId(),new ConsoleFragment(),"console").commitNow();
    }
    public ConsoleFragment console(){return (ConsoleFragment)getSupportFragmentManager().findFragmentByTag("console");}
}
