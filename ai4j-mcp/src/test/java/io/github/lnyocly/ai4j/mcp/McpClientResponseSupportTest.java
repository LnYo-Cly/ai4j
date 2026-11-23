package io.github.lnyocly.ai4j.mcp;

import io.github.lnyocly.ai4j.mcp.client.McpClientResponseSupport;
import io.github.lnyocly.ai4j.mcp.client.McpClient;
import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
import io.github.lnyocly.ai4j.mcp.entity.McpPrompt;
import io.github.lnyocly.ai4j.mcp.entity.McpPromptResult;
import io.github.lnyocly.ai4j.mcp.entity.McpRequest;
import io.github.lnyocly.ai4j.mcp.entity.McpResource;
import io.github.lnyocly.ai4j.mcp.entity.McpResourceContent;
import io.github.lnyocly.ai4j.mcp.entity.McpResponse;
import io.github.lnyocly.ai4j.mcp.entity.McpToolDefinition;
import io.github.lnyocly.ai4j.mcp.transport.McpTransport;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class McpClientResponseSupportTest {

    @Test
    public void shouldParseToolsListResponse() {
        Map<String, Object> inputSchema = new HashMap<String, Object>();
        inputSchema.put("type", "object");

        Map<String, Object> tool = new HashMap<String, Object>();
        tool.put("name", "queryWeather");
        tool.put("description", "Query current weather");
        tool.put("inputSchema", inputSchema);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("tools", Collections.singletonList(tool));

        List<McpToolDefinition> tools = McpClientResponseSupport.parseToolsListResponse(result);

        Assert.assertEquals(1, tools.size());
        Assert.assertEquals("queryWeather", tools.get(0).getName());
        Assert.assertEquals("Query current weather", tools.get(0).getDescription());
        Assert.assertEquals("object", tools.get(0).getInputSchema().get("type"));
    }

    @Test
    public void shouldParseToolCallTextResponse() {
        Map<String, Object> part1 = new HashMap<String, Object>();
        part1.put("type", "text");
        part1.put("text", "Hello ");

        Map<String, Object> part2 = new HashMap<String, Object>();
        part2.put("type", "text");
        part2.put("text", "World");

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("content", Arrays.asList(part1, part2));

        String text = McpClientResponseSupport.parseToolCallResponse(result);

        Assert.assertEquals("Hello World", text);
    }

    @Test
    public void shouldParseResourcesListResponse() {
        Map<String, Object> resource = new HashMap<String, Object>();
        resource.put("uri", "file://docs/readme.md");
        resource.put("name", "README");
        resource.put("description", "Project readme");
        resource.put("mimeType", "text/markdown");
        resource.put("size", 128L);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("resources", Collections.singletonList(resource));

        List<McpResource> resources = McpClientResponseSupport.parseResourcesListResponse(result);

        Assert.assertEquals(1, resources.size());
        Assert.assertEquals("file://docs/readme.md", resources.get(0).getUri());
        Assert.assertEquals("README", resources.get(0).getName());
        Assert.assertEquals("text/markdown", resources.get(0).getMimeType());
        Assert.assertEquals(Long.valueOf(128L), resources.get(0).getSize());
    }

    @Test
    public void shouldParseResourceReadResponse() {
        Map<String, Object> content = new HashMap<String, Object>();
        content.put("uri", "file://docs/readme.md");
        content.put("mimeType", "text/markdown");
        content.put("text", "# Hello");

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("contents", Collections.singletonList(content));

        McpResourceContent resourceContent = McpClientResponseSupport.parseResourceReadResponse(result);

        Assert.assertNotNull(resourceContent);
        Assert.assertEquals("file://docs/readme.md", resourceContent.getUri());
        Assert.assertEquals("text/markdown", resourceContent.getMimeType());
        Assert.assertEquals("# Hello", resourceContent.getContents());
    }

    @Test
    public void shouldParsePromptsListResponse() {
        Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.put("city", "string");

        Map<String, Object> prompt = new HashMap<String, Object>();
        prompt.put("name", "weather_prompt");
        prompt.put("description", "Build weather prompt");
        prompt.put("arguments", arguments);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("prompts", Collections.singletonList(prompt));

        List<McpPrompt> prompts = McpClientResponseSupport.parsePromptsListResponse(result);

        Assert.assertEquals(1, prompts.size());
        Assert.assertEquals("weather_prompt", prompts.get(0).getName());
        Assert.assertEquals("Build weather prompt", prompts.get(0).getDescription());
        Assert.assertEquals("string", prompts.get(0).getArguments().get("city"));
    }

    @Test
    public void shouldParsePromptGetResponse() {
        Map<String, Object> content = new HashMap<String, Object>();
        content.put("type", "text");
        content.put("text", "Weather for Luoyang");

        Map<String, Object> message = new HashMap<String, Object>();
        message.put("role", "user");
        message.put("content", content);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("description", "Weather prompt");
        result.put("messages", Collections.singletonList(message));

        McpPromptResult promptResult = McpClientResponseSupport.parsePromptGetResponse("weather_prompt", result);

        Assert.assertNotNull(promptResult);
        Assert.assertEquals("weather_prompt", promptResult.getName());
        Assert.assertEquals("Weather prompt", promptResult.getDescription());
        Assert.assertEquals("Weather for Luoyang", promptResult.getContent());
    }

    @Test
    public void shouldSupportResourceAndPromptApisThroughClient() {
        Map<String, Object> resourcesListResult = new HashMap<String, Object>();
        resourcesListResult.put("resources", Collections.singletonList(mapOf(
                "uri", "file://docs/readme.md",
                "name", "README",
                "description", "Project readme",
                "mimeType", "text/markdown",
                "size", 128L
        )));

        Map<String, Object> resourceReadResult = new HashMap<String, Object>();
        resourceReadResult.put("contents", Collections.singletonList(mapOf(
                "uri", "file://docs/readme.md",
                "mimeType", "text/markdown",
                "text", "# Hello"
        )));

        Map<String, Object> promptsListResult = new HashMap<String, Object>();
        promptsListResult.put("prompts", Collections.singletonList(mapOf(
                "name", "weather_prompt",
                "description", "Build weather prompt",
                "arguments", mapOf("city", "string")
        )));

        Map<String, Object> promptGetResult = new HashMap<String, Object>();
        promptGetResult.put("description", "Weather prompt");
        promptGetResult.put("messages", Collections.singletonList(mapOf(
                "role", "user",
                "content", mapOf("type", "text", "text", "Weather for Luoyang")
        )));

        FakeTransport transport = new FakeTransport()
                .respond("resources/list", resourcesListResult)
                .respond("resources/read", resourceReadResult)
                .respond("prompts/list", promptsListResult)
                .respond("prompts/get", promptGetResult);

        McpClient client = new McpClient("test", "1.0.0", transport, false);

        List<McpResource> resources = client.getAvailableResources().join();
        McpResourceContent resourceContent = client.readResource("file://docs/readme.md").join();
        List<McpPrompt> prompts = client.getAvailablePrompts().join();
        McpPromptResult promptResult = client.getPrompt("weather_prompt", Collections.singletonMap("city", "Luoyang")).join();

        Assert.assertEquals(1, resources.size());
        Assert.assertEquals("README", resources.get(0).getName());
        Assert.assertEquals("# Hello", resourceContent.getContents());
        Assert.assertEquals(1, prompts.size());
        Assert.assertEquals("weather_prompt", prompts.get(0).getName());
        Assert.assertEquals("Weather for Luoyang", promptResult.getContent());
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private static final class FakeTransport implements McpTransport {

        private final Map<String, Object> responses = new HashMap<String, Object>();
        private McpMessageHandler handler;

        private FakeTransport respond(String method, Object result) {
            responses.put(method, result);
            return this;
        }

        @Override
        public CompletableFuture<Void> start() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> sendMessage(McpMessage message) {
            if (message instanceof McpRequest && handler != null) {
                Object result = responses.get(message.getMethod());
                handler.handleMessage(new McpResponse(message.getId(), result));
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void setMessageHandler(McpMessageHandler handler) {
            this.handler = handler;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public boolean needsHeartbeat() {
            return false;
        }

        @Override
        public String getTransportType() {
            return "test";
        }
    }
}
