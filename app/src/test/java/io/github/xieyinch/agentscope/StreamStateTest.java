package io.github.xieyinch.agentscope;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class StreamStateTest {
    private JSONObject event(String id,int seq,String text,boolean delta) throws Exception {
        return new JSONObject().put("type","text").put("msg_id",id).put("index",0)
            .put("sequence_number",seq).put("text",text).put("delta",delta);
    }
    @Test public void replacesFinalSnapshotRatherThanDuplicatingIt() throws Exception {
        StreamState state=new StreamState();state.accept(event("a",1,"你",true));state.accept(event("a",2,"好",true));
        assertEquals("你好",state.accept(event("a",3,"你好",false)));
    }
    @Test public void ignoresReplayedEventsAndPreservesMultipleMessages() throws Exception {
        StreamState state=new StreamState();state.accept(event("a",1,"第一段",true));state.accept(event("a",1,"第一段",true));
        assertEquals("第一段\n\n第二段",state.accept(event("b",2,"第二段",true)));
    }
    @Test public void ignoresNonTextContentRatherThanDisplayingPrivateToolPayloads() throws Exception {
        StreamState state=new StreamState();assertEquals("",state.accept(new JSONObject().put("type","function_call").put("arguments","private")));
    }
}
