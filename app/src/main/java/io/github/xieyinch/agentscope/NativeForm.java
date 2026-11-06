package io.github.xieyinch.agentscope;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Typed nested native controls. Never renders a website or asks users to edit raw JSON. */
final class NativeForm {
    interface Read {Object get() throws Exception;}
    final Context context;
    final LinearLayout layout;
    private JSONObject original;
    private final Map<String,Read> fields=new LinkedHashMap<>();
    private final boolean editable;
    private final Ui ui;
    private static final Map<String,String> LABELS=new HashMap<>();
    static {
        String[] pairs={"name","名称","description","说明","enabled","启用","model","模型","model_id","模型 ID","provider_id","供应商","api_key","API 密钥","base_url","服务地址","transport","连接方式","url","地址","command","命令","args","参数","env","环境变量","cwd","工作目录","headers","请求头","tools","工具","timeout","超时（秒）","max_tokens","最大 Token","temperature","温度","schedule","调度","cron","Cron 表达式","timezone","时区","prompt","提示词","text","文本","channel","频道","target","目标","type","类型","content","内容","title","标题","language","语言","interval","间隔","token","访问令牌","username","用户名","password","密码","host","主机","port","端口","id","标识","key","键名","value","值","async_execution","后台执行","http_timeout","HTTP 超时","provider","供应商","reasoning_effort","思考强度","max_steps","最大步骤","runtime","运行参数","active_hours","活跃时段","config","配置","dispatch","投递","request","请求","task_type","任务类型","session_id","会话 ID","user_id","用户 ID","limits","限制","heartbeat","心跳","memory","记忆","args_mapping","参数映射"};
        for(int i=0;i<pairs.length;i+=2)LABELS.put(pairs[i],pairs[i+1]);
        String[] extra={"client_key","服务标识","client","服务配置","include_agents","包含智能体","include_global_config","包含全局配置","include_secrets","包含密钥","include_skill_pool","包含技能池","agent_ids","智能体范围","agents","智能体范围","preserve_local_protected_config","保留本地受保护配置","default_effect","默认访问策略","effect","访问策略","rules","规则","allow","允许","ask","询问","deny","拒绝","auto_sync","自动同步","max_steps","最大执行步数","max_input_length","最大输入长度","save_result_to_inbox","保存结果到收件箱","workspace_dir","工作区目录","created_at","创建时间","updated_at","更新时间","last_run_time","上次执行时间","next_run_time","下次执行时间","state","运行状态","status","状态","scope","备份范围","version","版本","error","错误信息","job_id","任务标识","percent","进度"};for(int i=0;i<extra.length;i+=2)LABELS.put(extra[i],extra[i+1]);
    }
    static String label(String key){return LABELS.getOrDefault(key,key.replace('_',' '));}
    NativeForm(Context c,JSONObject object,boolean edit) {
        context=c;ui=new Ui(c);original=object;editable=edit;layout=column(c);layout.setPadding(Ui.dp(c,16),Ui.dp(c,4),Ui.dp(c,16),Ui.dp(c,16));layout.setBackground(Ui.shape(c,ui.surface,16));
        rebuild(object);
    }
    void rebuild(JSONObject object){original=object;layout.removeAllViews();fields.clear();Iterator<String> keys=object.keys();while(keys.hasNext()){String key=keys.next();fields.put(key,field(layout,key,object.opt(key),0));}}
    void removeField(String key) throws Exception {JSONObject values=read();values.remove(key);rebuild(values);}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static TextView caption(Context c,String text){TextView t=new TextView(c);t.setText(text);t.setTextSize(13);t.setTextColor(new Ui(c).muted);t.setPadding(0,Ui.dp(c,16),0,Ui.dp(c,8));return t;}
    Read field(LinearLayout parent,String key,Object value,int depth) {
        String name=label(key);
        if(value instanceof Boolean){Switch sw=new Switch(context);sw.setText(name);sw.setChecked((Boolean)value);sw.setEnabled(editable);sw.setTextColor(ui.ink);sw.setTextSize(16);sw.setButtonTintList(android.content.res.ColorStateList.valueOf(ui.blue));sw.setPadding(0,10,0,10);parent.addView(sw);return sw::isChecked;}
        parent.addView(caption(context,name));
        String[] choices=key.equals("transport")?new String[]{"stdio","streamable_http","sse"}:key.equals("approval_level")?new String[]{"STRICT","SMART","AUTO","OFF"}:key.equals("default_effect")||key.equals("effect")?new String[]{"ask","allow","deny"}:null;
        if(editable&&choices!=null&&value instanceof String){java.util.List<String> items=new ArrayList<>(Arrays.asList(choices));if(!items.contains(value))items.add((String)value);Spinner select=new Spinner(context);ArrayAdapter<String> adapter=new ArrayAdapter<>(context,android.R.layout.simple_spinner_item,items);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);select.setAdapter(adapter);select.setSelection(items.indexOf(value));parent.addView(select,new LinearLayout.LayoutParams(-1,Ui.dp(context,48)));return ()->items.get(select.getSelectedItemPosition());}

