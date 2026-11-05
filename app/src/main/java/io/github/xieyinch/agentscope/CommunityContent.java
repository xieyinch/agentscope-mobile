package io.github.xieyinch.agentscope;

import org.json.JSONObject;

/** Optional community text can be absent or explicitly JSON null. */
final class CommunityContent {
    static String first(JSONObject object,String... keys){
        for(String key:keys){
            Object value=object.opt(key);
            if(!(value instanceof String))continue;
            String text=(String)value;
            if(!text.trim().isEmpty()&&!"null".equalsIgnoreCase(text.trim()))return text;
        }
        return "";
    }
}
