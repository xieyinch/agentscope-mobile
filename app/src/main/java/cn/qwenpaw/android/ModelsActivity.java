package cn.qwenpaw.android;

import android.os.Bundle;
import android.text.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Native model management. An operation is bound to the account and agent that opened this page. */
public final class ModelsActivity extends AppCompatActivity {
    private Ui ui;
    private ChatController chat;
    private ApiClient api;
    private long scope;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private JSONArray providers=new JSONArray();
    private String selected="",query="";
    private boolean busy,showHidden,loaded;
    private int revision;
    private LinearLayout root;
    private TextView message;
    private ProgressBar progress;
    private AlertDialog editor,discoveryDialog;
    private final Map<View,Boolean> enabledBeforeRequest=new IdentityHashMap<>();
    private java.util.function.BooleanSupplier dirty=()->false;
    private String activeProvider="",activeModel="";

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);ui=new Ui(this);chat=((PawApplication)getApplication()).ensureChat();if(!chat.loggedIn){finish();return;}
        api=chat.api();scope=chat.scope();if(saved!=null){selected=saved.getString("provider","");query=saved.getString("query","");showHidden=saved.getBoolean("hidden");}render();load();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("provider",selected);out.putString("query",query);out.putBoolean("hidden",showHidden);}
    @Override protected void onResume(){super.onResume();if(api!=null&&!current())finish();}
    @Override protected void onDestroy(){revision++;if(editor!=null)editor.dismiss();if(discoveryDialog!=null)discoveryDialog.dismiss();io.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(busy){tell("正在处理，请稍候",false);return;}if(!selected.isEmpty()){selected="";query="";render();}else super.onBackPressed();}
    private boolean current(){return !isFinishing()&&!isDestroyed()&&chat.loggedIn&&chat.scope()==scope&&chat.base.equals(api.base)&&chat.token.equals(api.token)&&chat.agent.equals(api.agent);}
    private boolean mutable(){if(busy)return false;if(chat.running||chat.busy||chat.uploads>0){tell("请先等待对话任务或附件上传结束，再修改模型设置",true);return false;}return true;}
    private void tell(String text,boolean error){if(message!=null){message.setText(text);message.setTextColor(error?Ui.DANGER:Ui.MUTED);message.setVisibility(text.isEmpty()?View.GONE:View.VISIBLE);}}
    private interface Work {Object run()throws Exception;}
    private void request(Work work,Consumer<Object> done){request(work,done,this::failed);}
    private void request(Work work,Consumer<Object> done,Consumer<Exception> onError){
        if(busy)return;busy=true;int id=++revision;progress.setVisibility(View.VISIBLE);tell("正在处理…",false);enable(root,false);if(editor!=null){editor.setCancelable(false);enable(editor.getWindow().getDecorView(),false);}if(discoveryDialog!=null)enable(discoveryDialog.getWindow().getDecorView(),false);
        io.execute(()->{try{Object result=work.run();runOnUiThread(()->{if(!current()||id!=revision)return;settled();try{done.accept(result);}catch(Exception e){failed(e);}});}catch(Exception e){runOnUiThread(()->{if(!current()||id!=revision)return;settled();onError.accept(e);});}});
    }
    private void settled(){busy=false;enable(root,true);progress.setVisibility(View.GONE);tell("",false);}
    private void failed(Exception e){if(e instanceof ApiClient.ApiException a&&a.status==401){chat.failure(e);finish();return;}String text=e.getMessage()==null?"操作失败，请重试":e.getMessage();tell(text,true);if(editor!=null&&editor.isShowing())Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void enable(View view,boolean enabled){if(enabled){enabledBeforeRequest.forEach(View::setEnabled);enabledBeforeRequest.clear();return;}enabledBeforeRequest.putIfAbsent(view,view.isEnabled());view.setEnabled(false);if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)enable(g.getChildAt(i),false);}
    private JSONArray catalog()throws IOException{Object data=api.call("GET","/models",null);if(!(data instanceof JSONArray))throw new IOException("服务器返回的模型列表格式不正确");return (JSONArray)data;}
    private JSONObject active()throws IOException{Object data=api.call("GET","/models/active?scope=effective&agent_id="+ApiClient.enc(api.agent),null);if(!(data instanceof JSONObject))throw new IOException("服务器未返回当前模型配置");return (JSONObject)data;}
    private void applyCatalog(JSONArray data){providers=data;chat.providers=data;loaded=true;}
    private void load(){request(this::catalog,r->{applyCatalog((JSONArray)r);render();readActive();});}
    private void readActive(){request(this::active,r->{JSONObject slot=Json.object(((JSONObject)r).opt("active_llm"));activeProvider=Json.str(slot,"provider_id");activeModel=Json.str(slot,"model");render();});}
    private JSONObject provider(){for(int i=0;i<providers.length();i++){JSONObject p=providers.optJSONObject(i);if(p!=null&&selected.equals(Json.str(p,"id")))return p;}return null;}
    private void render(){
        root=ui.column();root.setBackgroundColor(Ui.BG);root.setPadding(ui.dp(16),0,ui.dp(16),ui.dp(12));setContentView(root);Ui.insets(root,16,12);
        LinearLayout head=ui.row();head.addView(ui.iconButton("返回","close",v->onBackPressed()));JSONObject p=provider();head.addView(ui.heading(selected.isEmpty()?"模型与供应商":p==null?"供应商不可用":Json.str(p,"name",selected),21),new LinearLayout.LayoutParams(0,-2,1));head.addView(ui.iconButton("刷新模型列表","loop",v->{query="";load();}));root.addView(head);
        TextView scopeLabel=ui.text("当前智能体 · "+api.agent+" · 只修改你选择的供应商或模型",12,Ui.MUTED);root.addView(scopeLabel);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(busy?View.VISIBLE:View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,ui.dp(3)));
        message=ui.text("",13,Ui.MUTED);message.setPadding(0,ui.dp(8),0,ui.dp(8));message.setVisibility(View.GONE);root.addView(message);
        if(!selected.isEmpty()&&p==null){tell(loaded?"该供应商已被移除，返回列表后重试":"正在读取供应商…",false);return;}
        if(p==null){root.addView(ui.button("添加自定义供应商","plus",true,v->createProvider()));}
        else{
            TextView selectedModel=ui.text(activeProvider.equals(selected)?"当前默认模型 · "+activeModel:"点击模型可设为智能体默认模型；已有会话的单独选择保持不变",12,Ui.MUTED);root.addView(selectedModel);
            LinearLayout actions=ui.row();actions.addView(ui.button("连接设置","settings",false,v->connection(p)),new LinearLayout.LayoutParams(0,ui.dp(48),1));var discover=ui.button("自动发现","search",false,v->discover(p));discover.setEnabled(p.optBoolean("support_model_discovery",true));actions.addView(discover,new LinearLayout.LayoutParams(0,ui.dp(48),1));root.addView(actions);
            if(!p.optBoolean("support_model_discovery",true))root.addView(ui.text(Json.str(p,"discovery_support_reason","当前供应商不支持自动发现，可手动添加模型"),12,Ui.MUTED));
            root.addView(ui.button("手动添加模型","plus",false,v->addModel(p)));
        }
        EditText search=field(selected.isEmpty()?"搜索供应商":"搜索模型名称或 ID",query,false);root.addView(search);
        if(p!=null){CheckBox hidden=new CheckBox(this);hidden.setText("显示已隐藏模型");hidden.setTextColor(Ui.MUTED);hidden.setChecked(showHidden);root.addView(hidden);hidden.setOnCheckedChangeListener((b,on)->{showHidden=on;render();});}
        ListView list=new ListView(this);list.setDivider(null);list.setDividerHeight(ui.dp(8));root.addView(list,new LinearLayout.LayoutParams(-1,0,1));TextView empty=ui.text("没有匹配项",14,Ui.MUTED);root.addView(empty);list.setEmptyView(empty);
        List<JSONObject> shown=new ArrayList<>();ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>());list.setAdapter(adapter);
        Runnable filter=()->{shown.clear();adapter.clear();JSONArray source=p==null?providers:ModelCatalog.entries(p,showHidden);String q=query.trim().toLowerCase(Locale.ROOT);for(int i=0;i<source.length();i++){JSONObject item=source.optJSONObject(i);if(item==null)continue;String id=Json.str(item,"id"),name=Json.str(item,"name",id);if(!(name+" "+id).toLowerCase(Locale.ROOT).contains(q))continue;shown.add(item);adapter.add(p==null?name+"\n"+ModelCatalog.entries(item,false).length()+" 个模型 · "+(item.optBoolean("enabled",true)?"已启用":"已停用"):(activeProvider.equals(selected)&&activeModel.equals(id)?"✓ ":"")+name+"\n"+id+(ModelCatalog.hidden(p,id)?" · 已隐藏":""));}adapter.notifyDataSetChanged();};filter.run();watch(search,s->{query=s;filter.run();});
        list.setOnItemClickListener((parent,view,index,id)->{if(busy)return;if(p==null){selected=Json.str(shown.get(index),"id");query="";render();}else modelActions(p,shown.get(index));});
        if(busy)enable(root,false);
    }
    private EditText field(String label,String value,boolean secret){EditText e=new androidx.appcompat.widget.AppCompatEditText(this);e.setHint(label);e.setContentDescription(label);e.setText(value);e.setSingleLine(true);e.setTextSize(15);e.setTextColor(Ui.INK);e.setHintTextColor(Ui.MUTED);ui.padding(e,12,12);e.setMinHeight(ui.dp(48));e.setBackground(ui.shape(Ui.WHITE,12,Ui.LINE));e.setSaveEnabled(false);if(secret)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}
    private EditText labeled(LinearLayout box,String label,String value,boolean secret){box.addView(ui.text(label,13,Ui.INK));EditText e=field(label,value,secret);box.addView(e);ui.gap(box,10);return e;}
    private void watch(EditText e,Consumer<String> changed){e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){changed.accept(s.toString());}public void afterTextChanged(Editable e){}});}
    private void confirm(String title,String detail,Runnable action){new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(detail).setNegativeButton("取消",null).setPositiveButton("确定",(d,w)->action.run()).show();}
    private void dialog(String title,LinearLayout box){ScrollView scroll=new ScrollView(this);ui.padding(box,20,12);scroll.addView(box);editor=new MaterialAlertDialogBuilder(this).setTitle(title).setView(scroll).setNegativeButton("关闭",null).create();editor.setOnShowListener(d->editor.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{if(busy)return;if(dirty.getAsBoolean())confirm("放弃未保存的修改？","本次填写的内容还没有保存。",()->editor.dismiss());else editor.dismiss();}));editor.setCancelable(false);editor.setOnKeyListener((d,key,event)->{if(key!=KeyEvent.KEYCODE_BACK)return false;if(event.getAction()==KeyEvent.ACTION_UP&&!busy){if(dirty.getAsBoolean())confirm("放弃未保存的修改？","本次填写的内容还没有保存。",()->editor.dismiss());else editor.dismiss();}return true;});editor.setOnDismissListener(d->{editor=null;dirty=()->false;});editor.show();}
    private String providerPath(JSONObject p){return "/models/"+ApiClient.enc(Json.str(p,"id"));}
    private void saved(Object response){applyCatalog(Json.array(response));if(editor!=null)editor.dismiss();chat.refreshCatalog();render();readActive();}
    private Object mutateAndRead(String method,String path,Object payload)throws Exception{
        Object response=api.call(method,path,payload);if(response instanceof JSONArray)return response;
        JSONObject updated=Json.object(response);if(Boolean.FALSE.equals(updated.opt("success")))throw new IOException(Json.str(updated,"message","服务器拒绝了操作"));
        if(!Json.str(updated,"id").isEmpty()){JSONArray next=new JSONArray();boolean replaced=false;for(int i=0;i<providers.length();i++){JSONObject p=providers.optJSONObject(i);if(p!=null&&Json.str(p,"id").equals(Json.str(updated,"id"))){next.put(updated);replaced=true;}else next.put(p);}if(!replaced)next.put(updated);return next;}
        try{return catalog();}catch(Exception e){throw new IOException("请求已被服务器接受，但列表回读失败。请刷新核对结果后再决定是否重试。",e);}
    }
    private void connection(JSONObject p){
        if(busy)return;LinearLayout box=ui.column();String original=Json.str(p,"base_url",Json.str(p,"default_base_url"));EditText base=labeled(box,"API 服务地址",original,false),key=labeled(box,"新 API Key（留空保留现有密钥）","",true);base.setEnabled(!p.optBoolean("freeze_url"));
        box.addView(ui.text("密钥不会显示在页面、日志或页面重建状态中。测试可以使用未保存的地址和密钥。",12,Ui.MUTED));String path=providerPath(p);
        box.addView(ui.button("测试连接",null,false,v->{if(busy)return;try{JSONObject payload=ModelCatalog.connection(base.getText().toString(),key.getText().toString(),false);request(()->{JSONObject result=Json.object(api.call("POST",path+"/test",payload));return ModelCatalog.outcome(result,"连接可用");},r->Toast.makeText(this,r.toString(),Toast.LENGTH_LONG).show());}catch(Exception e){failed(e);}}));
        box.addView(ui.button("保存连接设置",null,true,v->{if(!mutable())return;try{JSONObject payload=ModelCatalog.connection(base.getText().toString(),key.getText().toString(),true);request(()->mutateAndRead("PUT",path+"/config",payload),this::saved);}catch(Exception e){failed(e);}}));
        box.addView(ui.button(p.optBoolean("enabled",true)?"停用供应商":"启用供应商",null,false,v->{if(!mutable())return;confirm("更改供应商状态？","会影响使用此供应商的模型。",()->{if(mutable())request(()->mutateAndRead("PUT",path+"/config",Json.obj("enabled",!p.optBoolean("enabled",true),"auto_discover",false)),this::saved);});}));
        if(p.optBoolean("is_custom"))box.addView(ui.button("删除自定义供应商",null,false,v->{if(!mutable())return;confirm("删除“"+Json.str(p,"name")+"”？","将移除此供应商的连接设置和模型，请先切换依赖它的智能体。",()->{if(mutable())request(()->mutateAndRead("DELETE","/models/custom-providers/"+ApiClient.enc(Json.str(p,"id")),null),r->{selected="";saved(r);});});}));
        dirty=()->!base.getText().toString().equals(original)||key.length()>0;dialog("供应商连接",box);
    }
    private void modelActions(JSONObject p,JSONObject model){
        String id=Json.str(model,"id");boolean hidden=ModelCatalog.hidden(p,id);List<String> actions=new ArrayList<>();if(ModelCatalog.chatModel(model)&&!hidden&&p.optBoolean("enabled",true))actions.add("设为当前智能体默认模型");actions.add("测试此模型");actions.add("生成参数与上下文");actions.add(hidden?"恢复显示":"隐藏模型");actions.add("删除模型");
        new MaterialAlertDialogBuilder(this).setTitle(Json.str(model,"name",id)).setItems(actions.toArray(new String[0]),(d,i)->{String action=actions.get(i),path=providerPath(p);switch(action){
            case "设为当前智能体默认模型"->{if(!mutable())return;request(()->{api.call("PUT","/models/active",Json.obj("provider_id",Json.str(p,"id"),"model",id,"model_type","chat","scope","agent","agent_id",api.agent));JSONObject slot=Json.object(active().opt("active_llm"));if(!id.equals(Json.str(slot,"model"))||!Json.str(p,"id").equals(Json.str(slot,"provider_id")))throw new IOException("服务器尚未确认模型切换，请刷新后重试");return slot;},r->{activeProvider=Json.str(p,"id");activeModel=id;chat.refreshCatalog();chat.refreshThinking();render();tell("已设为智能体默认模型；已有会话如设置了单独模型，继续使用该会话的选择。",false);});}
            case "测试此模型"->{if(busy)return;confirm("测试“"+id+"”？","会发送少量真实模型请求，可能产生供应商用量。",()->request(()->ModelCatalog.outcome(Json.object(api.call("POST",path+"/models/test",Json.obj("model_id",id))),"模型可用"),r->tell(r.toString(),false)));}
            case "生成参数与上下文"->parameters(p,model);
            case "隐藏模型","恢复显示"->{if(!mutable())return;confirm(action+"？","只更改模型列表可见性，不会自动替换已选模型。",()->{if(mutable())request(()->mutateAndRead("PUT",path+"/models/"+ApiClient.enc(id)+"/visibility",Json.obj("hidden",!hidden)),this::saved);});}
            case "删除模型"->{if(!mutable())return;confirm("删除“"+id+"”？","只删除此供应商下的模型条目；请先切换使用它的会话和智能体。",()->{if(mutable())request(()->mutateAndRead("DELETE",path+"/models/"+ApiClient.enc(id),null),this::saved);});}
        }}).show();
    }
    private void parameters(JSONObject p,JSONObject model){
        LinearLayout box=ui.column();String original=Json.str(model,"max_input_length"),json=Json.object(model.opt("generate_kwargs")).toString();EditText length=labeled(box,"上下文上限 Token（空白使用默认）",original,false),kwargs=labeled(box,"生成参数 JSON",json,false);length.setInputType(InputType.TYPE_CLASS_NUMBER);kwargs.setSingleLine(false);kwargs.setMinLines(5);kwargs.setMaxLines(10);box.addView(ui.text("保留未修改的自定义参数。清空生成参数为 {} 会移除此模型的生成参数覆盖，继续使用供应商默认设置。",12,Ui.MUTED));
        box.addView(ui.button("保存模型参数",null,true,v->{if(!mutable())return;try{JSONObject data=ModelCatalog.modelParameters(model,length.getText().toString(),kwargs.getText().toString());request(()->mutateAndRead("PUT",providerPath(p)+"/models/"+ApiClient.enc(Json.str(model,"id"))+"/config",data),this::saved);}catch(Exception e){failed(e);}}));dirty=()->!length.getText().toString().equals(original)||!kwargs.getText().toString().equals(json);dialog("生成参数与上下文",box);
    }
    private void createProvider(){
        LinearLayout box=ui.column();EditText id=labeled(box,"供应商 ID（字母、数字、下划线或短横线）","",false),name=labeled(box,"显示名称","",false),url=labeled(box,"API 服务地址","",false),key=labeled(box,"API Key","",true);
        Spinner protocol=new Spinner(this);String[] protocols={"OpenAIChatModel","AnthropicChatModel"};protocol.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,protocols));box.addView(ui.text("接口协议",13,Ui.INK));box.addView(protocol);
        box.addView(ui.button("创建供应商",null,true,v->{if(!mutable())return;try{String pid=id.getText().toString().trim(),label=name.getText().toString().trim();if(!pid.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")||label.isEmpty())throw new IllegalArgumentException("请填写有效 ID 和显示名称");JSONObject credentials=ModelCatalog.connection(url.getText().toString(),key.getText().toString(),false);JSONObject payload=Json.obj("id",pid,"name",label,"default_base_url",Json.str(credentials,"base_url"),"api_key",Json.str(credentials,"api_key"),"chat_model",protocols[protocol.getSelectedItemPosition()]);request(()->mutateAndRead("POST","/models/custom-providers",payload),r->{selected=pid;saved(r);});}catch(Exception e){failed(e);}}));dirty=()->id.length()>0||name.length()>0||url.length()>0||key.length()>0;dialog("添加自定义供应商",box);
    }
    private void addModel(JSONObject p){LinearLayout box=ui.column();EditText id=labeled(box,"模型 ID","",false),name=labeled(box,"显示名称（可留空）","",false);box.addView(ui.button("添加模型",null,true,v->{if(!mutable())return;String mid=id.getText().toString().trim();if(mid.isEmpty()){id.setError("请填写模型 ID");return;}JSONObject payload=Json.obj("id",mid,"name",name.length()==0?mid:name.getText().toString().trim());request(()->mutateAndRead("POST",providerPath(p)+"/models",payload),this::saved);}));dirty=()->id.length()>0||name.length()>0;dialog("手动添加模型",box);}
    private void discover(JSONObject p){
        if(busy)return;if(!p.optBoolean("support_model_discovery",true)){tell("当前供应商不支持自动发现，请手动添加模型",true);return;}request(()->{JSONObject result=Json.object(api.call("POST",providerPath(p)+"/discover?save=false",new JSONObject()));ModelCatalog.outcome(result,"发现成功");if(result.optBoolean("used_static_fallback"))throw new IOException("供应商实时发现失败，服务器仅返回静态目录。请检查连接或手动添加，未导入任何模型。");return result;},r->discoveryPreview(p,Json.array(((JSONObject)r).opt("models"))));
    }
    private void discoveryPreview(JSONObject p,JSONArray models){
        LinkedHashMap<String,JSONObject> candidates=new LinkedHashMap<>();Set<String> existing=new HashSet<>();JSONArray known=ModelCatalog.entries(p,true);for(int i=0;i<known.length();i++)existing.add(Json.str(known.optJSONObject(i),"id"));for(int i=0;i<models.length();i++){JSONObject m=models.optJSONObject(i);if(m!=null&&!Json.str(m,"id").isEmpty()&&!existing.contains(Json.str(m,"id")))candidates.put(Json.str(m,"id"),m);}
        List<JSONObject> rows=new ArrayList<>(candidates.values());if(rows.isEmpty()){tell("没有发现尚未添加的模型",false);return;}
        LinearLayout box=ui.column();ui.padding(box,16,12);TextView summary=ui.text("发现 "+rows.size()+" 个新模型，只导入选中项。",13,Ui.MUTED);box.addView(summary);ListView list=new ListView(this);list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);List<String> labels=new ArrayList<>();for(JSONObject m:rows)labels.add(Json.str(m,"name",Json.str(m,"id"))+"\n"+Json.str(m,"id"));list.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_multiple_choice,labels));LinearLayout actions=ui.row();actions.addView(ui.button("全选",null,false,v->{for(int i=0;i<rows.size();i++)list.setItemChecked(i,true);}));actions.addView(ui.button("全不选",null,false,v->{for(int i=0;i<rows.size();i++)list.setItemChecked(i,false);}));box.addView(actions);box.addView(list,new LinearLayout.LayoutParams(-1,ui.dp(300)));
        AlertDialog preview=new MaterialAlertDialogBuilder(this).setTitle("发现的模型").setView(box).setNegativeButton("取消",null).setPositiveButton("导入所选",null).create();discoveryDialog=preview;preview.setOnDismissListener(d->discoveryDialog=null);preview.setOnShowListener(d->preview.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(!mutable())return;List<JSONObject> chosen=new ArrayList<>();for(int i=0;i<rows.size();i++)if(list.isItemChecked(i))chosen.add(rows.get(i));if(chosen.isEmpty()){summary.setText("请至少选择一个模型");return;}preview.setCancelable(false);preview.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);preview.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            request(()->{JSONArray failed=new JSONArray();int added=0;for(JSONObject m:chosen){if(Thread.currentThread().isInterrupted())break;try{api.call("POST",providerPath(p)+"/models",Json.obj("id",Json.str(m,"id"),"name",Json.str(m,"name",Json.str(m,"id")),"model_type",Json.str(m,"model_type","chat")));added++;}catch(ApiClient.ApiException e){if(e.status==401)throw e;failed.put(Json.str(m,"id"));}catch(Exception e){failed.put(Json.str(m,"id"));}}Object fresh;try{fresh=catalog();}catch(Exception e){fresh=JSONObject.NULL;}return Json.obj("providers",fresh,"added",added,"failed",failed);},r->{preview.dismiss();JSONObject result=(JSONObject)r;if(result.opt("providers") instanceof JSONArray fresh)applyCatalog(fresh);render();tell("已添加 "+result.optInt("added")+" 个模型"+(result.isNull("providers")?"；列表刷新失败，请手动刷新":"")+(Json.array(result.opt("failed")).length()==0?"":"；未能添加："+Json.array(result.opt("failed"))),Json.array(result.opt("failed")).length()>0);},e->{preview.setCancelable(true);preview.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);preview.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);summary.setText("导入中断，可刷新列表后重试，已成功添加的模型不会回滚。");failed(e);});
        }));preview.show();
    }
}
