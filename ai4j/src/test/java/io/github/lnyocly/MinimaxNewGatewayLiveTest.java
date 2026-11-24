package io.github.lnyocly;

import io.github.lnyocly.ai4j.config.MinimaxConfig;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import okhttp3.OkHttpClient;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Live: verifies {@code PlatformType.MINIMAX} ({@code MinimaxChatService}) uses the current
 * OpenAI-compatible gateway by default and works with modern models (MiniMax-M3) + coding-plan key.
 * <p>Override {@code MINIMAX_BASE_URL} to target another regional base.
 * <p>Env: {@code MINIMAX_API_KEY} (required).
 */
@Category(LiveProviderTest.class)
public class MinimaxNewGatewayLiveTest {

    @Test
    public void minimaxChatServiceWorksOnNewGatewayWithM3() throws Exception {
        String key = LiveProviderTestSupport.requireEnv("skip: MINIMAX_API_KEY not set", "MINIMAX_API_KEY");
        String baseUrl = System.getenv("MINIMAX_BASE_URL");
        MinimaxConfig config = new MinimaxConfig();
        config.setApiKey(key);
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            config.setApiHost(baseUrl);
        }
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        Configuration configuration = new Configuration();
        configuration.setMinimaxConfig(config);
        configuration.setOkHttpClient(client);

        IChatService chatService = new AiService(configuration).getChatService(PlatformType.MINIMAX);

        ChatCompletion req = ChatCompletion.builder()
                .model("MiniMax-M3")
                .message(ChatMessage.withUser("Say hi in one short sentence."))
                .maxCompletionTokens(128)
                .build();

        ChatCompletionResponse resp = chatService.chatCompletion(req);
        System.out.println("=== MinimaxChatService (new gateway) M3 ===");
        System.out.println(resp);

        assertNotNull(resp);
        assertNotNull(resp.getChoices());
        assertFalse("choices must not be empty", resp.getChoices().isEmpty());
        assertNotNull(resp.getChoices().get(0).getMessage());
        assertNotNull(resp.getChoices().get(0).getMessage().getContent());
        assertTrue("content must be non-empty",
                resp.getChoices().get(0).getMessage().getContent().getText() != null
                        && !resp.getChoices().get(0).getMessage().getContent().getText().isEmpty());
    }
}
