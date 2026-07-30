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

    @Test
    public void acceptsExactIntegerValuesAndRejectsFractionalOrUnsafeValues() {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("count", schema("integer", "Count"));
        Map<String, Object> schema = new HashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);

        Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.put("count", 42.0d);
        Assert.assertEquals("42", McpHttpHeaderSupport.createToolParameterHeaders(schema, arguments)
                .get("Mcp-Param-Count"));

        arguments.put("count", 1.5d);
        assertInvalidInteger(schema, arguments);

        arguments.put("count", 9007199254740992L);
        assertInvalidInteger(schema, arguments);
    }

    @Test
    public void comparesIntegerHeadersNumericallyButStringHeadersExactly() {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("count", schema("integer", "Count"));
        properties.put("code", schema("string", "Code"));
        Map<String, Object> schema = new HashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);

        Assert.assertTrue(McpHttpHeaderSupport.areToolParameterHeaderValuesEquivalent(
                schema, "mcp-param-count", "42", "42.0"));
        Assert.assertFalse(McpHttpHeaderSupport.areToolParameterHeaderValuesEquivalent(
                schema, "Mcp-Param-Code", "01", "1"));
    }

    private static void assertInvalidInteger(Map<String, Object> schema, Map<String, Object> arguments) {
        try {
            McpHttpHeaderSupport.createToolParameterHeaders(schema, arguments);
            Assert.fail("expected an invalid integer header value");
        } catch (IllegalArgumentException expected) {
            // Expected: fractional and unsafe values cannot be mirrored into MCP headers.
        }
    }

    private static Map<String, Object> schema(String type, String header) {
        Map<String, Object> schema = new HashMap<String, Object>();
        schema.put("type", type);
        schema.put("x-mcp-header", header);
        return schema;
    }
}
