package io.github.lnyocly.ai4j.agent.model;

import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import io.github.lnyocly.ai4j.platform.openai.usage.UsageDetails;
import io.github.lnyocly.ai4j.service.IChatService;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

public class ChatModelClientTest {

    @Test
    public void createShouldEnableToolCallPassThroughForAgentTools() throws Exception {
        final AtomicReference<ChatCompletion> captured = new AtomicReference<ChatCompletion>();
        IChatService chatService = new IChatService() {
            @Override
            public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) {
                captured.set(chatCompletion);
                return new ChatCompletionResponse();
            }

            @Override
            public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) {
                captured.set(chatCompletion);
                return new ChatCompletionResponse();
            }

            @Override
            public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) {
            }

            @Override
            public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) {
            }
        };

        ChatModelClient client = new ChatModelClient(chatService);
        client.create(AgentPrompt.builder()
                .model("MiniMax-M2.1")
                .tools(Collections.<Object>singletonList(testTool("read_file")))
                .build());

        Assert.assertNotNull(captured.get());
        Assert.assertEquals(Boolean.TRUE, captured.get().getPassThroughToolCalls());
    }

    @Test
    public void createShouldKeepSystemAndInstructionsInOneStablePrefix() throws Exception {
        final AtomicReference<ChatCompletion> captured = new AtomicReference<ChatCompletion>();
        IChatService chatService = new IChatService() {
            @Override
            public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) {
                captured.set(chatCompletion);
                return new ChatCompletionResponse();
            }

            @Override
            public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) {
                return chatCompletion(null, null, chatCompletion);
            }

            @Override
            public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) {
            }

            @Override
            public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) {
            }
        };

        new ChatModelClient(chatService).create(AgentPrompt.builder()
                .model("gpt-5-mini")
                .systemPrompt("fixed system")
                .instructions("fixed developer rules")
                .build());

        Assert.assertEquals(1, captured.get().getMessages().size());
        Assert.assertEquals("fixed system\n\nfixed developer rules",
                captured.get().getMessages().get(0).getContent().getText());
    }

    @Test
    public void createAndStreamShouldExposeCachedUsage() throws Exception {
        final ChatCompletionResponse response = new ChatCompletionResponse();
        response.setUsage(openAiUsage());
        IChatService chatService = new IChatService() {
            @Override
            public ChatCompletionResponse chatCompletion(String baseUrl, String apiKey, ChatCompletion chatCompletion) {
                return response;
            }

            @Override
            public ChatCompletionResponse chatCompletion(ChatCompletion chatCompletion) {
                return response;
            }

            @Override
            public void chatCompletionStream(String baseUrl, String apiKey, ChatCompletion chatCompletion, SseListener eventSourceListener) {
                eventSourceListener.onEvent(null, null, null,
                        "{\"choices\":[],\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":20,\"total_tokens\":120,"
                                + "\"prompt_tokens_details\":{\"cached_tokens\":60,\"cache_write_tokens\":30},"
                                + "\"completion_tokens_details\":{\"reasoning_tokens\":7}}}");
            }

            @Override
            public void chatCompletionStream(ChatCompletion chatCompletion, SseListener eventSourceListener) {
                chatCompletionStream(null, null, chatCompletion, eventSourceListener);
            }
        };
        ChatModelClient client = new ChatModelClient(chatService);

        assertCachedUsage(client.create(AgentPrompt.builder().model("gpt-5-mini").build()));
        assertCachedUsage(client.createStream(AgentPrompt.builder().model("gpt-5-mini").build(), null));
    }

    private Usage openAiUsage() {
        Usage usage = new Usage(100L, 20L, 120L);
        UsageDetails promptDetails = new UsageDetails();
        promptDetails.setCachedTokens(Long.valueOf(60L));
        promptDetails.setCacheWriteTokens(Long.valueOf(30L));
        usage.setPromptTokensDetails(promptDetails);
        UsageDetails completionDetails = new UsageDetails();
        completionDetails.setReasoningTokens(Long.valueOf(7L));
        usage.setCompletionTokensDetails(completionDetails);
        return usage;
    }

    private void assertCachedUsage(AgentModelResult result) {
        Assert.assertEquals(Long.valueOf(100L), result.getInputTokens());
        Assert.assertEquals(Long.valueOf(40L), result.getUncachedInputTokens());
        Assert.assertEquals(Long.valueOf(60L), result.getCacheReadInputTokens());
        Assert.assertEquals(Long.valueOf(30L), result.getCacheWriteInputTokens());
        Assert.assertEquals(Long.valueOf(20L), result.getOutputTokens());
        Assert.assertEquals(Long.valueOf(120L), result.getTotalTokens());
        Assert.assertEquals(Long.valueOf(7L), result.getReasoningTokens());
    }

    private Tool testTool(String name) {
        Tool.Function function = new Tool.Function();
        function.setName(name);
        function.setDescription("test tool");
        return new Tool("function", function);
    }
}
