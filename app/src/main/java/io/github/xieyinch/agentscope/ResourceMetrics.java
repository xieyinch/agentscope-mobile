package io.github.xieyinch.agentscope;
import org.json.JSONObject;
import java.util.Locale;
/** Platform snapshot values: missing measurements are not zero usage. */
final class ResourceMetrics {
    static final String[][] FIELDS={
        {"memoryUsageMbMax","内存峰值","MB"},{"memoryUsageMbAvg","平均内存","MB"},
        {"memoryUtilizationMax","内存利用率峰值","%"},{"vcpuCoresMax","CPU 使用峰值","核"},
        {"vcpuCoresAvg","CPU 平均使用","核"},{"vcpuUtilizationMax","CPU 利用率峰值","%"}
    };
    static Double number(JSONObject data,String key){
        Object raw=data.opt(key);if(!(raw instanceof Number)&&!(raw instanceof String))return null;
        try{double value=Double.parseDouble(raw.toString());return Double.isFinite(value)&&value>=0?value:null;}catch(NumberFormatException error){return null;}
    }
    static boolean hasSamples(JSONObject data){for(String[] field:FIELDS)if(number(data,field[0])!=null)return true;return false;}
    static String format(JSONObject data,String key,String unit){Double value=number(data,key);return value==null?"暂无采样":String.format(Locale.getDefault(),"%.2f %s",value,unit);}
}