        if(depth>7){TextView note=new TextView(context);note.setText("此项层级较深，保存时保留原值");parent.addView(note);return ()->value;}
        if(value instanceof JSONObject){
            JSONObject object=(JSONObject)value;
            NativeForm child=new NativeForm(context,object,editable);child.layout.setPadding(16,0,0,0);parent.addView(child.layout);
            if(editable){Button add=plainButton();add.setText("添加字段");parent.addView(add);add.setOnClickListener(v->{
                EditText input=new EditText(context);input.setSingleLine(true);input.setHint("字段名称");
                new AlertDialog.Builder(context).setTitle("添加配置项").setView(input).setNegativeButton("取消",null).setPositiveButton("添加",(d,w)->{
                    String k=input.getText().toString().trim();if(k.isEmpty()||child.fields.containsKey(k))return;
                    child.fields.put(k,child.field(child.layout,k,"",depth+1));
                }).show();
            });}
            if(editable&&Arrays.asList("headers","env","custom_headers","args_mapping").contains(key)){Button remove=plainButton();remove.setText("移除字段");parent.addView(remove);remove.setOnClickListener(v->{ArrayList<String> names=new ArrayList<>(child.fields.keySet());if(names.isEmpty()){android.widget.Toast.makeText(context,"没有可移除的字段",android.widget.Toast.LENGTH_SHORT).show();return;}new AlertDialog.Builder(context).setTitle("选择要移除的字段").setItems(names.toArray(new String[0]),(dialog,index)->{try{child.removeField(names.get(index));}catch(Exception error){android.widget.Toast.makeText(context,error.getMessage(),android.widget.Toast.LENGTH_LONG).show();}}).setNegativeButton("取消",null).show();});}
            return child::read;
        }
        if(value instanceof JSONArray){
            JSONArray a=(JSONArray)value;LinearLayout list=column(context);list.setPadding(16,0,0,0);parent.addView(list);
            List<Read> readers=new ArrayList<>();List<Boolean> deleted=new ArrayList<>();
            for(int i=0;i<a.length();i++)appendItem(list,readers,deleted,a.opt(i),depth);
            if(editable){Button add=plainButton();add.setText("添加列表项");parent.addView(add);add.setOnClickListener(v->new AlertDialog.Builder(context).setTitle("新条目类型").setItems(new String[]{"文本","数值","配置组"},(d,w)->appendItem(list,readers,deleted,w==1?0:w==2?new JSONObject():"",depth)).show());}
            return ()->{JSONArray out=new JSONArray();for(int i=0;i<readers.size();i++)if(!deleted.get(i))out.put(readers.get(i).get());return out;};
        }
        if(!editable){TextView text=new TextView(context);String lower=key.toLowerCase(Locale.ROOT);boolean hidden=ControlSchemas.secretField(key);text.setText(hidden&&!Api.text(value).isEmpty()?"已设置（隐藏）":Api.text(value));text.setTextIsSelectable(true);text.setTextSize(16);text.setTextColor(ui.ink);text.setPadding(0,0,0,8);parent.addView(text);return ()->value;}
        EditText input=new EditText(context);input.setText(value==null||value==JSONObject.NULL?"":String.valueOf(value));input.setTextSize(17);input.setTextColor(ui.ink);input.setHintTextColor(ui.muted);input.setPadding(Ui.dp(context,12),Ui.dp(context,12),Ui.dp(context,12),Ui.dp(context,12));input.setBackground(Ui.shape(context,ui.secondary,10));input.setMinimumHeight(Ui.dp(context,48));
        String lower=key.toLowerCase(Locale.ROOT);
        boolean secret=ControlSchemas.secretField(key);
        if(secret){input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);input.setTypeface(Typeface.DEFAULT);}
        else if(value instanceof Number)input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        else {input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setMaxLines(8);}
        input.setSaveEnabled(false);parent.addView(input,new LinearLayout.LayoutParams(-1,-2));
        return ()->{String s=input.getText().toString();if((value==null||value==JSONObject.NULL)&&s.isEmpty())return JSONObject.NULL;
            if(value instanceof Integer||value instanceof Long){try{return Long.parseLong(s);}catch(NumberFormatException e){throw new IllegalArgumentException(name+"需要填写整数");}}
            if(value instanceof Number){try{return Double.parseDouble(s);}catch(NumberFormatException e){throw new IllegalArgumentException(name+"需要填写数字");}}
            return s;};
    }
    private void appendItem(LinearLayout list,List<Read> readers,List<Boolean> deleted,Object value,int depth){
        int index=readers.size();LinearLayout row=column(context);list.addView(row);deleted.add(false);
        readers.add(field(row,"条目 "+(index+1),value,depth+1));
        if(editable){Button remove=plainButton();remove.setText("移除此项");row.addView(remove);remove.setOnClickListener(v->{deleted.set(index,true);row.setVisibility(View.GONE);});}
    }
    private Button plainButton(){Button b=new Button(context);b.setAllCaps(false);b.setTextColor(ui.blue);b.setTextSize(14);b.setStateListAnimator(null);b.setElevation(0);b.setBackground(Ui.ripple(context,ui.secondary,10,ui.blue));b.setMinimumHeight(Ui.dp(context,44));return b;}
    JSONObject read() throws Exception {
        JSONObject out=new JSONObject(original.toString());for(Map.Entry<String,Read> entry:fields.entrySet())out.put(entry.getKey(),entry.getValue().get());return out;
    }
}
