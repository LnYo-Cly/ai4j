package io.github.lnyocly.ai4j.mcp;

import io.github.lnyocly.ai4j.mcp.entity.McpToolDefinition;
import io.github.lnyocly.ai4j.mcp.gateway.McpGatewayKeySupport;
import io.github.lnyocly.ai4j.mcp.util.McpToolConversionSupport;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class McpGatewaySupportTest {

    @Test
    public void shouldBuildAndParseUserGatewayKeys() {
        String clientKey = McpGatewayKeySupport.buildUserClientKey("123", "github");
        String toolKey = McpGatewayKeySupport.buildUserToolKey("123", "search_repositories");

        Assert.assertEquals("user_123_service_github", clientKey);
        Assert.assertEquals("user_123_tool_search_repositories", toolKey);
        Assert.assertTrue(McpGatewayKeySupport.isUserClientKey(clientKey));
        Assert.assertEquals("123", McpGatewayKeySupport.extractUserIdFromClientKey(clientKey));
    }

    @Test
    public void shouldConvertMcpToolDefinitionToOpenAiFunction() {
        Map<String, Object> arrayItems = new HashMap<String, Object>();
        arrayItems.put("type", "string");
        arrayItems.put("description", "tag");

        Map<String, Object> tagsProperty = new HashMap<String, Object>();
        tagsProperty.put("type", "array");
        tagsProperty.put("description", "Tags");
        tagsProperty.put("items", arrayItems);

        Map<String, Object> statusProperty = new HashMap<String, Object>();
        statusProperty.put("type", "string");
        statusProperty.put("description", "Issue status");
        statusProperty.put("enum", Arrays.asList("open", "closed"));

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("status", statusProperty);
        properties.put("tags", tagsProperty);

        Map<String, Object> inputSchema = new HashMap<String, Object>();
        inputSchema.put("properties", properties);
        inputSchema.put("required", Collections.singletonList("status"));

        McpToolDefinition definition = McpToolDefinition.builder()
                .name("create_issue")
                .description("Create issue")
                .inputSchema(inputSchema)
                .build();

        Tool.Function function = McpToolConversionSupport.convertToOpenAiTool(definition);

        Assert.assertEquals("create_issue", function.getName());
        Assert.assertEquals("Create issue", function.getDescription());
        Assert.assertEquals("object", function.getParameters().getType());
        Assert.assertEquals(Collections.singletonList("status"), function.getParameters().getRequired());
        Assert.assertEquals(Arrays.asList("open", "closed"),
                function.getParameters().getProperties().get("status").getEnumValues());
        Assert.assertEquals("string",
                function.getParameters().getProperties().get("tags").getItems().getType());
    }
}
