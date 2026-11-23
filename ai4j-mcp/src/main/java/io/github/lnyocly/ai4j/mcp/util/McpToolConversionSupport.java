package io.github.lnyocly.ai4j.mcp.util;

import io.github.lnyocly.ai4j.mcp.entity.McpToolDefinition;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tool 与 OpenAI Tool.Function 转换辅助类
 */
public final class McpToolConversionSupport {

    private McpToolConversionSupport() {
    }

    public static Tool.Function convertToOpenAiTool(McpToolDefinition mcpTool) {
        Tool.Function function = new Tool.Function();
        function.setName(mcpTool.getName());
        function.setDescription(mcpTool.getDescription());

        if (mcpTool.getInputSchema() != null) {
            function.setParameters(convertInputSchema(mcpTool.getInputSchema()));
        }

        return function;
    }

    public static Tool.Function.Parameter convertInputSchema(Map<String, Object> inputSchema) {
        Tool.Function.Parameter parameter = new Tool.Function.Parameter();
        parameter.setType("object");

        Object properties = inputSchema.get("properties");
        Object required = inputSchema.get("required");

        if (properties instanceof Map<?, ?>) {
            Map<String, Tool.Function.Property> convertedProperties = new HashMap<String, Tool.Function.Property>();

            @SuppressWarnings("unchecked")
            Map<String, Object> propsMap = (Map<String, Object>) properties;
            propsMap.forEach((key, value) -> {
                if (value instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> propMap = (Map<String, Object>) value;
                    convertedProperties.put(key, convertToProperty(propMap));
                }
            });

            parameter.setProperties(convertedProperties);
        }

        if (required instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<String> requiredList = (List<String>) required;
            parameter.setRequired(requiredList);
        }

        return parameter;
    }

    public static Tool.Function.Property convertToProperty(Map<String, Object> propMap) {
        Tool.Function.Property property = new Tool.Function.Property();
        property.setType((String) propMap.get("type"));
        property.setDescription((String) propMap.get("description"));

        Object enumObj = propMap.get("enum");
        if (enumObj instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<String> enumValues = (List<String>) enumObj;
            property.setEnumValues(enumValues);
        }

        Object itemsObj = propMap.get("items");
        if (itemsObj instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> itemsMap = (Map<String, Object>) itemsObj;
            property.setItems(convertToProperty(itemsMap));
        }

        return property;
    }
}
