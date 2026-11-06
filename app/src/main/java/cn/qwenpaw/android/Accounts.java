package cn.qwenpaw.android;
import io.github.xieyinch.agentscope.R;
import io.github.xieyinch.agentscope.BuildConfig;

import okhttp3.HttpUrl;
import org.json.*;
import java.util.*;

/**
 * Saved server accounts. Each one owns its address, sign-in name, selected agent and last chat;
 * tokens and opted-in passwords live in TokenVault under the same id, local settings under {@link #settingPrefix}.
 */
final class Accounts {
    private Accounts() {}
    static JSONObject create(String id,String name,String base,String username){
        return Json.obj("id",id,"name",name.trim(),"base",base,"username",username,"agent","default","lastChat","","anonymous",false,"remember",false);
    }
    static JSONArray parse(String saved){
        JSONArray result=new JSONArray(),source=Json.array(Json.parse(saved==null?"":saved));
        for(int i=0;i<source.length();i++){JSONObject item=source.optJSONObject(i);if(item!=null&&!Json.str(item,"id").isEmpty()&&!Json.str(item,"base").isEmpty())result.put(item);}
        return result;
    }
    static JSONObject find(JSONArray list,String id){
        if(id==null||id.isEmpty())return null;
        for(int i=0;i<list.length();i++){JSONObject item=list.optJSONObject(i);if(item!=null&&id.equals(Json.str(item,"id")))return item;}
        return null;
    }
    /** The same server and sign-in name is the same account; reuse it instead of saving a duplicate. */
    static JSONObject match(JSONArray list,String base,String username){
        for(int i=0;i<list.length();i++){JSONObject item=list.optJSONObject(i);if(item!=null&&base.equals(Json.str(item,"base"))&&username.equals(Json.str(item,"username")))return item;}
        return null;
    }
    static JSONArray without(JSONArray list,String id){
        JSONArray result=new JSONArray();
        for(int i=0;i<list.length();i++){JSONObject item=list.optJSONObject(i);if(item!=null&&!id.equals(Json.str(item,"id")))result.put(item);}
        return result;
    }
    /** Server shown without the default port, e.g. {@code paw.example.com/team}. */
    static String host(String base){
        try{HttpUrl url=HttpUrl.get(base);String host=url.host()+(url.port()==HttpUrl.defaultPort(url.scheme())?"":":"+url.port());return host+url.encodedPath().replaceAll("/+$","");}
        catch(IllegalArgumentException e){return base;}
    }
    static String label(JSONObject account){
        if(account==null)return "";
        String name=Json.str(account,"name").trim();if(!name.isEmpty())return name;
        String user=Json.str(account,"username"),host=host(Json.str(account,"base"));
        return user.isEmpty()||account.optBoolean("anonymous")?host:user+" @ "+host;
    }
    static String settingPrefix(String id){return "acct:"+id+"|";}
    /** Single-account builds keyed local settings by {@code base|username|}; map those keys under the account id. */
    static Map<String,String> legacyKeys(Collection<String> keys,String base,String username,String id){
        String prefix=base+"|"+username+"|";Map<String,String> result=new LinkedHashMap<>();
        for(String key:keys)if(key.startsWith(prefix)&&key.length()>prefix.length())result.put(key,settingPrefix(id)+key.substring(prefix.length()));
        return result;
    }
}
