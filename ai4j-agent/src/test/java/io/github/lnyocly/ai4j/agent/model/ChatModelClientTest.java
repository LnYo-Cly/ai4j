package io.github.lnyocly.ai4j.agent.model;

import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
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

    private Tool testTool(String name) {
        Tool.Function function = new Tool.Function();
        function.setName(name);
        function.setDescription("test tool");
        return new Tool("function", function);
    }
}
