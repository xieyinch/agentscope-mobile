package io.github.xieyinch.agentscope;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.Call;

/** Entire UI is native Android. No WebView, remote frontend, or JavaScript injection. */
public class MainActivity extends Activity {
    private int INK,MUTED,ACCENT,BG;
    private Ui ui;
    private ScrollView scroll;
    private View backControl;
    private final Ui.Icon[] tabIcons=new Ui.Icon[5];
    private final TextView[] tabLabels=new TextView[5];
    private final Runnable[] savedScreens=new Runnable[5];
    private final java.util.List<Deque<Runnable>> savedHistories=new ArrayList<>();
    private final int[] tabScroll=new int[5];
    private int selectedTab;
    private java.util.function.BooleanSupplier dirty=()->false;
    private final Map<String,String> drafts=new HashMap<>();
    private Api api;
    private LinearLayout root,body;
    private TextView heading,status;
    private ProgressBar loading;
    private final ExecutorService work=Executors.newFixedThreadPool(3);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Deque<Runnable> history=new ArrayDeque<>();
    private Runnable screen;
    private int epoch=0;
    private volatile boolean busy=false;
    private JSONObject chat;
    private Call streaming;
    private String streamingChat="";
    private long streamGeneration=0;
    private String terminal="",terminalGroup="android",filePath="";
    private int cursor=0;
    private String downloadPath="",downloadName="";
    private int pickerEpoch;

