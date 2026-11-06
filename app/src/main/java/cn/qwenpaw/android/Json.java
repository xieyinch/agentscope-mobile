package cn.qwenpaw.android;
import io.github.xieyinch.agentscope.R;
import io.github.xieyinch.agentscope.BuildConfig;

import org.json.*;

/** Small JSON helpers; unexpected fields are retained for protocol compatibility. */
public final class Json {
    private Json() {}
    public static JSONObject obj(Object... pairs) {
        JSONObject o = new JSONObject();
        for (int i = 0; i < pairs.length; i += 2) put(o, pairs[i].toString(), pairs[i + 1]);
        return o;
    }
    public static void put(JSONObject o, String k, Object v) {
        try { o.put(k, v == null ? JSONObject.NULL : v); }
        catch (JSONException e) { throw new IllegalArgumentException(e); }
    }
    public static JSONArray arr(Object... values) {
        JSONArray a = new JSONArray(); for (Object v : values) a.put(v); return a;
    }
    public static JSONObject object(Object v) { return v instanceof JSONObject ? (JSONObject) v : new JSONObject(); }
    public static JSONArray array(Object v) { return v instanceof JSONArray ? (JSONArray) v : new JSONArray(); }
    public static Object parse(String s) {
        try { return new JSONTokener(s).nextValue(); } catch (JSONException e) { return s; }
    }
    public static String text(Object v) {
        if (v == null || v == JSONObject.NULL) return "";
        if (v instanceof JSONArray a) {
            StringBuilder b = new StringBuilder();
            for (int i=0;i<a.length();i++) { if (b.length()>0) b.append('\n'); b.append(text(a.opt(i))); }
            return b.toString();
        }
        if (v instanceof JSONObject o) {
            if (o.has("text")) return o.optString("text");
            try { return o.toString(2); } catch (JSONException e) { return o.toString(); }
        }
        return v.toString();
    }
    /** Android's optString returns "null" for JSON null; server Optional fields must fall back instead. */
    public static String str(JSONObject o, String k, String fallback) {
        if (o == null || !o.has(k) || o.isNull(k)) return fallback;
        return o.optString(k, fallback);
    }
    public static String str(JSONObject o, String k) { return str(o, k, ""); }
    public static JSONObject copy(JSONObject o) { return object(parse(o.toString())); }
}
