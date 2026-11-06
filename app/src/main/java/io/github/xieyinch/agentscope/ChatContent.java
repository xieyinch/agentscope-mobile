package io.github.xieyinch.agentscope;

import org.json.*;
import java.util.*;

/** Backend contracts shared by native model selection and chat attachments. */
final class ChatContent {
    static JSONArray models(JSONObject provider){
        JSONArray out=new JSONArray();Set<String> ids=new HashSet<>();
        for(String key:new String[]{"models","extra_models"}){
            JSONArray entries=provider.optJSONArray(key);if(entries==null)continue;
            for(int i=0;i<entries.length();i++){JSONObject model=entries.optJSONObject(i);if(model==null)continue;
                String id=CommunityContent.first(model,"id");
                if(!id.isEmpty()&&ids.add(id)&&!"decision".equals(model.optString("model_type")))out.put(model);
            }
        }
        return out;
    }
    static JSONObject selection(String provider,String model,String agent){
        return Api.obj("provider_id",provider,"model",model,"model_type","chat","scope","agent","agent_id",agent);
    }
    static JSONObject attachment(String url,String filename,String mime){
        if(mime.startsWith("image/"))return Api.obj("type","image","image_url",url,"file_name",filename);
        if(mime.startsWith("video/"))return Api.obj("type","video","video_url",url,"file_name",filename);
        if(mime.startsWith("audio/"))return Api.obj("type","audio","data",url,"file_name",filename);
        return Api.obj("type","file","file_url",url,"file_name",filename);
    }
    static JSONArray content(String prompt,JSONArray attachments){
        JSONArray out=new JSONArray();if(!prompt.trim().isEmpty())out.put(Api.obj("type","text","text",prompt));
        for(int i=0;i<attachments.length();i++)out.put(attachments.opt(i));return out;
    }
    static String display(Object value){
        if(value instanceof JSONArray){StringBuilder out=new StringBuilder();JSONArray items=(JSONArray)value;for(int i=0;i<items.length();i++){String text=display(items.opt(i));if(!text.isEmpty()){if(out.length()>0)out.append('\n');out.append(text);}}return out.toString();}
        if(value instanceof JSONObject){JSONObject item=(JSONObject)value;String type=item.optString("type");
            if(Arrays.asList("file","image","audio","video").contains(type)){String name=CommunityContent.first(item,"file_name","filename","name");return "附件 · "+(name.isEmpty()?type:name);}
        }
        return Api.text(value);
    }
}
