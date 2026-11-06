package io.github.xieyinch.agentscope;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class ResourceMetricsTest {
    @Test public void missingNullAndInvalidMetricsAreNotZero() throws Exception {
        JSONObject data=new JSONObject("{\"memoryUsageMbMax\":null,\"vcpuCoresAvg\":\"NaN\",\"vcpuCoresMax\":-1}");
        assertFalse(ResourceMetrics.hasSamples(data));assertEquals("暂无采样",ResourceMetrics.format(data,"memoryUsageMbMax","MB"));assertNull(ResourceMetrics.number(data,"missing"));
    }
    @Test public void zeroAndNumericStringsAreRealSamples() throws Exception {
        JSONObject data=new JSONObject("{\"memoryUsageMbMax\":\"128.5\",\"vcpuCoresAvg\":0}");
        assertTrue(ResourceMetrics.hasSamples(data));assertEquals(128.5,ResourceMetrics.number(data,"memoryUsageMbMax"),0.001);assertEquals(0,ResourceMetrics.number(data,"vcpuCoresAvg"),0.001);assertTrue(ResourceMetrics.format(data,"memoryUsageMbMax","MB").endsWith("MB"));
    }
}
