package io.github.xieyinch.agentscope;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChatContentTest {
    @Test public void includesUserModelsAndDeduplicatesWithoutDecisionModels() throws Exception {
        JSONObject p=new JSONObject("{\"models\":[{\"id\":\"base\"}],\"extra_models\":[{\"id\":\"base\"},{\"id\":\"custom\"},{\"id\":\"decision\",\"model_type\":\"decision\"}]}");
        JSONArray models=ChatContent.models(p);assertEquals(2,models.length());assertEquals("custom",models.getJSONObject(1).getString("id"));
    }
    @Test public void selectedModelTargetsOnlyCurrentAgent() throws Exception {
        JSONObject selected=ChatContent.selection("custom-provider","a/b:model","agent-2");
        assertEquals("agent",selected.getString("scope"));assertEquals("agent-2",selected.getString("agent_id"));assertEquals("chat",selected.getString("model_type"));assertEquals("a/b:model",selected.getString("model"));
    }
    @Test public void attachmentOnlyMessageKeepsActualUploadedPath() throws Exception {
        JSONArray content=ChatContent.content("",new JSONArray().put(ChatContent.attachment("/media/hash_report.pdf","report.pdf","application/pdf")));
        assertEquals(1,content.length());assertEquals("/media/hash_report.pdf",content.getJSONObject(0).getString("file_url"));assertEquals("file",content.getJSONObject(0).getString("type"));assertEquals("附件 · report.pdf",ChatContent.display(content));
    }
    @Test public void mapsMediaToBackendContentTypes() throws Exception {
        assertEquals("/media/a.png",ChatContent.attachment("/media/a.png","a.png","image/png").getString("image_url"));
        assertEquals("/media/a.mp4",ChatContent.attachment("/media/a.mp4","a.mp4","video/mp4").getString("video_url"));
        assertEquals("/media/a.wav",ChatContent.attachment("/media/a.wav","a.wav","audio/wav").getString("data"));
        JSONArray parts=ChatContent.content("分析附件",new JSONArray().put(ChatContent.attachment("a","a","application/pdf")));assertEquals("text",parts.getJSONObject(0).getString("type"));assertEquals(2,parts.length());
    }
}
