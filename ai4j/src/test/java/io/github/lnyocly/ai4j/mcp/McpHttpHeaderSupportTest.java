package io.github.lnyocly.ai4j.mcp;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class McpHttpHeaderSupportTest {

    @Test
    public void createsAndEncodesHeadersForReachablePrimitiveProperties() {
        Map<String, Object> region = schema("string", "Region");
        Map<String, Object> count = schema("integer", "Count");
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("region", region);
        properties.put("count", count);
        Map<String, Object> schema = new HashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);

        Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.put("region", "cn-北京");
        arguments.put("count", 7);
        Map<String, String> headers = McpHttpHeaderSupport.createToolParameterHeaders(schema, arguments);

        Assert.assertEquals("7", headers.get("Mcp-Param-Count"));
        Assert.assertTrue(headers.get("Mcp-Param-Region").startsWith("=?base64?"));
    }

    @Test
    public void rejectsAnnotationsOutsidePropertiesPath() {
        Map<String, Object> item = schema("string", "Invalid");
        Map<String, Object> schema = new HashMap<String, Object>();
        schema.put("type", "array");
        schema.put("items", item);

        Assert.assertFalse(McpHttpHeaderSupport.isValidToolSchema(schema));
    }

    @Test
    public void rejectsCaseInsensitiveDuplicateHeaderAnnotations() {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("region", schema("string", "Region"));
        properties.put("otherRegion", schema("string", "region"));
        Map<String, Object> schema = new HashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);

        Assert.assertFalse(McpHttpHeaderSupport.isValidToolSchema(schema));
    }

    private static Map<String, Object> schema(String type, String header) {
        Map<String, Object> schema = new HashMap<String, Object>();
        schema.put("type", type);
        schema.put("x-mcp-header", header);
        return schema;
    }
}
