package io.github.xieyinch.agentscope;

import org.json.*;
import java.util.*;

/** Merge runtime content snapshots/deltas by message and block, avoiding duplicate final text. */
final class StreamState {
    private final LinkedHashMap<String,String> blocks=new LinkedHashMap<>();
    private long sequence=-1;
    synchronized String accept(JSONObject event) {
        long seq=event.optLong("sequence_number",-1);
        if(seq>=0&&seq<=sequence)return render();
        if(seq>=0)sequence=seq;
        String type=event.optString("type","");
        if(type.equals("text")&&event.has("text")) {
            String key=event.optString("msg_id","message")+":"+event.optInt("index",0);
            String value=event.optString("text","");
            blocks.put(key,event.optBoolean("delta",false)?blocks.getOrDefault(key,"")+value:value);
        }
        return render();
    }
    synchronized String render(){return String.join("\n\n",blocks.values());}
}
