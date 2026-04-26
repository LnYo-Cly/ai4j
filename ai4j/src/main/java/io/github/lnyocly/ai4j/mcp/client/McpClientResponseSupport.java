package io.github.lnyocly.ai4j.mcp.client;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.mcp.entity.McpPrompt;
import io.github.lnyocly.ai4j.mcp.entity.McpPromptResult;
import io.github.lnyocly.ai4j.mcp.entity.McpResource;
import io.github.lnyocly.ai4j.mcp.entity.McpResourceContent;
import io.github.lnyocly.ai4j.mcp.entity.McpToolDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 客户端响应解析辅助类
 */
public final class McpClientResponseSupport {

    private McpClientResponseSupport() {
    }

    public static List<McpToolDefinition> parseToolsListResponse(Object result) {
        List<McpToolDefinition> tools = new ArrayList<McpToolDefinition>();

        try {
            Map<String, Object> resultMap = asMap(result);
            if (resultMap != null) {
                List<Object> toolsList = asList(resultMap.get("tools"));
                for (Object toolObj : toolsList) {
                    Map<String, Object> toolMap = asMap(toolObj);
                    if (toolMap != null) {
                        McpToolDefinition tool = McpToolDefinition.builder()
                                .name((String) toolMap.get("name"))
                                .description((String) toolMap.get("description"))
                                .inputSchema(asMap(toolMap.get("inputSchema")))
                                .build();
                        tools.add(tool);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return tools;
    }

    public static String parseToolCallResponse(Object result) {
        try {
            Map<String, Object> resultMap = asMap(result);
            if (resultMap != null) {
                Object content = resultMap.get("content");
                if (content != null) {
                    List<Object> contentList = asList(content);
                    if (!contentList.isEmpty()) {
                        StringBuilder resultText = new StringBuilder();
                        for (Object item : contentList) {
                            Map<String, Object> itemMap = asMap(item);
                            if (itemMap != null) {
                                Object text = itemMap.get("text");
                                if (text != null) {
                                    resultText.append(text.toString());
                                }
                            }
                        }
                        return resultText.toString();
                    }
                    return content.toString();
                }

                return JSON.toJSONString(result);
            }

            return result != null ? result.toString() : "";
        } catch (Exception e) {
            return "解析结果失败: " + e.getMessage();
        }
    }

    public static List<McpResource> parseResourcesListResponse(Object result) {
        List<McpResource> resources = new ArrayList<McpResource>();
        try {
            Map<String, Object> resultMap = asMap(result);
            if (resultMap == null) {
                return resources;
            }
            List<Object> resourceList = asList(resultMap.get("resources"));
            for (Object resourceObj : resourceList) {
                Map<String, Object> resourceMap = asMap(resourceObj);
                if (resourceMap == null) {
                    continue;
                }
                resources.add(McpResource.builder()
                        .uri(stringValue(resourceMap.get("uri")))
                        .name(stringValue(resourceMap.get("name")))
                        .description(stringValue(resourceMap.get("description")))
                        .mimeType(stringValue(resourceMap.get("mimeType")))
                        .size(longValue(resourceMap.get("size")))
                        .build());
            }
        } catch (Exception ignored) {
        }
        return resources;
    }

    public static McpResourceContent parseResourceReadResponse(Object result) {
        try {
            Map<String, Object> resultMap = asMap(result);
            if (resultMap == null) {
                return null;
            }
            List<Object> contentList = asList(resultMap.get("contents"));
            if (contentList.isEmpty()) {
                return null;
            }
            if (contentList.size() == 1) {
                return parseSingleResourceContent(contentList.get(0));
            }

            List<Object> contents = new ArrayList<Object>(contentList.size());
            String uri = null;
            String mimeType = null;
            for (Object contentObj : contentList) {
                Map<String, Object> itemMap = asMap(contentObj);
                if (itemMap == null) {
                    continue;
                }
                if (uri == null) {
                    uri = stringValue(itemMap.get("uri"));
                }
                if (mimeType == null) {
                    mimeType = stringValue(itemMap.get("mimeType"));
                }
                Object extracted = extractResourceContent(itemMap);
                contents.add(extracted == null ? itemMap : extracted);
            }
            return McpResourceContent.builder()
                    .uri(uri)
                    .mimeType(mimeType)
                    .contents(contents)
                    .build();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static List<McpPrompt> parsePromptsListResponse(Object result) {
        List<McpPrompt> prompts = new ArrayList<McpPrompt>();
        try {
            Map<String, Object> resultMap = asMap(result);
            if (resultMap == null) {
                return prompts;
            }
            List<Object> promptList = asList(resultMap.get("prompts"));
            for (Object promptObj : promptList) {
                Map<String, Object> promptMap = asMap(promptObj);
                if (promptMap == null) {
                    continue;
                }
                prompts.add(McpPrompt.builder()
                        .name(stringValue(promptMap.get("name")))
                        .description(stringValue(promptMap.get("description")))
                        .arguments(asMap(promptMap.get("arguments")))
                        .build());
            }
        } catch (Exception ignored) {
        }
        return prompts;
    }

    public static McpPromptResult parsePromptGetResponse(String name, Object result) {
        try {
            Map<String, Object> resultMap = asMap(result);
            if (resultMap == null) {
                return null;
            }
            List<Object> messages = asList(resultMap.get("messages"));
            StringBuilder content = new StringBuilder();
            for (Object messageObj : messages) {
                Map<String, Object> messageMap = asMap(messageObj);
                if (messageMap == null) {
                    continue;
                }
                appendPromptMessageText(content, messageMap.get("content"));
            }
            return McpPromptResult.builder()
                    .name(name)
                    .description(stringValue(resultMap.get("description")))
                    .content(content.toString())
                    .build();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static McpResourceContent parseSingleResourceContent(Object contentObj) {
        Map<String, Object> contentMap = asMap(contentObj);
        if (contentMap == null) {
            return null;
        }
        Object contents = extractResourceContent(contentMap);
        return McpResourceContent.builder()
                .uri(stringValue(contentMap.get("uri")))
                .mimeType(stringValue(contentMap.get("mimeType")))
                .contents(contents == null ? contentMap : contents)
                .build();
    }

    private static Object extractResourceContent(Map<String, Object> contentMap) {
        if (contentMap == null) {
            return null;
        }
        if (contentMap.containsKey("text")) {
            return contentMap.get("text");
        }
        if (contentMap.containsKey("blob")) {
            return contentMap.get("blob");
        }
        if (contentMap.containsKey("contents")) {
            return contentMap.get("contents");
        }
        return null;
    }

    private static void appendPromptMessageText(StringBuilder builder, Object content) {
        if (content == null) {
            return;
        }
        if (content instanceof String) {
            builder.append(content);
            return;
        }
        Map<String, Object> contentMap = asMap(content);
        if (contentMap != null) {
            Object text = contentMap.get("text");
            if (text != null) {
                builder.append(text);
                return;
            }
        }
        List<Object> parts = asList(content);
        for (Object partObj : parts) {
            Map<String, Object> partMap = asMap(partObj);
            if (partMap != null && partMap.get("text") != null) {
                builder.append(partMap.get("text"));
            }
        }
    }

    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> result = new HashMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static List<Object> asList(Object value) {
        if (!(value instanceof List<?>)) {
            return new ArrayList<Object>();
        }
        return new ArrayList<Object>((List<?>) value);
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? null : text;
    }

    private static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
