package io.github.lnyocly;

import io.github.lnyocly.ai4j.config.AnthropicConfig;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletion;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicContentBlock;
import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicMessage;
import io.github.lnyocly.ai4j.platform.anthropic.stream.AnthropicStreamHandler;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IMessagesService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import okhttp3.OkHttpClient;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 原生 {@link IMessagesService} 路径 live 烟测：直接用 Anthropic Messages 格式（零 OpenAI 转换）
 * 调两家 coding-plan 的 Anthropic 兼容端点。
 * <ul>
 *   <li>智谱 GLM：{@code GLM_API_KEY}（必填，缺失则 skip）、{@code GLM_BASE_URL}（默认 {@code open.bigmodel.cn/api/anthropic/}）、{@code GLM_MODEL}（默认 glm-5.1）</li>
 *   <li>MiniMax：{@code MINIMAX_API_KEY}（必填，缺失则 skip）、{@code MINIMAX_BASE_URL}（默认 {@code api.minimaxi.com/anthropic/}）、{@code MINIMAX_MODEL}（默认 MiniMax-M3）</li>
 * </ul>
 */
@Category(LiveProviderTest.class)
public class AnthropicNativeLiveTest {

    private static IMessagesService build(String baseUrl, String apiKey) {
        AnthropicConfig config = new AnthropicConfig();
        config.setApiKey(apiKey);
        config.setApiHost(baseUrl);
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        Configuration configuration = new Configuration();
        configuration.setAnthropicConfig(config);
        configuration.setOkHttpClient(client);
        return new AiService(configuration).getMessagesService(PlatformType.ANTHROPIC);
    }

    private static AnthropicChatCompletion request(String model, String prompt) {
        AnthropicChatCompletion req = new AnthropicChatCompletion();
        req.setModel(model);
        req.setSystem("Reply in one short sentence.");
        AnthropicMessage user = new AnthropicMessage();
        user.setRole("user");
        user.setContent(prompt);
        req.setMessages(new ArrayList<AnthropicMessage>(Collections.singletonList(user)));
        req.setMaxTokens(128);
        return req;
    }

    private static String extractText(AnthropicChatCompletionResponse resp) {
        StringBuilder sb = new StringBuilder();
        if (resp != null && resp.getContent() != null) {
            for (AnthropicContentBlock block : resp.getContent()) {
                if (block != null && "text".equals(block.getType()) && block.getText() != null) {
                    sb.append(block.getText());
                }
            }
        }
        return sb.toString();
    }

    // ---------- GLM coding plan ----------

    @Test
    public void glm_native_messages() throws Exception {
        String key = LiveProviderTestSupport.requireEnv("skip: GLM_API_KEY not set", "GLM_API_KEY");
        String baseUrl = System.getenv("GLM_BASE_URL");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = "https://open.bigmodel.cn/api/anthropic/";
        }
        String model = System.getenv("GLM_MODEL");
        if (model == null || model.trim().isEmpty()) {
            model = "glm-5.1";
        }
        IMessagesService service = build(baseUrl, key);

        System.out.println("=== GLM native messages: " + model + " ===");
        AnthropicChatCompletionResponse resp = service.messages(request(model, "Introduce yourself in one sentence."));
        System.out.println(resp);
        assertTrue("glm native response must have text", extractText(resp).length() > 0);
        assertNotNull(resp.getId());
    }

    @Test
    public void glm_native_stream() throws Exception {
        String key = LiveProviderTestSupport.requireEnv("skip: GLM_API_KEY not set", "GLM_API_KEY");
        String baseUrl = System.getenv("GLM_BASE_URL");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = "https://open.bigmodel.cn/api/anthropic/";
        }
        String model = System.getenv("GLM_MODEL");
        if (model == null || model.trim().isEmpty()) {
            model = "glm-5.1";
        }
        IMessagesService service = build(baseUrl, key);

        final StringBuilder text = new StringBuilder();
        final StringBuilder stopReason = new StringBuilder();
        AnthropicStreamHandler handler = new AnthropicStreamHandler() {
            @Override
            public void onDeltaText(String t) {
                text.append(t);
                System.out.print(t);
            }

            @Override
            public void onStopReason(String reason, long in, long out) {
                stopReason.append(String.valueOf(reason));
            }
        };
        System.out.println("=== GLM native stream: " + model + " ===");
        service.messagesStream(request(model, "Say hi in one short sentence."), handler);
        System.out.println();
        assertTrue("glm stream output non-empty", text.length() > 0);
        assertEquals("end_turn", stopReason.toString());
    }

    // ---------- MiniMax coding plan ----------

    @Test
    public void minimax_native_messages() throws Exception {
        String key = LiveProviderTestSupport.requireEnv("skip: MINIMAX_API_KEY not set", "MINIMAX_API_KEY");
        String baseUrl = System.getenv("MINIMAX_BASE_URL");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = "https://api.minimaxi.com/anthropic/";
        }
        String model = System.getenv("MINIMAX_MODEL");
        if (model == null || model.trim().isEmpty()) {
            model = "MiniMax-M3";
        }
        IMessagesService service = build(baseUrl, key);

        System.out.println("=== MiniMax native messages: " + model + " ===");
        AnthropicChatCompletionResponse resp = service.messages(request(model, "Introduce yourself in one sentence."));
        System.out.println(resp);
        assertTrue("minimax native response must have text", extractText(resp).length() > 0);
        assertNotNull(resp.getId());
    }

    @Test
    public void minimax_native_stream() throws Exception {
        String key = LiveProviderTestSupport.requireEnv("skip: MINIMAX_API_KEY not set", "MINIMAX_API_KEY");
        String baseUrl = System.getenv("MINIMAX_BASE_URL");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = "https://api.minimaxi.com/anthropic/";
        }
        String model = System.getenv("MINIMAX_MODEL");
        if (model == null || model.trim().isEmpty()) {
            model = "MiniMax-M3";
        }
        IMessagesService service = build(baseUrl, key);

        final StringBuilder text = new StringBuilder();
        final StringBuilder stopReason = new StringBuilder();
        AnthropicStreamHandler handler = new AnthropicStreamHandler() {
            @Override
            public void onDeltaText(String t) {
                text.append(t);
                System.out.print(t);
            }

            @Override
            public void onStopReason(String reason, long in, long out) {
                stopReason.append(String.valueOf(reason));
            }
        };
        System.out.println("=== MiniMax native stream: " + model + " ===");
        service.messagesStream(request(model, "Say hi in one short sentence."), handler);
        System.out.println();
        assertTrue("minimax stream output non-empty", text.length() > 0);
        assertEquals("end_turn", stopReason.toString());
    }
}
