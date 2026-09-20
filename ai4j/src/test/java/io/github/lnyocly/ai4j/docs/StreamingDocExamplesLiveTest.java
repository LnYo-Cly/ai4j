package io.github.lnyocly.ai4j.docs;

import io.github.lnyocly.ai4j.config.AnthropicConfig;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.listener.ResponseSseListener;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletion;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicMessage;
import io.github.lnyocly.ai4j.platform.anthropic.stream.AnthropicStreamHandler;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.IMessagesService;
import io.github.lnyocly.ai4j.service.IResponsesService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executable source of truth for the snippets embedded in
 * {@code docs/capabilities/models/streaming.md}.
 *
 * <p>Every code block on that page is copied from a method here, so a snippet
 * cannot silently rot: if the SDK API changes, this stops compiling, and if the
 * runtime behaviour changes, this stops passing.
 *
 * <p>Chat/Responses require {@code OPENAI_API_KEY}; honours
 * {@code OPENAI_API_HOST} and {@code OPENAI_CHAT_MODEL}. Messages requires
 * {@code ANTHROPIC_API_KEY}; honours {@code ANTHROPIC_BASE_URL} and
 * {@code ANTHROPIC_MODEL}. Each test skips independently when its key is absent.
 */
@Category(LiveProviderTest.class)
public class StreamingDocExamplesLiveTest {

    private IChatService chatService() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        Assume.assumeTrue("OPENAI_API_KEY not set", apiKey != null && !apiKey.trim().isEmpty());

        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiKey(apiKey);
        String apiHost = System.getenv("OPENAI_API_HOST");
        if (apiHost != null && !apiHost.trim().isEmpty()) {
            openAiConfig.setApiHost(apiHost);
        }

        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(openAiConfig);
        return new AiService(configuration).getChatService(PlatformType.OPENAI);
    }

    private IResponsesService responsesService() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        Assume.assumeTrue("OPENAI_API_KEY not set", apiKey != null && !apiKey.trim().isEmpty());

        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiKey(apiKey);
        String apiHost = System.getenv("OPENAI_API_HOST");
        if (apiHost != null && !apiHost.trim().isEmpty()) {
            openAiConfig.setApiHost(apiHost);
        }

        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(openAiConfig);
        return new AiService(configuration).getResponsesService(PlatformType.OPENAI);
    }

    private IMessagesService messagesService() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        Assume.assumeTrue("ANTHROPIC_API_KEY not set", apiKey != null && !apiKey.trim().isEmpty());

        AnthropicConfig anthropicConfig = new AnthropicConfig();
        anthropicConfig.setApiKey(apiKey);
        String baseUrl = System.getenv("ANTHROPIC_BASE_URL");
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            anthropicConfig.setApiHost(baseUrl);
        }

        Configuration configuration = new Configuration();
        configuration.setAnthropicConfig(anthropicConfig);
        return new AiService(configuration).getMessagesService(PlatformType.ANTHROPIC);
    }

    private String openAiModel() {
        String model = System.getenv("OPENAI_CHAT_MODEL");
        return (model == null || model.trim().isEmpty()) ? "gpt-4o-mini" : model;
    }

    private String anthropicModel() {
        String model = System.getenv("ANTHROPIC_MODEL");
        return (model == null || model.trim().isEmpty()) ? "claude-haiku-4-5-20251001" : model;
    }

    // ---- Chat 流式：SseListener 聚合 delta ----

    @Test
    public void chatStreamAggregatesDeltas() throws Exception {
        IChatService chatService = chatService();

        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model(openAiModel())
                .message(ChatMessage.withUser("从 1 数到 5，只输出数字"))
                .stream(Boolean.TRUE)
                .build();

        SseListener sseListener = new SseListener() {
            @Override
            protected void send() {
                // 每个 delta 到达时触发；getCurrStr() 是本次增量
                System.out.print(getCurrStr());
            }
        };

        chatService.chatCompletionStream(chatCompletion, sseListener);

        // 流结束后，聚合状态都在 listener 上
        String full = sseListener.getOutput().toString();
        System.out.println("\n完整输出: " + full);
        System.out.println("finishReason: " + sseListener.getFinishReason());
        System.out.println("usage: " + sseListener.getUsage());

        Assert.assertTrue("流式应产出内容", full.length() > 0);
        Assert.assertNotNull(sseListener.getFinishReason());
    }

    // ---- Responses 流式：事件驱动聚合 ----

    @Test
    public void responsesStreamAggregatesEvents() throws Exception {
        IResponsesService responsesService = responsesService();

        ResponseRequest request = ResponseRequest.builder()
                .model(openAiModel())
                .input("从 1 数到 5，只输出数字")
                .stream(Boolean.TRUE)
                .build();

        ResponseSseListener listener = new ResponseSseListener() {
            @Override
            protected void onEvent() {
                // getCurrText() 是本次事件带来的文本增量
                String delta = getCurrText();
                if (delta != null && !delta.isEmpty()) {
                    System.out.print(delta);
                }
            }
        };

        responsesService.createStream(request, listener);

        // 流结束后，聚合状态都在 listener 上
        System.out.println("\n完整文本: " + listener.getOutputText());
        System.out.println("事件条数: " + listener.getEvents().size());

        Assert.assertTrue("流式应产出文本", listener.getOutputText().length() > 0);
        Assert.assertFalse("应收到事件", listener.getEvents().isEmpty());
    }

    // ---- Messages 流式：类型化回调 ----

    @Test
    public void messagesStreamTypedCallbacks() throws Exception {
        IMessagesService messagesService = messagesService();

        AnthropicChatCompletion request = new AnthropicChatCompletion();
        request.setModel(anthropicModel());
        request.setMaxTokens(128);
        AnthropicMessage user = new AnthropicMessage();
        user.setRole("user");
        user.setContent("从 1 数到 5，只输出数字");
        request.setMessages(new ArrayList<AnthropicMessage>(Collections.singletonList(user)));

        final StringBuilder text = new StringBuilder();
        final AtomicBoolean completed = new AtomicBoolean(false);
        AnthropicStreamHandler handler = new AnthropicStreamHandler() {
            @Override
            public void onDeltaText(String delta) {
                // 正文文本增量，到达即打印
                System.out.print(delta);
                text.append(delta);
            }

            @Override
            public void onStopReason(String stopReason, long inputTokens, long outputTokens) {
                System.out.println("\nstopReason: " + stopReason);
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }
        };

        // messagesStream 内部阻塞到 message_stop 或失败才返回
        messagesService.messagesStream(request, handler);

        Assert.assertTrue("流式应产出文本", text.length() > 0);
        Assert.assertTrue("应收到 message_stop", completed.get());
    }
}
