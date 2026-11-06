package cn.qwenpaw.android;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class ModelCatalogTest {
    @Test public void mergesDiscoveryAndOverridesAndFiltersHiddenRemoved(){
        JSONObject p=Json.obj("models",Json.arr(Json.obj("id","base","name","old")),"discovered_models",Json.arr(Json.obj("id","remote"),Json.obj("id","hidden"),Json.obj("id","gone")),"extra_models",Json.arr(Json.obj("id","base","name","custom")),"hidden_model_ids",Json.arr("hidden"),"removed_model_ids",Json.arr("gone"));
        JSONArray rows=ModelCatalog.entries(p,false);assertEquals(2,rows.length());assertEquals("custom",rows.optJSONObject(0).optString("name"));assertEquals("remote",rows.optJSONObject(1).optString("id"));assertEquals(3,ModelCatalog.entries(p,true).length());
    }
    @Test public void leavesExistingKeyAloneAndDoesNotDiscoverOnSave(){JSONObject p=ModelCatalog.connection("https://provider.example/v1","",true);assertFalse(p.has("api_key"));assertFalse(p.optBoolean("auto_discover"));assertEquals(0,ModelCatalog.connection("","",false).length());}
    @Test public void falseOrMissingSuccessCannotBeReportedSuccessful(){for(JSONObject p:new JSONObject[]{Json.obj("success",false,"message","invalid key"),new JSONObject()})try{ModelCatalog.outcome(p,"ok");fail();}catch(IllegalArgumentException expected){}assertEquals("ok",ModelCatalog.outcome(Json.obj("success",true),"ok"));}
    @Test public void editingParametersKeepsUnknownGenerationOptions(){JSONObject p=ModelCatalog.modelParameters(Json.obj("model_type","chat"),"128000","{\"max_tokens\":1024,\"custom\":{\"enabled\":true}}");assertEquals(128000,p.optLong("max_input_length"));assertTrue(p.optJSONObject("generate_kwargs").optJSONObject("custom").optBoolean("enabled"));assertTrue(ModelCatalog.modelParameters(new JSONObject(),"","{}").isNull("max_input_length"));}
    @Test public void rejectsMalformedParameters(){for(String s:new String[]{"[]","{\"max_tokens\":true}","{\"max_tokens\":0}","{\"temperature\":3}","{\"top_p\":1.1}"})try{ModelCatalog.modelParameters(new JSONObject(),"",s);fail(s);}catch(IllegalArgumentException expected){}}
}
