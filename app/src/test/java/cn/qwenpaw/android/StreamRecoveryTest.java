package cn.qwenpaw.android;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class StreamRecoveryTest {
    @Test public void jsonStrFallsBackForMissingAndNullValues() throws Exception {
        JSONObject o=new JSONObject("{\"a\":null,\"b\":\"x\",\"c\":3}");
        assertEquals("f",Json.str(o,"a","f"));
        assertEquals("f",Json.str(o,"missing","f"));
        assertEquals("x",Json.str(o,"b","f"));
        assertEquals("3",Json.str(o,"c","f"));
        assertEquals("",Json.str(null,"b"));
    }
    @Test public void mainSurfacePathsExcludeAppOwnedChats() {
        assertEquals("/chats?channel=console&archived=false&include_app_owned=false",ChatHistory.listPath(false));
        assertEquals("/chats/a%2Fb?include_app_owned=false",ChatHistory.detailPath("a/b"));
        assertEquals("/chats/a%2Fb/status",ChatHistory.statusPath("a/b"));
    }
    @Test public void pendingPromptIsRestoredUntilHistoryHasItsReceipt() {
        long now=1_000_000_000L;
        JSONArray content=Json.arr(Json.obj("type","text","text","long task"));
        String saved=ChatHistory.pending("r1",content,now).toString();
        JSONArray history=Json.arr(Json.obj("role","user","content",Json.arr(Json.obj("type","text","text","older"))),
            Json.obj("role","assistant","content",Json.arr(Json.obj("type","text","text","older answer"))));
        JSONObject restored=ChatHistory.pendingToRestore(saved,history,now+60_000);
        assertNotNull(restored);assertEquals("r1",Json.str(restored,"id"));
        history.put(Json.obj("role","user","metadata",Json.obj("metadata",Json.obj("qwenpaw_client_message_id","r1"))));
        assertNull(ChatHistory.pendingToRestore(saved,history,now+60_000));
        assertNull(ChatHistory.pendingToRestore(saved,new JSONArray(),now+ChatHistory.PENDING_TTL_MS+1));
        assertNull(ChatHistory.pendingToRestore("",new JSONArray(),now));
        assertNull(ChatHistory.pendingToRestore("not json",new JSONArray(),now));
    }
    @Test public void reopenedRunningChatKeepsPreviousAnswerAndShowsPendingPrompt() {
        Transcript t=new Transcript();
        t.history(Json.arr(Json.obj("role","user","content",Json.arr(Json.obj("type","text","text","q1"))),
            Json.obj("role","assistant","content",Json.arr(Json.obj("type","text","text","a1")))));
        t.beginTurn();t.addUser("r2",Json.arr(Json.obj("type","text","text","q2")));
        t.accept(Json.obj("object","content","msg_id","live","index",0,"type","text","text","a2","delta",true,"sequence_number",1));
        List<String> texts=new ArrayList<>();for(Transcript.Row r:t.rows())texts.add(r.text);
        assertEquals(List.of("q1","a1","q2","a2"),texts);
        Transcript.Row previous=t.rows().get(1);assertTrue(previous.done);
        assertFalse(t.rows().get(3).done);
    }
    @Test public void nullOptionalFieldsNeverRenderAsNullText() throws Exception {
        Transcript t=new Transcript();
        t.history(new JSONArray("[{\"role\":\"assistant\",\"status\":null,\"content\":[{\"type\":\"file\",\"filename\":null,\"file_url\":\"/files/report.pdf\"}]}]"));
        assertEquals("report.pdf",t.rows().get(0).title);
    }
    @Test public void reconnectBudgetResetsOnlyAfterLiveEventFollowingReplay() {
        StreamRecovery r=new StreamRecovery();
        for(int i=0;i<StreamRecovery.FAST_ATTEMPTS;i++)r.nextDelay();
        assertTrue(r.slow());assertEquals(StreamRecovery.SLOW_DELAY_MS,r.nextDelay());
        r.streamStarted(true,0);r.event("response",1);r.event("replay_end",2);
        assertTrue("replayed events alone do not prove the connection is healthy",r.slow());
        r.event("content",3);assertFalse(r.slow());assertEquals(1000,r.nextDelay());
    }
    @Test public void silentStreamIsCheckedAndDeclaredDeadOnlyAfterRepeatedFailures() {
        StreamRecovery r=new StreamRecovery();r.streamStarted(false,0);
        assertFalse(r.idleCheckDue(StreamRecovery.IDLE_MS-1));assertTrue(r.idleCheckDue(StreamRecovery.IDLE_MS));
        assertFalse(r.statusFailed());r.statusOk();assertFalse(r.statusFailed());assertTrue(r.statusFailed());
    }
}
