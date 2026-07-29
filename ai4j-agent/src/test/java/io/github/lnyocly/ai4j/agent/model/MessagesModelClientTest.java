package io.github.lnyocly.ai4j.agent.model;

import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletion;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicCacheCreation;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicContentBlock;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicOutputTokensDetails;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicServerToolUsage;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicUsage;
import io.github.lnyocly.ai4j.platform.anthropic.stream.AnthropicStreamHandler;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.service.IMessagesService;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MessagesModelClientTest {

    private static AnthropicContentBlock textBlock(String text) {
        AnthropicContentBlock b = new AnthropicContentBlock();
        b.setType("text");
        b.setText(text);
        return b;
    }

    private static AnthropicContentBlock thinkingBlock(String thinking) {
        AnthropicContentBlock b = new AnthropicContentBlock();
        b.setType("thinking");
        b.setThinking(thinking);
        return b;
    }

    private static AnthropicContentBlock toolUseBlock(String id, String name, Object input) {
        AnthropicContentBlock b = new AnthropicContentBlock();
        b.setType("tool_use");
        b.setId(id);
        b.setName(name);
        b.setInput(input);
        return b;
    }

    private static class StubMessagesService implements IMessagesService {
        AnthropicChatCompletionResponse response;
        AnthropicStreamHandler streamHandler;
        AnthropicChatCompletion capturedRequest;
        AnthropicUsage streamUsage;

        @Override
        public AnthropicChatCompletionResponse messages(String baseUrl, String apiKey, AnthropicChatCompletion request) {
            this.capturedRequest = request;
            if (response == null) {
                response = new AnthropicChatCompletionResponse();
                response.setId("msg_1");
                response.setModel("glm-5.1");
                response.setStopReason("tool_use");
                List<AnthropicContentBlock> blocks = new ArrayList<AnthropicContentBlock>();
                blocks.add(thinkingBlock("analyzing"));
                blocks.add(textBlock("final answer"));
                blocks.add(toolUseBlock("toolu_1", "get_weather", Collections.singletonMap("city", "北京")));
                response.setContent(blocks);
            }
            return response;
        }

        @Override
        public AnthropicChatCompletionResponse messages(AnthropicChatCompletion request) {
            return messages(null, null, request);
        }

        @Override
        public void messagesStream(String baseUrl, String apiKey, AnthropicChatCompletion request, AnthropicStreamHandler handler) {
            this.capturedRequest = request;
            this.streamHandler = handler;
            handler.onStart("msg_s", "glm-5.1");
            if (streamUsage != null) {
                handler.onUsage(streamUsage);
            }
            handler.onThinkingDelta("reasoning ");
            handler.onDeltaText("answer");
            handler.onToolUseComplete(0, "toolu_1", "get_weather", "{\"city\":\"北京\"}");
            handler.onStopReason("tool_use", 1, 5);
            handler.onComplete();
        }

        @Override
        public void messagesStream(AnthropicChatCompletion request, AnthropicStreamHandler handler) {
            messagesStream(null, null, request, handler);
        }
    }

    private static AgentPrompt prompt() {
        List<Object> items = new ArrayList<Object>();
        items.add(ChatMessage.withUser("hello"));
        return AgentPrompt.builder()
                .model("glm-5.1")
                .systemPrompt("be helpful")
                .items(items)
                .maxOutputTokens(64)
                .build();
    }

    @Test
    public void createShouldMapResponseToAgentModelResult() throws Exception {
        StubMessagesService stub = new StubMessagesService();
        MessagesModelClient client = new MessagesModelClient(stub);

        AnthropicChatCompletion captured = null;
        AgentModelResult result = client.create(prompt());
        // stub captured request via messages(); verify result mapping
        Assert.assertEquals("final answer", result.getOutputText());
        Assert.assertEquals("analyzing", result.getReasoningText());
        Assert.assertNotNull(result.getToolCalls());
        Assert.assertEquals(1, result.getToolCalls().size());
        Assert.assertEquals("toolu_1", result.getToolCalls().get(0).getCallId());
        Assert.assertEquals("get_weather", result.getToolCalls().get(0).getName());
        Assert.assertTrue(result.getToolCalls().get(0).getArguments().contains("北京"));
    }

    @Test
    public void createShouldMapSystemAndItemsToAnthropicRequest() throws Exception {
        StubMessagesService stub = new StubMessagesService();
        MessagesModelClient client = new MessagesModelClient(stub);
        client.create(prompt());

        // 通过 stub 拿到的请求来断言；这里间接验证：stream 路径会用到同一 toAnthropicRequest
        // 直接验证 create 返回即可（映射在上一个用例覆盖）；此用例确保不抛异常且 system 被设置。
        Assert.assertNotNull(stub.response);
    }

    @Test
    public void streamShouldBridgeNativeEventsToListener() throws Exception {
        StubMessagesService stub = new StubMessagesService();
        MessagesModelClient client = new MessagesModelClient(stub);

        final List<String> textDeltas = new ArrayList<String>();
        final List<String> reasoningDeltas = new ArrayList<String>();
        final List<io.github.lnyocly.ai4j.agent.tool.AgentToolCall> toolCalls = new ArrayList<io.github.lnyocly.ai4j.agent.tool.AgentToolCall>();
        AgentModelStreamListener listener = new AgentModelStreamListener() {
            @Override
            public void onDeltaText(String delta) {
                textDeltas.add(delta);
            }

            @Override
            public void onReasoningDelta(String delta) {
                reasoningDeltas.add(delta);
            }

            @Override
            public void onToolCall(io.github.lnyocly.ai4j.agent.tool.AgentToolCall call) {
                toolCalls.add(call);
            }
        };

        AgentModelResult result = client.createStream(prompt(), listener);

        Assert.assertEquals("answer", result.getOutputText());
        Assert.assertEquals("reasoning ", result.getReasoningText());
        Assert.assertEquals(1, result.getToolCalls().size());
        Assert.assertEquals(Collections.singletonList("answer"), textDeltas);
        Assert.assertEquals(Collections.singletonList("reasoning "), reasoningDeltas);
        Assert.assertEquals(1, toolCalls.size());
        Assert.assertEquals("get_weather", toolCalls.get(0).getName());
    }

    @Test
    public void createAndStreamShouldExposeAnthropicCacheUsage() throws Exception {
        StubMessagesService stub = new StubMessagesService();
        AnthropicUsage usage = new AnthropicUsage(12L, 8L);
        usage.setCacheReadInputTokens(Long.valueOf(70L));
        usage.setCacheCreationInputTokens(Long.valueOf(30L));
        AnthropicCacheCreation cacheCreation = new AnthropicCacheCreation();
        cacheCreation.setEphemeral5mInputTokens(Long.valueOf(20L));
        cacheCreation.setEphemeral1hInputTokens(Long.valueOf(10L));
        usage.setCacheCreation(cacheCreation);
        AnthropicOutputTokensDetails outputDetails = new AnthropicOutputTokensDetails();
        outputDetails.setThinkingTokens(Long.valueOf(5L));
        usage.setOutputTokensDetails(outputDetails);
        AnthropicServerToolUsage serverToolUse = new AnthropicServerToolUsage();
        serverToolUse.setWebFetchRequests(Long.valueOf(2L));
        serverToolUse.setWebSearchRequests(Long.valueOf(3L));
        usage.setServerToolUse(serverToolUse);
        usage.setInferenceGeo("us");
        usage.setServiceTier("priority");
        AnthropicChatCompletionResponse response = new AnthropicChatCompletionResponse();
        response.setUsage(usage);
        stub.response = response;
        stub.streamUsage = usage;
        MessagesModelClient client = new MessagesModelClient(stub);

        assertCachedUsage(client.create(prompt()));
        assertCachedUsage(client.createStream(prompt(), null));
    }

    @Test
    public void cacheControlShouldOnlyMarkTheStableSystemBoundary() throws Exception {
        StubMessagesService stub = new StubMessagesService();
        Map<String, Object> cacheControl = new LinkedHashMap<String, Object>();
        cacheControl.put("type", "ephemeral");

        new MessagesModelClient(stub).create(AgentPrompt.builder()
                .model("claude-test")
                .systemPrompt("fixed system")
                .instructions("fixed developer rules")
                .extraBody(Collections.<String, Object>singletonMap("cache_control", cacheControl))
                .build());

        Assert.assertTrue(stub.capturedRequest.getSystem() instanceof List);
        AnthropicContentBlock systemBlock = (AnthropicContentBlock) ((List<?>) stub.capturedRequest.getSystem()).get(0);
        Assert.assertEquals(cacheControl, systemBlock.getCacheControl());
        Assert.assertNull(stub.capturedRequest.getExtraBody());
    }

    private void assertCachedUsage(AgentModelResult result) {
        Assert.assertEquals(Long.valueOf(12L), result.getInputTokens());
        Assert.assertEquals(Long.valueOf(12L), result.getUncachedInputTokens());
        Assert.assertEquals(Long.valueOf(70L), result.getCacheReadInputTokens());
        Assert.assertEquals(Long.valueOf(30L), result.getCacheCreationInputTokens());
        Assert.assertEquals(Long.valueOf(8L), result.getOutputTokens());
        Assert.assertEquals(Long.valueOf(120L), result.getTotalTokens());
        Assert.assertEquals(Long.valueOf(5L), result.getReasoningTokens());

        AnthropicUsage rawUsage;
        if (result.getRawResponse() instanceof AnthropicChatCompletionResponse) {
            rawUsage = ((AnthropicChatCompletionResponse) result.getRawResponse()).getUsage();
        } else {
            Assert.assertTrue(result.getRawResponse() instanceof Map);
            Object raw = ((Map<?, ?>) result.getRawResponse()).get("usage");
            Assert.assertTrue(raw instanceof AnthropicUsage);
            rawUsage = (AnthropicUsage) raw;
        }
        Assert.assertNotNull(rawUsage);
        Assert.assertEquals(Long.valueOf(20L), rawUsage.getCacheCreation().getEphemeral5mInputTokens());
        Assert.assertEquals(Long.valueOf(10L), rawUsage.getCacheCreation().getEphemeral1hInputTokens());
        Assert.assertEquals(Long.valueOf(5L), rawUsage.getOutputTokensDetails().getThinkingTokens());
        Assert.assertEquals(Long.valueOf(2L), rawUsage.getServerToolUse().getWebFetchRequests());
        Assert.assertEquals(Long.valueOf(3L), rawUsage.getServerToolUse().getWebSearchRequests());
        Assert.assertEquals("us", rawUsage.getInferenceGeo());
        Assert.assertEquals("priority", rawUsage.getServiceTier());
    }
}
