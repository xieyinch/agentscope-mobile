package cn.qwenpaw.android;

import org.json.*;
import java.util.*;

/** Shared catalog rules for management and chat. Server false/null never means success. */
public final class ModelCatalog {
    private ModelCatalog(){}
    public static boolean contains(JSONArray ids,String id){for(int i=0;i<ids.length();i++)if(id.equals(ids.optString(i)))return true;return false;}
    public static boolean hidden(JSONObject provider,String id){return contains(Json.array(provider.opt("hidden_model_ids")),id);}
    public static JSONArray entries(JSONObject provider,boolean includeHidden){
        LinkedHashMap<String,JSONObject> byId=new LinkedHashMap<>();
        for(String key:new String[]{"models","discovered_models","extra_models"}){
            JSONArray list=Json.array(provider.opt(key));
            for(int i=0;i<list.length();i++){JSONObject m=list.optJSONObject(i);if(m==null)continue;String id=Json.str(m,"id");
                if(id.isEmpty()||contains(Json.array(provider.opt("removed_model_ids")),id)||!includeHidden&&(hidden(provider,id)||m.optBoolean("hidden")))continue;
                byId.put(id,m);
            }
        }
        JSONArray out=new JSONArray();for(JSONObject m:byId.values())out.put(m);return out;
    }
    public static JSONObject importPayload(JSONObject model){
        String id=Json.str(model,"id"),name=Json.str(model,"name");JSONObject result=Json.obj("id",id,"name",name.isEmpty()?id:name,"model_type",Json.str(model,"model_type","chat"));
        for(String key:new String[]{"template_id","is_free","supports_multimodal","supports_image","supports_video","probe_source"})if(model.has(key)&&!model.isNull(key))Json.put(result,key,model.opt(key));
        return result;
    }
    public static boolean chatModel(JSONObject m){return Json.str(m,"model_type","chat").equals("chat");}
    public static JSONObject connection(String address,String key,boolean save){
        String url=address.trim();if(!url.isEmpty()){
            okhttp3.HttpUrl parsed=okhttp3.HttpUrl.parse(url);
            if(parsed==null||!parsed.username().isEmpty()||!parsed.password().isEmpty()||parsed.query()!=null||parsed.fragment()!=null)throw new IllegalArgumentException("API 地址需为 HTTP(S) 地址，不能包含账号、查询参数或锚点");
        }
        JSONObject payload=new JSONObject();if(save||!url.isEmpty())Json.put(payload,"base_url",url);
        if(!key.trim().isEmpty())Json.put(payload,"api_key",key.trim());if(save)Json.put(payload,"auto_discover",false);return payload;
    }
    public static String outcome(JSONObject result,String success){
        String msg=Json.str(result,"message");
        if(!Boolean.TRUE.equals(result.opt("success")))throw new IllegalArgumentException(msg.isEmpty()?"服务端未确认成功，请检查设置后重试":msg);
        return msg.isEmpty()?success:success+"\n"+msg;
    }
    public static JSONObject modelParameters(JSONObject model,String context,String kwargs){
        JSONObject params;try{Object value=new JSONTokener(kwargs.trim().isEmpty()?"{}":kwargs).nextValue();if(!(value instanceof JSONObject))throw new JSONException("not object");params=(JSONObject)value;}catch(JSONException e){throw new IllegalArgumentException("生成参数必须是 JSON 对象，例如 {\"temperature\":0.7}");}
        Object max=params.opt("max_tokens");if(max!=null&&max!=JSONObject.NULL&&(!(max instanceof Number)||!Double.isFinite(((Number)max).doubleValue())||((Number)max).doubleValue()%1!=0||((Number)max).doubleValue()<1||((Number)max).doubleValue()>Integer.MAX_VALUE))throw new IllegalArgumentException("max_tokens 必须是大于 0 的整数");
        if(max instanceof Number)Json.put(params,"max_tokens",((Number)max).longValue());
        for(String key:new String[]{"temperature","top_p"})if(params.has(key)&&!params.isNull(key)){
            Object value=params.opt(key);double limit=key.equals("top_p")?1:2;
            if(!(value instanceof Number)||!Double.isFinite(((Number)value).doubleValue())||((Number)value).doubleValue()<0||((Number)value).doubleValue()>limit)throw new IllegalArgumentException(key+" 必须是 0–"+limit+" 之间的数字");
        }
        JSONObject out=Json.obj("model_type",Json.str(model,"model_type","chat"),"generate_kwargs",params);
        if(context.trim().isEmpty())Json.put(out,"max_input_length",JSONObject.NULL);
        else try{long length=Long.parseLong(context.trim());if(length<1000)throw new NumberFormatException();Json.put(out,"max_input_length",length);}catch(NumberFormatException e){throw new IllegalArgumentException("上下文上限必须是不小于 1000 的整数，留空使用默认值");}
        return out;
    }
}