    interface Job {Object run() throws Exception;}
    interface Result {void accept(Object value) throws Exception;}
    @Override public void onCreate(Bundle saved) {
        ui=new Ui(this);setTheme(ui.dark?R.style.AppThemeDark:R.style.AppTheme);
        super.onCreate(saved);
        INK=ui.ink;MUTED=ui.muted;ACCENT=ui.blue;BG=ui.bg;
        if(Build.VERSION.SDK_INT>=29)getWindow().getDecorView().setForceDarkAllowed(false);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(ui.surface);
        if(Build.VERSION.SDK_INT>=26){int flags=getWindow().getDecorView().getSystemUiVisibility();getWindow().getDecorView().setSystemUiVisibility(ui.dark?flags&~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:flags|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
        for(int i=0;i<5;i++)savedHistories.add(new ArrayDeque<>());history=savedHistories.get(0);
        api=new Api(new SecretStore(this));
        api.agent=getPreferences(0).getString("agent","default");
        root=column();root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout bar=row();bar.setPadding(dp(12),dp(4),dp(12),dp(4));
        FrameLayout back=new FrameLayout(this);Ui.Icon backIcon=new Ui.Icon(this,"back",ACCENT);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER);back.addView(backIcon,bp);back.setContentDescription("返回上一页");back.setOnClickListener(v->back());backControl=back;bar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        heading=text("AgentScope",17,INK);heading.setGravity(Gravity.CENTER);heading.setTypeface(null,Typeface.BOLD);bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        FrameLayout refresh=new FrameLayout(this);refresh.addView(new Ui.Icon(this,"refresh",ACCENT),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));refresh.setContentDescription("刷新当前页面");refresh.setBackground(Ui.ripple(this,BG,22,ACCENT));refresh.setOnClickListener(v->guard(()->{if(screen!=null)screen.run();}));bar.addView(refresh,new LinearLayout.LayoutParams(dp(44),dp(44)));root.addView(bar);
        loading=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);loading.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));loading.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ACCENT));loading.setIndeterminate(true);loading.setVisibility(View.GONE);root.addView(loading,new LinearLayout.LayoutParams(-1,dp(2)));
        status=text("",14,ui.red);status.setPadding(dp(16),dp(12),dp(16),dp(12));status.setBackground(Ui.shape(this,ui.surface,12));status.setVisibility(View.GONE);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.setMargins(dp(20),dp(8),dp(20),0);root.addView(status,sp);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);body=column();body.setPadding(dp(20),dp(4),dp(20),dp(28));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        View divider=new View(this);divider.setBackgroundColor(ui.line);root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        LinearLayout nav=row();nav.setBackgroundColor(ui.surface);nav.setPadding(dp(6),dp(5),dp(6),dp(5));String[] names={"总览","对话","管理","社区","我的"},icons={"grid","chat","settings","community","person"};
        for(int i=0;i<names.length;i++){final int index=i;LinearLayout tab=column();tab.setGravity(Gravity.CENTER);tab.setContentDescription(names[i]);tab.setBackground(Ui.ripple(this,ui.surface,14,ACCENT));tab.setClickable(true);tab.setFocusable(true);tab.setOnClickListener(v->guard(()->switchTab(index)));
            tabIcons[i]=new Ui.Icon(this,icons[i],MUTED);tab.addView(tabIcons[i],new LinearLayout.LayoutParams(dp(24),dp(24)));tabLabels[i]=text(names[i],11,MUTED);tabLabels[i].setPadding(0,dp(3),0,0);tabLabels[i].setGravity(Gravity.CENTER);tab.addView(tabLabels[i]);nav.addView(tab,new LinearLayout.LayoutParams(0,dp(54),1));}
        root.addView(nav);setContentView(root);switchTab(0);
    }
    private Runnable tabRoot(int index){switch(index){case 1:return this::chats;case 2:return this::settings;case 3:return this::community;case 4:return this::account;default:return this::dashboard;}}
    private void switchTab(int index){
        if(screen!=null){savedScreens[selectedTab]=screen;tabScroll[selectedTab]=scroll.getScrollY();}
        selectedTab=index;history=savedHistories.get(index);screen=savedScreens[index]==null?tabRoot(index):savedScreens[index];
        for(int i=0;i<5;i++){tabIcons[i].tint(i==index?ACCENT:MUTED);tabLabels[i].setTextColor(i==index?ACCENT:MUTED);tabLabels[i].setTypeface(null,i==index?Typeface.BOLD:Typeface.NORMAL);}
        screen.run();int y=tabScroll[index];scroll.post(()->scroll.scrollTo(0,y));
    }
    private void guard(Runnable next){if(dirty.getAsBoolean())confirm("放弃尚未保存的更改？",next);else next.run();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(4),0,dp(4));return t;}
    private Button button(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(16);b.setTypeface(null,Typeface.NORMAL);b.setTextColor(ACCENT);b.setGravity(Gravity.CENTER);b.setMinimumHeight(dp(48));b.setMinHeight(dp(48));b.setPadding(dp(14),dp(10),dp(14),dp(10));b.setStateListAnimator(null);b.setElevation(0);b.setBackground(Ui.ripple(this,ui.surface,13,ACCENT));
        if(label.startsWith("删除")||label.startsWith("清空")||label.startsWith("重置")||label.equals("退出登录"))b.setTextColor(ui.red);
        b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(6);p.bottomMargin=dp(6);b.setLayoutParams(p);return b;
    }
    private Button primary(String label,Runnable action){Button b=button(label,action);b.setTextColor(Color.WHITE);b.setTypeface(null,Typeface.BOLD);b.setBackground(Ui.ripple(this,ACCENT,14,Color.WHITE));return b;}
    private EditText input(String hint,String value,boolean secret){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setTextSize(17);e.setPadding(dp(16),dp(14),dp(16),dp(14));e.setBackground(Ui.shape(this,ui.surface,12));e.setMinimumHeight(dp(50));e.setSaveEnabled(false);if(secret)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);e.setLayoutParams(p);return e;}
    private void note(String s){TextView t=text(s,14,MUTED);t.setLineSpacing(dp(3),1);t.setPadding(dp(2),dp(4),dp(2),dp(12));body.addView(t);}
    private void section(String s){TextView t=text(s,20,INK);t.setTypeface(null,Typeface.BOLD);t.setPadding(dp(2),dp(22),0,dp(10));body.addView(t);}
    private String iconFor(String title){if(title.contains("文件")||title.contains("目录"))return "folder";if(title.contains("终端"))return "terminal";if(title.contains("对话"))return "chat";if(title.contains("审批")||title.contains("安全"))return "shield";if(title.contains("定时")||title.contains("心跳"))return "clock";if(title.contains("部署")||title.contains("连接")||title.contains("实例"))return "cloud";return "grid";}
    private void card(String title,String sub,Runnable action){
        LinearLayout card=row();card.setPadding(dp(16),dp(14),dp(14),dp(14));card.setGravity(Gravity.CENTER_VERTICAL);card.setMinimumHeight(dp(74));card.setBackground(action==null?Ui.shape(this,ui.surface,16):Ui.ripple(this,ui.surface,16,ACCENT));
        FrameLayout badge=new FrameLayout(this);badge.setBackground(Ui.shape(this,ui.secondary,11));badge.addView(new Ui.Icon(this,iconFor(title),ACCENT),new FrameLayout.LayoutParams(dp(23),dp(23),Gravity.CENTER));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(42),dp(42));ip.rightMargin=dp(13);card.addView(badge,ip);
        LinearLayout copy=column();TextView h=text(title,17,INK);h.setTypeface(null,Typeface.BOLD);h.setPadding(0,0,0,0);copy.addView(h);if(!sub.isEmpty()){TextView detail=text(sub,13,MUTED);detail.setLineSpacing(dp(2),1);detail.setMaxLines(3);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(detail);}card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        if(action!=null){LinearLayout.LayoutParams arrow=new LinearLayout.LayoutParams(dp(16),dp(20));arrow.leftMargin=dp(8);card.addView(new Ui.Icon(this,"chevron",MUTED),arrow);card.setClickable(true);card.setFocusable(true);card.setContentDescription(title+"，"+sub);card.setOnClickListener(v->guard(action));}
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);body.addView(card,p);
    }
    private void start(String title){epoch++;dirty=()->false;heading.setText(history.isEmpty()?"AgentScope":title);backControl.setVisibility(history.isEmpty()?View.INVISIBLE:View.VISIBLE);body.removeAllViews();status.setVisibility(View.GONE);loading.setVisibility(View.GONE);TextView large=text(title,32,INK);large.setTypeface(null,Typeface.BOLD);large.setLetterSpacing(-0.025f);large.setPadding(0,dp(8),0,dp(18));body.addView(large);scroll.scrollTo(0,0);}
    private void navigate(Runnable next,boolean remember){if(remember&&screen!=null)history.push(screen);screen=next;savedScreens[selectedTab]=next;next.run();}
    private void back(){guard(()->{if(!history.isEmpty())navigate(history.pop(),false);else if(selectedTab!=0)switchTab(0);else confirm(streaming!=null?"退出应用？Agent 任务仍在服务端运行。":"退出 AgentScope 随行？",this::finish);});}
    @Override public void onBackPressed(){back();}
    private void info(String message){status.setText(message);status.setTextColor(INK);status.setVisibility(View.VISIBLE);}
    private void failure(Exception e){status.setText(e.getMessage()==null?"操作失败，请重试":e.getMessage());status.setTextColor(ui.red);status.setVisibility(View.VISIBLE);}
    private void task(Job job,Result result){
        int visit=epoch;loading.setVisibility(View.VISIBLE);
        String scope=api.agent;
        work.execute(()->{api.requestAgent.set(scope);try{Object data=job.run();handler.post(()->{if(isFinishing()||visit!=epoch)return;loading.setVisibility(View.GONE);try{result.accept(data);}catch(Exception e){failure(e);}});}catch(Exception e){handler.post(()->{if(!isFinishing()&&visit==epoch){loading.setVisibility(View.GONE);failure(e);}});}finally{api.requestAgent.remove();}});
    }
    private void change(String message,Job job,Runnable done){
        confirm(message,()->{if(busy){info("上一个操作仍在处理");return;}busy=true;task(()->{try{return job.run();}finally{busy=false;}},v->{dirty=()->false;Toast.makeText(this,"操作成功",Toast.LENGTH_SHORT).show();done.run();});});
    }
    private void equalButtons(LinearLayout row){for(int i=0;i<row.getChildCount();i++){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);if(i>0)p.leftMargin=dp(6);row.getChildAt(i).setLayoutParams(p);if(row.getChildAt(i) instanceof TextView)((TextView)row.getChildAt(i)).setTextSize(13);}}
    private void confirm(String message,Runnable yes){new AlertDialog.Builder(this).setMessage(message).setNegativeButton("取消",null).setPositiveButton("确认",(d,w)->yes.run()).show();}
    private boolean requireInstance(){if(api.connected())return true;note("先连接你的 QwenPaw 实例，即可管理 Agent。");body.addView(button("连接实例",()->navigate(this::connection,true)));return false;}
    private boolean requireAccount(){if(!api.secrets.get("access").isEmpty())return true;note("登录 AgentScope 账户后管理部署和社区内容。");body.addView(button("登录账户",()->navigate(this::login,true)));return false;}
    private static String id(JSONObject o){for(String k:new String[]{"id","appId","key","name"})if(!Api.str(o,k).isEmpty())return Api.str(o,k);return "";}
    private static String name(JSONObject o){for(String k:new String[]{"title","name","display_name","appName","username","id","key"})if(!Api.str(o,k).isEmpty())return Api.str(o,k);return "未命名";}
    private void detail(String title,Object value){navigate(()->{start(title);JSONObject object=value instanceof JSONObject?(JSONObject)value:Api.obj("内容",value);NativeForm form=new NativeForm(this,object,false);body.addView(form.layout);},true);}

    private void dashboard(){
        start("Agent 控制台");
        card(api.connected()?"已连接 Agent 实例":"连接你的 Agent",api.connected()?"当前智能体："+api.agent:"原生界面 · 直接连接官方服务",()->navigate(this::connection,true));
        if(api.connected()){
            card("开始对话","历史记录、流式回复、停止任务",()->navigate(this::chats,true));
            card("智能体管理","选择、创建、启停与配置 Agent",()->navigate(()->resources("智能体","/agents","agents"),true));
            card("工作区文件","浏览、编辑、上传与下载",()->navigate(()->files(""),true));
            card("终端","远程命令与输出",()->navigate(this::terminals,true));
            card("工具审批","查看并处理待审批执行请求",()->navigate(this::approvals,true));
            card("收件箱","任务通知与运行记录",()->navigate(()->readList("收件箱","/console/inbox/events?limit=50"),true));
        }
        section("云端部署");
        if(!requireAccount())return;
        body.addView(button("刷新部署列表",this::dashboard));
        task(()->Api.unwrap(api.platform("GET","/api/v1/app/list",null)),v->{JSONArray list=Api.array(v);if(list.length()==0)note("此账户还没有部署实例。");for(int i=0;i<list.length();i++){JSONObject a=list.optJSONObject(i);if(a==null)continue;card(name(a),a.optString("status","查看状态与管理"),()->navigate(()->deployment(a),true));}});
    }
    private void deployment(JSONObject app){
        start("工作空间");String appId=app.optString("appId",id(app));note("管理你的云端 Agent 实例");
        task(()->Api.unwrap(api.platform("GET","/api/v1/app/get?appId="+Uri.encode(appId),null)),value->{
            JSONObject state=Api.object(value);String raw=state.optString("status","unknown");String label=raw.equals("running")?"正在运行":raw.equals("sleeping")?"已休眠":raw.equals("stopped")?"已停止":raw.equals("starting")?"正在启动":raw;
            LinearLayout hero=column();hero.setPadding(dp(20),dp(20),dp(20),dp(20));hero.setBackground(Ui.shape(this,ui.surface,20));TextView badge=text("●  "+label,13,raw.equals("running")?ui.green:MUTED);hero.addView(badge);TextView name=text("QwenPaw",26,INK);name.setTypeface(null,Typeface.BOLD);hero.addView(name);hero.addView(text("连接控制台后，开始对话并管理 Agent。",14,MUTED));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.bottomMargin=dp(14);body.addView(hero,hp);
            Button connect=primary("连接并打开控制台",()->{});connect.setOnClickListener(view->{connect.setEnabled(false);connect.setText("正在建立安全连接…");task(()->{try{api.connectFromApp(appId);return null;}finally{handler.post(()->{connect.setEnabled(true);connect.setText("重新连接控制台");});}},result->{for(int i=0;i<3;i++){savedHistories.get(i).clear();savedScreens[i]=null;}switchTab(1);Toast.makeText(this,"控制台已连接",Toast.LENGTH_SHORT).show();});});body.addView(connect);
            if(!raw.equals("running"))note("实例休眠或停止时，先在运行管理中启动，再连接。");
            section("运行信息");
            card("部署日志","查看启动和运行记录",()->task(()->Api.unwrap(api.platform("GET","/api/v1/app/deploy-logs?appId="+Uri.encode(appId),null)),v->detail("部署日志",v)));
            card("资源监控","内存、运行状态与资源信息",()->task(()->Api.unwrap(api.platform("GET","/api/v1/app/metrics?appId="+Uri.encode(appId),null)),v->detail("资源监控",v)));
            section("管理");card("运行管理","启动、暂停、重启与版本升级",()->navigate(()->deploymentActions(app),true));
        });
    }
    private void deploymentActions(JSONObject app){
        start("运行管理");String appId=app.optString("appId",id(app));note("这些操作仅作用于当前工作空间。");
        section("运行控制");
        for(String[] a:new String[][]{{"启动实例","start"},{"停止实例","stop"},{"重启实例","restart"},{"升级 QwenPaw","update"},{"恢复实例","recover"}})
            card(a[0],"",()->change(a[0]+"？",()->api.platform("POST","/api/v1/app/"+a[1],Api.obj("appId",appId)),()->deploymentActions(app)));
        section("版本");
        card("使用稳定版","适合日常使用",()->change("切换此实例到稳定版？",()->api.platform("POST","/api/v1/app/stable",Api.obj("appId",appId)),()->deploymentActions(app)));
        card("使用预览版","新功能可能不稳定",()->change("切换此实例到预览版？",()->api.platform("POST","/api/v1/app/pre",Api.obj("appId",appId)),()->deploymentActions(app)));
        section("高级操作");note("重置操作可能影响认证信息或数据。操作前请确认已有备份。");
        body.addView(button("重置实例认证",()->change("重置当前实例认证？已有连接可能失效。",()->api.platform("POST","/api/v1/app/reset-qwenpaw-auth",Api.obj("appId",appId)),()->deploymentActions(app))));
        body.addView(button("清空并重置实例",()->change("清空并重置当前实例？配置与数据可能被永久删除，此操作无法通过应用撤回。",()->api.platform("POST","/api/v1/app/reset",Api.obj("appId",appId)),()->deploymentActions(app))));
    }
    private void login(){
        start("登录 AgentScope");section("欢迎回来");note("使用 AgentScope 绑定的邮箱登录。凭据只发送至 platform.agentscope.io；密码不保存。");
        EditText email=input("邮箱",api.secrets.get("email"),false),password=input("密码","",true);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);body.addView(email);body.addView(password);
        Button submit=primary("登录",()->{});submit.setOnClickListener(view->{String a=email.getText().toString().trim(),p=password.getText().toString();if(a.isEmpty()||p.isEmpty()){info("请输入邮箱和密码");return;}submit.setEnabled(false);task(()->{try{api.login(a,p);return null;}finally{handler.post(()->submit.setEnabled(true));}},v->{password.setText("");history.clear();navigate(this::dashboard,false);});});body.addView(submit);
        note("当前版本支持邮箱密码登录；GitHub OAuth、注册与账户合并尚未实现。");
    }
    private void connection(){
        start("连接 Agent 实例");note("推荐登录平台，在部署管理中点击连接。也可连接你自己的 HTTPS QwenPaw 实例。");
        body.addView(button("平台部署列表",()->navigate(this::dashboard,true)));
        EditText address=input("HTTPS 实例地址",api.base(),false),token=input("实例访问令牌（如启用认证）","",true);address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);body.addView(address);body.addView(token);
        body.addView(primary("验证并连接",()->{String a=address.getText().toString(),t=token.getText().toString();if(t.isEmpty()&&a.equals(api.base()))t=api.secrets.get("instanceToken");final String supplied=t;task(()->{api.configureInstance(a,supplied);return null;},v->{token.setText("");history.clear();navigate(this::dashboard,false);});}));
        note("平台登录与实例认证彼此独立。实例地址及令牌通过 Android Keystore 加密保存。");
    }
    private void account(){
        start("我的账户");if(!requireAccount())return;
        task(()->Api.unwrap(api.platform("GET","/api/v1/auth/userinfo",null)),v->{JSONObject u=Api.object(v);card(name(u),u.optString("email",api.secrets.get("email")),null);});
        body.addView(button("账户绑定信息",()->task(()->Api.unwrap(api.platform("GET","/api/v1/auth/bindings",null)),v->detail("绑定信息",v))));
        body.addView(button("连接实例",()->navigate(this::connection,true)));
        body.addView(button("我的帖子",()->navigate(()->articleList("我的帖子","/api/v1/community/me/articles?page=1&page_size=30"),true)));
        body.addView(button("我的收藏",()->navigate(()->articleList("我的收藏","/api/v1/community/me/favorites?page=1&page_size=30"),true)));
        body.addView(button("退出登录",()->change("退出并移除此应用中保存的全部登录状态？",()->{try{api.platform("POST","/api/v1/auth/logout",null);}finally{api.secrets.clear();}return null;},this::account)));
        section("AgentScope 随行 · 0.3.0");note("为 AgentScope 打造的独立安卓客户端。部分高级能力仍在完善中。");
    }

    private void settings(){
        start("Agent 配置");if(!requireInstance())return;
        card("当前智能体",api.agent,()->navigate(()->resources("智能体","/agents","agents"),true));
        String[][] modules={{"运行配置","/workspace/running-config","config"},{"定时任务","/cron/jobs","cron"},{"频道","/config/channels","config"},{"心跳","/config/heartbeat","config"},{"技能","/skills","skills"},{"工具","/tools","tools"},{"MCP 服务","/mcp","mcp"},{"ACP","/config/acp","config"},{"模型供应商","/models","models"},{"扩展插件","/plugins","plugins"},{"技能池","/skills/pool","pool"},{"环境变量","/envs","env"},{"工具安全","/config/security/tool-guard","config"},{"沙箱安全","/config/security/sandbox","config"},{"文件保护","/config/security/file-guard","config"},{"备份","/backups","backups"}};
        for(String[] m:modules)card(m[0],"查看与管理",()->navigate(()->resources(m[0],m[1],m[2]),true));
        card("Token 消耗","最近 7 天",()->{java.time.LocalDate today=java.time.LocalDate.now();navigate(()->readList("Token 消耗","/token-usage?start_date="+today.minusDays(6)+"&end_date="+today),true);});
        card("进程状态","诊断 Agent 服务",()->navigate(()->readList("进程状态","/agent/admin/status"),true));
    }
    private void readList(String title,String path){start(title);if(!requireInstance())return;task(()->api.instance("GET",path,null),v->{JSONArray list=Api.array(v);if(list.length()>0){for(int i=0;i<list.length();i++){Object value=list.opt(i);card(value instanceof JSONObject?name((JSONObject)value):Api.text(value),Api.text(value),()->detail(title,value));}}else body.addView(new NativeForm(this,v instanceof JSONObject?(JSONObject)v:Api.obj("内容",v),false).layout);});}
    private void resources(String title,String path,String kind){
        start(title);if(!requireInstance())return;
        task(()->api.instance("GET",path,null),v->{
            if(kind.equals("config")){editConfig(title,path,Api.object(v),"PUT",false);if(path.equals("/config/heartbeat"))body.addView(button("立即运行心跳",()->change("现在运行心跳？",()->api.instance("POST",path+"/run",null),()->resources(title,path,kind))));return;}
            JSONArray list=Api.array(v);
            if(list.length()==0)note("暂无条目，或服务端返回的版本结构暂不支持。");
            if(kind.equals("agents"))body.addView(button("创建智能体",()->newForm("创建智能体",path,Api.obj("id","","name","","description",""))));
            if(kind.equals("mcp"))body.addView(button("添加 MCP 服务",()->newForm("添加 MCP",path,Api.obj("name","","description","","enabled",true,"transport","streamable_http","url","","headers",new JSONObject(),"command","","args",new JSONArray(),"env",new JSONObject(),"cwd",""))));
            if(kind.equals("cron"))body.addView(button("创建定时任务",this::createCron));
            if(kind.equals("env"))body.addView(button("添加环境变量",this::addEnvironment));
            if(kind.equals("models"))body.addView(button("添加供应商",()->newForm("添加供应商","/models/custom-providers",Api.obj("id","","name","","default_base_url","","api_key",""))));
            for(int i=0;i<list.length();i++){JSONObject o=list.optJSONObject(i);if(o==null)continue;String key=id(o);String sub=o.optString("description",o.optString("status",""));if(o.has("enabled"))sub=(o.optBoolean("enabled")?"已启用 · ":"已停用 · ")+sub;
                card(name(o),sub,()->navigate(()->resourceDetail(title,path,kind,key,o),true));}
        });
    }
    private void editConfig(String title,String path,JSONObject config,String method,boolean returnAfter){
        NativeForm form=new NativeForm(this,config,true);body.addView(form.layout);String original=config.toString();dirty=()->{try{return !form.read().toString().equals(original);}catch(Exception ignored){return true;}};
        body.addView(button("保存配置",()->{try{JSONObject data=form.read();change("保存“"+title+"”配置？",()->api.instance(method,path,data),()->{if(returnAfter)back();else resources(title,path,"config");});}catch(Exception e){failure(e);}}));
    }
    private void newForm(String title,String path,JSONObject initial){navigate(()->{start(title);editConfig(title,path,initial,"POST",true);},true);}
    private void resourceDetail(String title,String path,String kind,String key,JSONObject o){
        start(name(o));String encoded=Uri.encode(key);
        if(kind.equals("agents")){
            body.addView(button("切换至此智能体",()->{api.agent=key;getPreferences(0).edit().putString("agent",key).apply();history.clear();navigate(this::dashboard,false);}));
            body.addView(button(o.optBoolean("enabled",true)?"停用智能体":"启用智能体",()->change("切换此智能体运行状态？",()->api.instance("PATCH","/agents/"+encoded+"/toggle",Api.obj("enabled",!o.optBoolean("enabled",true))),()->resources(title,path,kind))));
            task(()->api.instance("GET",path+"/"+encoded,null),v->editConfig(title,path+"/"+encoded,Api.object(v),"PUT",true));
        }else if(kind.equals("tools")){
            body.addView(button(o.optBoolean("enabled")?"停用工具":"启用工具",()->change("切换此工具状态？",()->api.instance("PATCH",path+"/"+encoded+"/toggle",null),()->resources(title,path,kind))));
            body.addView(button(o.optBoolean("async_execution")?"关闭后台执行":"允许后台执行",()->change("更改工具后台执行设置？",()->api.instance("PATCH",path+"/"+encoded+"/async-execution",Api.obj("async_execution",!o.optBoolean("async_execution"))),()->resources(title,path,kind))));
            if(o.optBoolean("requires_config"))body.addView(button("工具参数",()->task(()->api.instance("GET",path+"/"+encoded+"/config",null),v->{JSONObject values=Api.object(v);navigate(()->{start("工具参数");NativeForm f=new NativeForm(this,values,true);body.addView(f.layout);body.addView(button("保存",()->{try{JSONObject data=Api.obj("config",f.read());change("保存工具参数？",()->api.instance("POST",path+"/"+encoded+"/config",data),this::back);}catch(Exception e){failure(e);}}));},true);})));body.addView(new NativeForm(this,o,false).layout);
        }else if(kind.equals("skills")){
            body.addView(button(o.optBoolean("enabled")?"停用技能":"启用技能",()->change("切换此技能状态？",()->api.instance("POST",path+"/"+encoded+(o.optBoolean("enabled")?"/disable":"/enable"),null),()->resources(title,path,kind))));body.addView(new NativeForm(this,o,false).layout);
        }else if(kind.equals("mcp")){
            body.addView(button("切换启用状态",()->change("切换 MCP 服务状态？",()->api.instance("PATCH","/mcp/toggle/"+encoded,null),()->resources(title,path,kind))));
            body.addView(button("可用工具",()->navigate(()->readList("MCP 工具","/mcp/tools/"+encoded),true)));
            task(()->api.instance("GET",path+"/"+encoded,null),v->editConfig(title,path+"/"+encoded,Api.object(v),"PUT",true));
        }else if(kind.equals("cron")){
            for(String[] a:new String[][]{{"立即执行","run"},{"暂停","pause"},{"恢复","resume"}})body.addView(button(a[0],()->change(a[0]+"此定时任务？",()->api.instance("POST",path+"/"+encoded+"/"+a[1],null),()->resources(title,path,kind))));
            body.addView(button("执行历史",()->navigate(()->readList("执行历史",path+"/"+encoded+"/history"),true)));
            JSONObject spec=Api.object(copy(o));spec.remove("state");spec.remove("next_run_time");spec.remove("last_run_time");editConfig(title,path+"/"+encoded,spec,"PUT",true);
        }else if(kind.equals("models"))modelDetail(o);
        else if(kind.equals("env")){
            EditText value=input("新的值","",true);body.addView(value);body.addView(button("保存此变量",()->change("更新环境变量 "+key+"？",()->api.instance("PATCH","/envs",Api.obj(key,value.getText().toString())),()->resources(title,path,kind))));
        }else {body.addView(new NativeForm(this,o,false).layout);note("此模块的高级操作尚未完成适配。");}
        if(Arrays.asList("agents","mcp","cron","env").contains(kind))body.addView(button("删除",()->change("删除“"+name(o)+"”？此操作可能无法恢复。",()->api.instance("DELETE",path+"/"+encoded,null),()->resources(title,path,kind))));
    }
    private static Object copy(Object o){try{return new JSONTokener(o.toString()).nextValue();}catch(Exception e){return new JSONObject();}}
    private void modelDetail(JSONObject provider){
        String pid=id(provider),path="/models/"+Uri.encode(pid);
        section("供应商连接");EditText base=input("API 服务地址",provider.optString("base_url",provider.optString("default_base_url","")),false),key=input("新 API Key（留空保持现有密钥）","",true);body.addView(base);body.addView(key);
        body.addView(button("保存连接设置",()->{JSONObject data=Api.obj("base_url",base.getText().toString());try{if(key.length()>0)data.put("api_key",key.getText().toString());}catch(JSONException ignored){}change("保存供应商连接设置？",()->api.instance("PUT",path+"/config",data),()->resources("模型供应商","/models","models"));}));
        JSONArray models=Api.array(provider.opt("models"));section("模型");if(models.length()==0)note("尚未添加模型。");
        for(int i=0;i<models.length();i++){JSONObject model=models.optJSONObject(i);if(model==null)continue;String modelId=id(model);card(name(model),model.optString("description",""),()->confirm("将 "+modelId+" 设为当前 Agent 的对话模型？",()->task(()->api.instance("PUT","/models/active",Api.obj("provider_id",pid,"model",modelId,"scope","agent","agent_id",api.agent)),v->info("模型已切换"))));}
        body.addView(button("添加模型",()->newForm("添加模型",path+"/models",Api.obj("id","","name",""))));
    }
    private void addEnvironment(){navigate(()->{start("添加环境变量");EditText key=input("变量名","",false),value=input("变量值","",true);body.addView(key);body.addView(value);body.addView(button("保存",()->{String k=key.getText().toString().trim();if(k.isEmpty()){info("请填写变量名");return;}change("保存环境变量 "+k+"？",()->api.instance("PATCH","/envs",Api.obj(k,value.getText().toString())),this::back);}));},true);}
    private void createCron(){
        JSONObject spec=Api.obj("id",UUID.randomUUID().toString(),"name","新的定时任务","enabled",false,"save_result_to_inbox",true,"schedule",Api.obj("type","cron","cron","0 9 * * *","timezone","Asia/Shanghai"),"task_type","text","text","","dispatch",Api.obj("type","channel","channel","console","target",Api.obj("user_id","default","session_id","default"),"mode","final"));newForm("创建定时任务","/cron/jobs",spec);
    }

    private void approvals(){
        start("工具审批");if(!requireInstance())return;
        task(()->api.instance("GET","/approval/list",null),value->{JSONArray list=Api.object(value).optJSONArray("pending_approvals");if(list==null||list.length()==0){note("当前没有待审批请求。");return;}
            for(int i=0;i<list.length();i++){JSONObject pending=list.optJSONObject(i);if(pending==null)continue;card(pending.optString("tool_display_name",pending.optString("tool_name","工具执行")),pending.optString("reasoning",pending.optString("findings_summary","")),()->navigate(()->{
                start("执行审批");body.addView(new NativeForm(this,pending,false).layout);
                JSONObject payload=Api.obj("request_id",pending.optString("request_id"),"session_id",pending.optString("session_id"),"scope","exact");
                body.addView(button("允许这次精确操作",()->change("允许以上工具按显示的参数执行？",()->api.instance("POST","/approval/approve",payload),this::back)));
                body.addView(button("拒绝",()->change("拒绝这次工具执行？",()->api.instance("POST","/approval/deny",payload),this::back)));
            },true));}
        });
    }

    private void chats(){
        start("对话");if(!requireInstance())return;
        if(streaming!=null)note("有一个任务正在运行，切回其会话查看结果。");
        body.addView(button("新建对话",()->{if(streaming!=null){info("请先停止当前任务，或等待完成");return;}String sid=UUID.randomUUID().toString();task(()->api.instance("POST","/chats",Api.obj("session_id",sid,"user_id","default","channel","console","name","新的对话")),v->{chat=Api.object(v);navigate(()->conversation(chat),true);});}));
        task(()->api.instance("GET","/chats",null),v->{JSONArray list=Api.array(v);if(list.length()==0)note("从一个新任务开始。");for(int i=0;i<list.length();i++){JSONObject c=list.optJSONObject(i);if(c==null)continue;card(name(c),c.optString("status","idle")+" · "+c.optString("updated_at",""),()->{chat=c;navigate(()->conversation(c),true);});}});
    }
    private void conversation(JSONObject c){
        start(name(c));if(!requireInstance())return;chat=c;String cid=id(c);int visit=epoch;
        LinearLayout controls=row();controls.addView(button("停止任务",()->change("停止此会话当前运行的任务？",()->api.instance("POST","/console/chat/stop?chat_id="+Uri.encode(cid),null),()->{if(streaming!=null&&streamingChat.equals(cid)){streamGeneration++;streaming.cancel();streaming=null;}conversation(c);})));controls.addView(button("终端",()->navigate(this::terminals,true)));controls.addView(button("文件",()->navigate(()->files(""),true)));equalButtons(controls);body.addView(controls);
        LinearLayout messages=column();body.addView(messages);
        EditText compose=input("发消息，让 Agent 帮你完成任务",drafts.getOrDefault(cid,""),false);compose.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence t,int st,int count,int after){}public void onTextChanged(CharSequence t,int st,int before,int count){drafts.put(cid,t.toString());}public void afterTextChanged(android.text.Editable e){}});compose.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);compose.setMinLines(2);compose.setMaxLines(6);body.addView(compose);
        Button send=primary("发送",()->{});body.addView(send);
        task(()->api.instance("GET","/chats/"+Uri.encode(cid),null),v->{JSONArray list=Api.object(v).optJSONArray("messages");if(list==null||list.length()==0){messages.addView(text("你好，有什么想交给 Agent 的？",15,MUTED));return;}for(int i=0;i<list.length();i++){JSONObject m=list.optJSONObject(i);if(m==null)continue;TextView label=text(m.optString("role","assistant").equals("user")?"你":"Agent",12,MUTED);messages.addView(label);TextView t=text(Api.text(m.opt("content")),15,INK);t.setTextIsSelectable(true);messages.addView(t);}});
        send.setOnClickListener(v->{String prompt=compose.getText().toString().trim();if(prompt.isEmpty())return;if(streaming!=null){info("已有任务运行中，请等待完成或停止");return;}
            compose.setText("");messages.addView(text("你",12,MUTED));messages.addView(text(prompt,15,INK));TextView response=text("Agent 正在处理…",15,INK);response.setTextIsSelectable(true);messages.addView(response);send.setEnabled(false);
            JSONObject request=Api.obj("input",new JSONArray().put(Api.obj("role","user","content",new JSONArray().put(Api.obj("type","text","text",prompt)))),"session_id",c.optString("session_id",cid),"user_id",c.optString("user_id","default"),"channel","console","stream",true,"request_context",Api.obj("chat_id",cid));
            StreamState state=new StreamState();long generation=++streamGeneration;streamingChat=cid;streaming=api.stream(request,new Api.StreamListener(){
                public void event(JSONObject event){String merged=state.accept(event);handler.post(()->{if(epoch==visit&&!isFinishing()){if(!merged.isEmpty())response.setText(merged);if(event.has("error"))info("服务端报告执行错误，请刷新会话查看详情");}});}
                public void done(Exception problem){handler.post(()->{if(generation!=streamGeneration)return;streaming=null;if(epoch!=visit||isFinishing())return;send.setEnabled(true);if(problem!=null){failure(problem);response.append("\n连接已结束，可刷新会话查看服务端结果。");}else{info("本轮执行结束");task(()->api.instance("GET","/chats/"+Uri.encode(cid),null),history->{JSONArray a=Api.object(history).optJSONArray("messages");if(a!=null)for(int i=a.length()-1;i>=0;i--){JSONObject m=a.optJSONObject(i);if(m!=null&&m.optString("role").equals("assistant")){String t=Api.text(m.opt("content"));if(!t.isEmpty()){response.setText(t);break;}}}});}});}
            });
        });
    }
    private void files(String path){
        start("工作区文件");filePath=path;if(!requireInstance())return;note(path.isEmpty()?"Agent 工作区根目录":path);
        body.addView(button("上传文件",()->{pickerEpoch=epoch;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,201);}));
        task(()->api.instance("GET","/workspace/tree?root=workspace&path="+Uri.encode(path)+"&limit=200",null),v->{JSONArray items=Api.array(v);for(int i=0;i<items.length();i++){JSONObject f=items.optJSONObject(i);if(f==null)continue;boolean directory=f.optString("kind").equals("directory");String p=f.optString("path",path+"/"+f.optString("name"));card((directory?"目录 · ":"")+name(f),directory?"打开目录":f.optLong("size",0)+" B",()->navigate(()->{if(directory)files(p);else file(p);},true));}if(Api.object(v).optBoolean("has_more"))note("当前展示前 200 项；目录分页仍待适配。");});
    }
    private void file(String path){
        start(path.substring(path.lastIndexOf('/')+1));note(path);String encoded=Uri.encode(path);
        body.addView(button("下载文件",()->{downloadPath=path;downloadName=path.substring(path.lastIndexOf('/')+1);pickerEpoch=epoch;startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,downloadName).addCategory(Intent.CATEGORY_OPENABLE),202);}));
        task(()->api.instance("GET","/workspace/file-content?root=workspace&path="+encoded+"&limit=262144",null),v->{JSONObject data=Api.object(v);EditText editor=input("文件内容",data.optString("content",""),false);editor.setTypeface(Typeface.MONOSPACE);editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);editor.setMinLines(10);body.addView(editor);boolean full=data.optBoolean("eof",false);if(!full){editor.setEnabled(false);note("文件超过编辑上限，仅预览，避免保存时截断原文件。");return;}String etag=data.optString("etag","");body.addView(button("保存文件",()->change("保存对“"+downloadSafeName(path)+"”的修改？",()->api.instance("PUT","/workspace/file-content?root=workspace&path="+encoded,Api.obj("content",editor.getText().toString()),etag),()->file(path))));});
    }
    private String downloadSafeName(String path){return path.substring(path.lastIndexOf('/')+1);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(pickerEpoch!=epoch){info("页面已变化，请重新选择文件");return;}
        if(request==201){String target=filePath;task(()->{
            String filename="upload.bin";try(android.database.Cursor c=getContentResolver().query(uri,null,null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(i>=0)filename=c.getString(i);}}
            byte[] bytes;try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){if(in==null)throw new java.io.IOException("无法读取文件");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>20*1024*1024)throw new java.io.IOException("当前版本单次上传上限为 20MB");}bytes=out.toByteArray();}
            okhttp3.RequestBody content=okhttp3.RequestBody.create(bytes,okhttp3.MediaType.get("application/octet-stream"));
            okhttp3.MultipartBody multipart=new okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM).addFormDataPart("files",filename,content).build();
            okhttp3.Request upload=api.request(api.base()+"/api/workspace/file-upload?root=workspace&conflict=rename&path="+Uri.encode(target),"POST",null,api.secrets.get("instanceToken"),true,null).newBuilder().post(multipart).build();
            try(okhttp3.Response r=api.http.newCall(upload).execute()){if(!r.isSuccessful())throw new java.io.IOException("上传失败（"+r.code()+"）");return null;}
        },v->{Toast.makeText(this,"文件已上传",Toast.LENGTH_SHORT).show();files(target);});}
        if(request==202){String path=downloadPath;task(()->{okhttp3.Request req=api.request(api.base()+"/api/workspace/file-download?root=workspace&path="+Uri.encode(path),"GET",null,api.secrets.get("instanceToken"),true,null);
            try(okhttp3.Response r=api.http.newCall(req).execute()){if(!r.isSuccessful()||r.body()==null)throw new java.io.IOException("下载失败（"+r.code()+"）");try(java.io.InputStream in=r.body().byteStream();java.io.OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new java.io.IOException("无法写入所选文件");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}return null;
        },v->info("文件已保存"));}
    }
    private void terminals(){
        start("远程终端");if(!requireInstance())return;note("终端命令在你的远程 Agent 实例执行。");String base="/terminals/"+Uri.encode(terminalGroup);
        body.addView(button("新建终端",()->change("创建一个远程终端？",()->api.instance("POST",base,null),()->task(()->api.instance("GET",base,null),v->renderTerminals(base,v)))));
        task(()->api.instance("GET",base,null),v->renderTerminals(base,v));
    }
    private void renderTerminals(String base,Object value){JSONArray a=Api.array(value);if(a.length()==0)note("还没有终端，点击新建开始。");for(int i=0;i<a.length();i++){JSONObject t=a.optJSONObject(i);if(t==null)continue;card(t.optString("title","终端"),t.optString("cwd",""),()->navigate(()->terminal(base,t),true));}}
    private void terminal(String base,JSONObject t){
        start(t.optString("title","终端"));terminal=t.optString("id");cursor=0;int visit=epoch;String termPath=base+"/"+Uri.encode(terminal);
        TextView output=text("连接终端…\n",13,Color.WHITE);output.setTypeface(Typeface.MONOSPACE);output.setBackgroundColor(0xff171c2d);output.setTextIsSelectable(true);output.setPadding(dp(12),dp(12),dp(12),dp(12));body.addView(output,new LinearLayout.LayoutParams(-1,dp(330)));
        EditText command=input("输入命令","",false);command.setSingleLine(true);body.addView(command);body.addView(button("发送",()->{String s=command.getText().toString();command.setText("");task(()->api.instance("POST",termPath+"/input",Api.obj("data",s+"\r")),v->{});}));
        LinearLayout keys=row();for(String[] key:new String[][]{{"Ctrl+C","\u0003"},{"Tab","\t"},{"↑","\u001b[A"},{"↓","\u001b[B"}})keys.addView(button(key[0],()->task(()->api.instance("POST",termPath+"/input",Api.obj("data",key[1])),v->{})));equalButtons(keys);body.addView(keys);
        body.addView(button("关闭终端",()->change("关闭此远程终端？运行的进程可能被中断。",()->api.instance("DELETE",termPath,null),this::back)));
        pollTerminal(termPath,output,visit);
    }
    private void pollTerminal(String path,TextView view,int visit){
        if(epoch!=visit||isFinishing())return;task(()->api.instance("GET",path+"/output?after="+cursor,null),v->{JSONObject o=Api.object(v);cursor=o.optInt("cursor",cursor);String data=o.optString("data","").replaceAll("\u001B\\[[0-?]*[ -/]*[@-~]","");if(o.optBoolean("reset"))view.setText("");view.append(data);if(view.length()>120000)view.setText(view.getText().subSequence(view.length()-100000,view.length()));if(!o.optBoolean("exited"))handler.postDelayed(()->pollTerminal(path,view,visit),500);else info("终端已退出："+o.optString("exit_code"));});
    }
    private void community(){articleList("社区","/api/v1/community/articles?page=1&page_size=30");}
    private void articleList(String title,String path){
        start(title);LinearLayout tools=row();tools.addView(button("发帖",()->navigate(()->publish(false),true)));tools.addView(button("提问",()->navigate(()->publish(true),true)));tools.addView(button("技能市场",()->navigate(()->market(false),true)));tools.addView(button("插件市场",()->navigate(()->market(true),true)));equalButtons(tools);body.addView(tools);
        EditText search=input("搜索社区","",false);body.addView(search);body.addView(button("搜索",()->navigate(()->articleList("搜索结果","/api/v1/community/articles?page=1&page_size=30&keyword="+Uri.encode(search.getText().toString())),true)));
        task(()->Api.unwrap(api.platform("GET",path,null)),v->{JSONArray list=Api.array(v);for(int i=0;i<list.length();i++){JSONObject a=list.optJSONObject(i);if(a==null)continue;card(name(a),a.optString("author_name","")+" · "+a.optInt("comment_count",0)+" 条回复\n"+a.optString("summary",""),()->navigate(()->article(id(a)),true));}
            JSONObject page=Api.object(v);int p=page.optInt("page",1),size=page.optInt("page_size",30);if(p*size<page.optInt("total",0)){String next=path.replaceFirst("([?&])page=\\d+","$1page="+(p+1));body.addView(button("下一页",()->navigate(()->articleList(title,next),true)));}});
    }
    private void article(String id){
        start("社区帖子");String path="/api/v1/community/articles/"+Uri.encode(id);
        task(()->Api.unwrap(api.platform("GET",path,null)),v->{JSONObject a=Api.object(v);section(name(a));note(a.optString("author_name","")+" · "+a.optString("published_at",""));
            String content=a.optString("body_text",a.optString("summary",""));TextView t=text(content,16,INK);if(!a.optString("body_html").isEmpty())t.setText(android.text.Html.fromHtml(a.optString("body_html"),android.text.Html.FROM_HTML_MODE_COMPACT));t.setTextIsSelectable(true);body.addView(t);
            LinearLayout actions=row();actions.addView(button(a.optBoolean("liked")?"取消赞":"点赞",()->task(()->api.platform("POST",path+"/like",null),r->article(id))));actions.addView(button(a.optBoolean("favorited")?"取消收藏":"收藏",()->task(()->api.platform("POST",path+"/favorite",null),r->article(id))));equalButtons(actions);body.addView(actions);
            section("回复");EditText reply=input("写下回复","",false);body.addView(reply);body.addView(button("发布回复",()->{String s=reply.getText().toString().trim();if(s.isEmpty())return;change("发布这条回复？",()->api.platform("POST",path+"/comments",Api.obj("content",s)),()->article(id));}));
            task(()->Api.unwrap(api.platform("GET",path+"/comments?page=1&page_size=50",null)),comments->{JSONArray items=Api.array(comments);for(int i=0;i<items.length();i++){JSONObject c=items.optJSONObject(i);if(c==null)continue;card(c.optString("author_name","用户"),c.optString("content",c.optString("body_text","")),null);}});
        });
    }
    private void publish(boolean question){
        start(question?"发布问题":"发布帖子");if(!requireAccount())return;EditText title=input("标题","",false),content=input("正文","",false);content.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);content.setMinLines(8);body.addView(title);body.addView(content);
        Spinner category=new Spinner(this);String[] codes={"discussion","work_share","app_case","beginner_tutorial"};category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"交流讨论","开发分享","应用案例","新手教程"}));if(!question)body.addView(category);
        body.addView(button("发布",()->{String h=title.getText().toString().trim(),b=content.getText().toString().trim();if(h.isEmpty()||b.isEmpty()){info("请填写标题和正文");return;}change("将此内容发布至公开社区？",()->api.platform("POST","/api/v1/community/"+(question?"questions":"articles"),Api.obj("title",h,"article_type",question?"question":codes[category.getSelectedItemPosition()],"body_text",b,"body_html","<p>"+android.text.TextUtils.htmlEncode(b).replace("\n","</p><p>")+"</p>","tags",new JSONArray())),()->{history.clear();navigate(this::community,false);});}));
    }
    private void market(boolean plugin){
        start(plugin?"插件市场":"技能市场");String path="/api/v1/"+(plugin?"plugins":"skills");task(()->Api.unwrap(api.platform("GET",path+"?page=1&pageSize=30",null)),v->{JSONArray a=Api.array(v);for(int i=0;i<a.length();i++){JSONObject item=a.optJSONObject(i);if(item==null)continue;String identifier=id(item);card(item.optString("name",item.optString("skill_code",identifier)),item.optString("description",""),()->task(()->Api.unwrap(api.platform("GET",path+"/"+Uri.encode(identifier),null)),detail->{detail(plugin?"插件详情":"技能详情",detail);}));}note("市场安装及分页仍待适配。");});
    }
    @Override protected void onDestroy(){epoch++;handler.removeCallbacksAndMessages(null);if(streaming!=null)streaming.cancel();work.shutdownNow();super.onDestroy();}
}
