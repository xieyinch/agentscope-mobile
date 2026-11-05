package io.github.xieyinch.agentscope;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class CommunityContentTest {
    @Test public void explicitNullDoesNotReplaceRealBody() throws Exception {
        JSONObject article=new JSONObject("{\"body_html\":null,\"body_text\":\"完整正文\"}");
        assertEquals("",CommunityContent.first(article,"body_html"));
        assertEquals("完整正文",CommunityContent.first(article,"body_text","summary"));
    }
    @Test public void missingAndNullStringsFallBack() throws Exception {
        JSONObject article=new JSONObject("{\"content\":\"null\",\"body_text\":\"完整回复\\n第二行\"}");
        assertEquals("完整回复\n第二行",CommunityContent.first(article,"missing","content","body_text"));
        assertEquals("",CommunityContent.first(article,"missing"));
    }
}
