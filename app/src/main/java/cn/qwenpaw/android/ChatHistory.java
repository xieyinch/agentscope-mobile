package cn.qwenpaw.android;
import io.github.xieyinch.agentscope.R;
import io.github.xieyinch.agentscope.BuildConfig;

import org.json.*;
import java.time.*;
import java.util.*;

final class ChatHistory {
    static JSONObject newConsoleChat(String sessionId) {
        // The server upgrades this explicit placeholder on the first textual turn, then generates a title.
        return Json.obj("name","New Chat","session_id",sessionId,"user_id","default","channel","console",
                "meta",Json.obj("console_placeholder_name","New Chat"));
    }
    static final long PENDING_TTL_MS=24L*60*60*1000;
    /** Main-surface list/detail paths; PawApp-owned chats stay inside their owning app like on the web. */
    static String listPath(boolean archived) {return "/chats?channel=console&archived="+archived+"&include_app_owned=false";}
    static String detailPath(String chatId) {return "/chats/"+ApiClient.enc(chatId)+"?include_app_owned=false";}
    static String statusPath(String chatId) {return "/chats/"+ApiClient.enc(chatId)+"/status";}
    /** Whether persisted history already contains the user message sent with this client receipt. */
    static boolean hasReceipt(JSONArray history,String receipt) {
        if(receipt==null || receipt.isEmpty())return false;
        for(int i=0;i<history.length();i++){
            JSONObject meta=Json.object(Json.object(history.opt(i)).opt("metadata"));
            JSONObject original=meta.optJSONObject("metadata");if(original==null)original=meta;
            if(receipt.equals(Json.str(original,"qwenpaw_client_message_id")))return true;
        }
        return false;
    }
    static JSONObject pending(String id,JSONArray content,long now) {return Json.obj("id",id,"content",content,"at",now);}
    /**
     * The server persists the running turn's user message only after the run ends, so a reopened running chat
     * restores the locally cached prompt (as the web console does) unless history already has it or it is stale.
     */
    static JSONObject pendingToRestore(String saved,JSONArray history,long now) {
        if(saved==null || saved.isEmpty())return null;
        JSONObject value=Json.object(Json.parse(saved));String id=Json.str(value,"id");
        if(id.isEmpty() || !(value.opt("content") instanceof JSONArray))return null;
        long at=value.optLong("at",0);
        if(at<=0 || now-at>PENDING_TTL_MS || now<at-PENDING_TTL_MS || hasReceipt(history,id))return null;
        return value;
    }
    static boolean syncName(JSONObject current,JSONArray latest) {
        if(current==null || current.optString("id").isEmpty())return false;
        for(int i=0;i<latest.length();i++) {
            JSONObject item=latest.optJSONObject(i);
            if(item==null || !current.optString("id").equals(item.optString("id")))continue;
            String name=Json.str(item,"name");
            if(name.isBlank() || name.equals(current.optString("name")))return false;
            Json.put(current,"name",name);return true;
        }
        return false;
    }
    static List<JSONObject> sorted(JSONArray source) {
        List<JSONObject> result=new ArrayList<>();
        for(int i=0;i<source.length();i++)if(source.optJSONObject(i)!=null)result.add(source.optJSONObject(i));
        result.sort(Comparator.<JSONObject,Boolean>comparing(x->x.optBoolean("pinned")).reversed()
                .thenComparing(Comparator.comparingLong(ChatHistory::time).reversed()));
        return result;
    }
    private static long time(JSONObject chat) {
        Long updated=parse(chat.optString("updated_at"));
        Long created=parse(chat.optString("created_at"));
        return updated!=null?updated:created!=null?created:Long.MIN_VALUE;
    }
    private static Long parse(String value) {
        try{return OffsetDateTime.parse(value).toInstant().toEpochMilli();}catch(Exception ignored){}
        try{return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli();}catch(Exception ignored){}
        try{return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();}catch(Exception ignored){}
        return null;
    }
}
