package io.github.lnyocly.ai4j.platform.anthropic.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.config.AnthropicConfig;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletion;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicContentBlock;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicMessage;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicTool;
import io.github.lnyocly.ai4j.service.Configuration;
import okhttp3.Request;
import okio.Buffer;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Anthropic tool-search / deferred-loading 透传支持测试：
 * {@code defer_loading}、server tool {@code type}、{@code anthropic-beta} header、
 * {@code tool_reference} 块的历史往返保留。
 */
public class AnthropicToolSearchPassthroughTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static AnthropicMessagesService newService(AnthropicConfig config) {
        Configuration configuration = new Configuration();
        configuration.setAnthropicConfig(config);
        return new AnthropicMessagesService(configuration);
    }

    private static String bodyJson(Request request) throws Exception {
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readUtf8();
    }

    private static AnthropicChatCompletion minimalRequest() {
        AnthropicChatCompletion request = new AnthropicChatCompletion();
        request.setModel("claude-sonnet-4-5");
        request.setMaxTokens(1024);
        AnthropicMessage message = new AnthropicMessage();
        message.setRole("user");
        message.setContent("hi");
        request.setMessages(Collections.singletonList(message));
        return request;
    }

    @Test
    public void shouldSerializeDeferLoadingAndServerToolType() throws Exception {
        AnthropicMessagesService service = newService(new AnthropicConfig());

        AnthropicChatCompletion request = minimalRequest();
        AnthropicTool searchTool = new AnthropicTool();
        searchTool.setType("tool_search_tool_bm25_20251119");
        searchTool.setName("tool_search_tool");
        AnthropicTool deferred = new AnthropicTool();
        deferred.setName("mcp__jira__create_issue");
        deferred.setDescription("Create a Jira issue");
        deferred.setDeferLoading(Boolean.TRUE);
        request.setTools(Arrays.asList(searchTool, deferred));

        JsonNode tools = MAPPER.readTree(bodyJson(service.buildRequest(
                "https://api.anthropic.com/", "k", request))).path("tools");

        Assert.assertEquals("tool_search_tool_bm25_20251119", tools.get(0).path("type").asText());
        Assert.assertEquals("tool_search_tool", tools.get(0).path("name").asText());
        Assert.assertTrue(tools.get(1).path("defer_loading").asBoolean());
        Assert.assertTrue(tools.get(0).path("defer_loading").isMissingNode());
    }

    @Test
    public void shouldEmitAnthropicBetaHeaderOnlyWhenConfigured() throws Exception {
        AnthropicConfig config = new AnthropicConfig();
        config.setBetaFeatures(Arrays.asList("advanced-tool-use-2025-11-20", "context-1m-2025-08-07"));
        AnthropicMessagesService service = newService(config);

        Request withBeta = service.buildRequest("https://api.anthropic.com/", "k", minimalRequest());
        Assert.assertEquals("advanced-tool-use-2025-11-20,context-1m-2025-08-07",
                withBeta.header("anthropic-beta"));

        AnthropicMessagesService noBeta = newService(new AnthropicConfig());
        Assert.assertNull(noBeta.buildRequest("https://api.anthropic.com/", "k", minimalRequest())
                .header("anthropic-beta"));
    }

    @Test
    public void shouldRoundTripToolReferenceBlock() throws Exception {
        String json = "{\"type\":\"tool_reference\",\"tool_name\":\"mcp__jira__create_issue\"}";
        AnthropicContentBlock block = MAPPER.readValue(json, AnthropicContentBlock.class);
        Assert.assertEquals("tool_reference", block.getType());
        Assert.assertEquals("mcp__jira__create_issue", block.getToolName());

        JsonNode out = MAPPER.readTree(MAPPER.writeValueAsString(block));
        Assert.assertEquals("tool_reference", out.path("type").asText());
        Assert.assertEquals("mcp__jira__create_issue", out.path("tool_name").asText());
    }
}
