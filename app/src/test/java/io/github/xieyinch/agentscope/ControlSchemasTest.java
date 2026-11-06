package io.github.xieyinch.agentscope;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ControlSchemasTest {
    @Test public void mcpUpdateDropsReadOnlyFieldsAndPreservesTransportSettings() throws Exception {JSONObject result=ControlSchemas.mcp(new JSONObject("{\"key\":\"server\",\"name\":\"name\",\"oauth_status\":{},\"headers\":{\"Authorization\":\"test\"},\"transport\":\"sse\"}"));assertFalse(result.has("key"));assertFalse(result.has("oauth_status"));assertEquals("sse",result.getString("transport"));assertEquals("test",result.getJSONObject("headers").getString("Authorization"));}
    @Test public void mcpCreateRequiresAnExplicitKeyAndTransportTarget() throws Exception {JSONObject client=ControlSchemas.mcp(new JSONObject());client.put("name","MCP");assertThrows(IllegalArgumentException.class,()->ControlSchemas.validateMcpCreate(Api.obj("client_key","","client",client)));client.put("url","https://example.invalid/mcp");ControlSchemas.validateMcpCreate(Api.obj("client_key","server","client",client));}
    @Test public void restoreDoesNotSelectSecretsOrGlobalConfigByDefault() throws Exception {JSONObject result=ControlSchemas.restore(new JSONArray().put("a"));assertFalse(result.getBoolean("include_secrets"));assertFalse(result.getBoolean("include_global_config"));assertTrue(result.getBoolean("preserve_local_protected_config"));assertEquals("a",result.getJSONArray("agent_ids").getString(0));}
}
