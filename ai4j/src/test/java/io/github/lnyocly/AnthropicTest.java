package io.github.lnyocly;

import io.github.lnyocly.ai4j.config.AnthropicConfig;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Anthropic Messages 适配器 live 测试。
 * <p>
 * 通过环境变量配置：
 * <ul>
 *   <li>{@code ANTHROPIC_API_KEY} —— 必填，缺失则 skip（见 {@link LiveProviderTestSupport#requireEnv}）</li>
 *   <li>{@code ANTHROPIC_BASE_URL} —— 可选，指向合作厂家的 Anthropic 兼容入口
 *       （如智谱 Coding Plan {@code https://open.bigmodel.cn/api/anthropic/}）</li>
 *   <li>{@code ANTHROPIC_MODEL} —— 可选，默认 {@code claude-sonnet-4-6}</li>
 * </ul>
 */
@Slf4j
@Category(LiveProviderTest.class)
public class AnthropicTest {

    private IChatService chatService;
    private String model;

    @Before
    public void init() {
        String apiKey = LiveProviderTestSupport.requireEnv(
                "Skip because ANTHROPIC_API_KEY is not configured", "ANTHROPIC_API_KEY");

        AnthropicConfig config = new AnthropicConfig();
        config.setApiKey(apiKey);
        String baseUrl = System.getenv("ANTHROPIC_BASE_URL");
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            config.setApiHost(baseUrl);
        }
        this.model = System.getenv("ANTHROPIC_MODEL");
        if (model == null || model.trim().isEmpty()) {
            this.model = "claude-sonnet-4-6";
        }

        Configuration configuration = new Configuration();
        configuration.setAnthropicConfig(config);
        chatService = new AiService(configuration).getChatService(PlatformType.ANTHROPIC);
    }

    @Test
    public void test_chatCompletion_common() throws Exception {
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model(model)
                .message(ChatMessage.withSystem("You are a helpful assistant. Reply in one short sentence."))
                .message(ChatMessage.withUser("Introduce yourself."))
                .maxCompletionTokens(128)
                .build();

        System.out.println("=== ANTHROPIC chatCompletion: " + model + " ===");
        ChatCompletionResponse response = chatService.chatCompletion(chatCompletion);
        System.out.println(response);

        assertNotNull(response);
        assertNotNull(response.getChoices());
        assertFalse("choices must not be empty", response.getChoices().isEmpty());
        assertNotNull(response.getChoices().get(0).getMessage());
        assertNotNull(response.getChoices().get(0).getMessage().getContent());
        assertTrue("response content must be non-empty",
                response.getChoices().get(0).getMessage().getContent().getText() != null
                        && !response.getChoices().get(0).getMessage().getContent().getText().isEmpty());
    }

    @Test
    public void test_chatCompletion_stream() throws Exception {
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model(model)
                .message(ChatMessage.withUser("Say hello in one short sentence."))
                .maxCompletionTokens(128)
                .stream(true)
                .build();

        SseListener sseListener = new SseListener() {
            @Override
            protected void send() {
                System.out.print(getCurrStr());
            }
        };

        System.out.println("=== ANTHROPIC stream: " + model + " ===");
        chatService.chatCompletionStream(chatCompletion, sseListener);
        System.out.println();
        System.out.println("usage: " + sseListener.getUsage());

        assertTrue("stream output must be non-empty", sseListener.getOutput().length() > 0);
        assertEquals("stream should finish with stop", "stop", sseListener.getFinishReason());
    }
}
