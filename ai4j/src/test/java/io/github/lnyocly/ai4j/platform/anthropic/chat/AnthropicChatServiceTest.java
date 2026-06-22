package io.github.lnyocly.ai4j.platform.anthropic.chat;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.config.AnthropicConfig;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletion;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicContentBlock;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicMessage;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicUsage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AnthropicChatServiceTest {

    private static AnthropicChatService newService() {
        Configuration configuration = new Configuration();
        configuration.setAnthropicConfig(new AnthropicConfig());
        return new AnthropicChatService(configuration);
    }

    private static ChatCompletion baseRequest() {
        return ChatCompletion.builder()
                .model("claude-test")
                .message(ChatMessage.withSystem("You are helpful."))
                .message(ChatMessage.withUser("hello"))
                .build();
    }

    // ---------- request mapping ----------

    @Test
    public void shouldExtractSystemAndMapMessagesToAnthropicRequest() {
        AnthropicChatService service = newService();
        AnthropicChatCompletion request = service.convertChatCompletionObject(baseRequest());

        Assert.assertEquals("claude-test", request.getModel());
        Assert.assertEquals("You are helpful.", request.getSystem());
        Assert.assertEquals(Integer.valueOf(4096), request.getMaxTokens());
        Assert.assertEquals(1, request.getMessages().size());
        Assert.assertEquals("user", request.getMessages().get(0).getRole());
        Assert.assertEquals("hello", request.getMessages().get(0).getContent());
    }

    @Test
    public void shouldRespectMaxCompletionTokens() {
        AnthropicChatService service = newService();
        ChatCompletion req = ChatCompletion.builder()
                .model("claude-test")
                .message(ChatMessage.withUser("hi"))
                .maxCompletionTokens(128)
                .build();
        AnthropicChatCompletion request = service.convertChatCompletionObject(req);
        Assert.assertEquals(Integer.valueOf(128), request.getMaxTokens());
    }

    @Test
    public void shouldMapAssistantToolCallsToToolUseBlocks() {
        AnthropicChatService service = newService();
        ToolCall toolCall = new ToolCall("toolu_1", "function", new ToolCall.Function("get_weather", "{\"city\":\"北京\"}"));
        ChatCompletion req = ChatCompletion.builder()
                .model("claude-test")
                .message(ChatMessage.withAssistant("thinking", Collections.singletonList(toolCall)))
                .build();
        AnthropicChatCompletion request = service.convertChatCompletionObject(req);

        Assert.assertEquals(1, request.getMessages().size());
        AnthropicMessage msg = request.getMessages().get(0);
        Assert.assertEquals("assistant", msg.getRole());
        @SuppressWarnings("unchecked")
        List<AnthropicContentBlock> blocks = (List<AnthropicContentBlock>) msg.getContent();
        AnthropicContentBlock toolUse = findBlock(blocks, "tool_use");
        Assert.assertNotNull("should contain a tool_use block", toolUse);
        Assert.assertEquals("toolu_1", toolUse.getId());
        Assert.assertEquals("get_weather", toolUse.getName());
        Assert.assertNotNull(toolUse.getInput());
    }

    @Test
    public void shouldMapToolResultMessageToToolResultBlock() {
        AnthropicChatService service = newService();
        ChatCompletion req = ChatCompletion.builder()
                .model("claude-test")
                .message(ChatMessage.withTool("sunny", "toolu_1"))
                .build();
        AnthropicChatCompletion request = service.convertChatCompletionObject(req);

        Assert.assertEquals(1, request.getMessages().size());
        AnthropicMessage msg = request.getMessages().get(0);
        Assert.assertEquals("user", msg.getRole());
        @SuppressWarnings("unchecked")
        List<AnthropicContentBlock> blocks = (List<AnthropicContentBlock>) msg.getContent();
        Assert.assertEquals("tool_result", blocks.get(0).getType());
        Assert.assertEquals("toolu_1", blocks.get(0).getToolUseId());
        Assert.assertEquals("sunny", blocks.get(0).getContent());
    }

    @Test
    public void shouldConvertOpenAiToolsToAnthropicInputSchema() {
        AnthropicChatService service = newService();
        Tool.Function function = new Tool.Function();
        function.setName("get_weather");
        function.setDescription("get weather");
        function.setParameters(new Tool.Function.Parameter());
        ChatCompletion req = ChatCompletion.builder()
                .model("claude-test")
                .message(ChatMessage.withUser("weather?"))
                .tools(Collections.singletonList(new Tool("function", function)))
                .build();
        AnthropicChatCompletion request = service.convertChatCompletionObject(req);

        Assert.assertNotNull(request.getTools());
        Assert.assertEquals(1, request.getTools().size());
        Assert.assertEquals("get_weather", request.getTools().get(0).getName());
        Assert.assertNotNull(request.getTools().get(0).getInputSchema());
    }

    @Test
    public void shouldPassthroughExtraBodyForThinking() {
        AnthropicChatService service = newService();
        ChatCompletion req = ChatCompletion.builder()
                .model("claude-test")
                .message(ChatMessage.withUser("hi"))
                .extraBody("thinking", (Object) Collections.singletonMap("type", "enabled"))
                .build();
        AnthropicChatCompletion request = service.convertChatCompletionObject(req);
        Assert.assertNotNull(request.getExtraBody());
        Assert.assertNotNull(request.getExtraBody().get("thinking"));
    }

    @Test
    public void shouldSerializeRequestWithSnakeCaseJsonFields() throws Exception {
        // 验证实际上线的 JSON 使用 Jackson @JsonProperty 的 snake_case
        AnthropicChatService service = newService();
        AnthropicChatCompletion request = service.convertChatCompletionObject(baseRequest());
        request.setMaxTokens(64);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = mapper.writeValueAsString(request);

        Assert.assertTrue("max_tokens must serialize snake_case", json.contains("\"max_tokens\":64"));
        Assert.assertTrue("messages present", json.contains("\"messages\""));
        Assert.assertTrue("system present", json.contains("\"system\":\"You are helpful.\""));
    }

    // ---------- response mapping ----------

    @Test
    public void shouldMapTextResponseToOpenAiFormat() {
        AnthropicChatService service = newService();
        AnthropicChatCompletionResponse response = new AnthropicChatCompletionResponse();
        response.setId("msg_1");
        response.setModel("claude-test");
        response.setStopReason("end_turn");
        AnthropicContentBlock textBlock = new AnthropicContentBlock();
        textBlock.setType("text");
        textBlock.setText("Hello!");
        response.setContent(Collections.singletonList(textBlock));
        response.setUsage(new AnthropicUsage(10, 5));

        ChatCompletionResponse result = service.convertChatCompletionResponse(response);
        Assert.assertEquals("msg_1", result.getId());
        Assert.assertEquals("chat.completion", result.getObject());
        Assert.assertEquals("claude-test", result.getModel());
        Assert.assertEquals(1, result.getChoices().size());
        Assert.assertEquals("stop", result.getChoices().get(0).getFinishReason());
        Assert.assertEquals("Hello!", result.getChoices().get(0).getMessage().getContent().getText());
        Assert.assertEquals(15L, result.getUsage().getTotalTokens());
    }

    @Test
    public void shouldMapToolUseResponseToToolCalls() {
        AnthropicChatService service = newService();
        AnthropicChatCompletionResponse response = new AnthropicChatCompletionResponse();
        response.setStopReason("tool_use");
        AnthropicContentBlock useBlock = new AnthropicContentBlock();
        useBlock.setType("tool_use");
        useBlock.setId("toolu_9");
        useBlock.setName("get_weather");
        useBlock.setInput(Collections.singletonMap("city", "北京"));
        response.setContent(Collections.singletonList(useBlock));

        ChatCompletionResponse result = service.convertChatCompletionResponse(response);
        Assert.assertEquals("tool_calls", result.getChoices().get(0).getFinishReason());
        List<ToolCall> toolCalls = result.getChoices().get(0).getMessage().getToolCalls();
        Assert.assertNotNull(toolCalls);
        Assert.assertEquals(1, toolCalls.size());
        Assert.assertEquals("toolu_9", toolCalls.get(0).getId());
        Assert.assertEquals("get_weather", toolCalls.get(0).getFunction().getName());
        Assert.assertTrue(toolCalls.get(0).getFunction().getArguments().contains("北京"));
    }

    @Test
    public void shouldMapMaxTokensStopReasonToLength() {
        AnthropicChatService service = newService();
        AnthropicChatCompletionResponse response = new AnthropicChatCompletionResponse();
        response.setStopReason("max_tokens");
        response.setContent(new ArrayList<AnthropicContentBlock>());

        ChatCompletionResponse result = service.convertChatCompletionResponse(response);
        Assert.assertEquals("length", result.getChoices().get(0).getFinishReason());
    }

    // ---------- stream translation ----------

    @Test
    public void shouldTranslateAnthropicStreamToOpenAiChunks() {
        AnthropicChatService service = newService();
        CapturingListener capture = new CapturingListener();
        EventSourceListener translator = service.convertEventSource(capture);

        feed(translator, "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_s\",\"model\":\"claude-test\"}}");
        feed(translator, "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}");
        feed(translator, "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}");
        feed(translator, "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"!\"}}");
        feed(translator, "{\"type\":\"content_block_stop\",\"index\":0}");
        feed(translator, "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":3}}");
        feed(translator, "{\"type\":\"message_stop\"}");

        Assert.assertTrue("expected at least 4 forwarded chunks, got " + capture.forwarded.size(),
                capture.forwarded.size() >= 4);
        Assert.assertEquals("[DONE]", capture.forwarded.get(capture.forwarded.size() - 1));

        Map<String, Object> firstTextChunk = JSON.parseObject(capture.forwarded.get(0), Map.class);
        Assert.assertEquals("chat.completion.chunk", firstTextChunk.get("object"));
        Assert.assertEquals("claude-test", firstTextChunk.get("model"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) firstTextChunk.get("choices");
        @SuppressWarnings("unchecked")
        Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
        Assert.assertEquals("Hi", delta.get("content"));

        Map<String, Object> finishChunk = JSON.parseObject(
                capture.forwarded.get(capture.forwarded.size() - 2), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> finishChoices = (List<Map<String, Object>>) finishChunk.get("choices");
        Assert.assertEquals("stop", finishChoices.get(0).get("finish_reason"));
    }

    @Test
    public void shouldTranslateToolUseStreamToToolCallChunk() {
        AnthropicChatService service = newService();
        CapturingListener capture = new CapturingListener();
        EventSourceListener translator = service.convertEventSource(capture);

        feed(translator, "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_t\",\"model\":\"claude-test\"}}");
        feed(translator, "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"get_weather\"}}");
        feed(translator, "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"city\\\":\\\"北京\\\"}\"}}");
        feed(translator, "{\"type\":\"content_block_stop\",\"index\":0}");
        feed(translator, "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":5}}");
        feed(translator, "{\"type\":\"message_stop\"}");

        String toolChunkJson = capture.forwarded.get(0);
        Map<String, Object> toolChunk = JSON.parseObject(toolChunkJson, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) toolChunk.get("choices");
        @SuppressWarnings("unchecked")
        Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) delta.get("tool_calls");
        Assert.assertNotNull("tool chunk must carry tool_calls", toolCalls);
        Assert.assertEquals("toolu_1", toolCalls.get(0).get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) toolCalls.get(0).get("function");
        Assert.assertEquals("get_weather", function.get("name"));
        Assert.assertTrue(((String) function.get("arguments")).contains("北京"));
    }

    // ---------- registration ----------

    @Test
    public void shouldBeRegisteredUnderAnthropicPlatform() {
        Configuration configuration = new Configuration();
        configuration.setAnthropicConfig(new AnthropicConfig());
        Object chatService = new AiService(configuration).getChatService(PlatformType.ANTHROPIC);
        Assert.assertTrue(chatService instanceof AnthropicChatService);
    }

    // ---------- helpers ----------

    private static AnthropicContentBlock findBlock(List<AnthropicContentBlock> blocks, String type) {
        for (AnthropicContentBlock block : blocks) {
            if (type.equals(block.getType())) {
                return block;
            }
        }
        return null;
    }

    private static void feed(EventSourceListener translator, String data) {
        translator.onEvent(null, null, null, data);
    }

    private static class CapturingListener extends SseListener {
        final List<String> forwarded = new ArrayList<String>();

        @Override
        protected void send() {
        }

        @Override
        public void onEvent(EventSource eventSource, String id, String type, String data) {
            forwarded.add(data);
        }
    }
}
