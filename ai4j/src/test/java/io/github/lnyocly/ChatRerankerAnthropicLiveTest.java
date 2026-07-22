package io.github.lnyocly;

import io.github.lnyocly.ai4j.config.AnthropicConfig;
import io.github.lnyocly.ai4j.rag.ChatReranker;
import io.github.lnyocly.ai4j.rag.RagHit;
import io.github.lnyocly.ai4j.rag.Reranker;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import okhttp3.OkHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Live smoke: ChatReranker via Anthropic-compatible IChatService (GLM coding-plan).
 *
 * <p>Env: {@code GLM_API_KEY} (required), {@code GLM_BASE_URL}
 * (default {@code https://open.bigmodel.cn/api/anthropic/}),
 * {@code GLM_MODEL} (default {@code glm-5.1}).</p>
 */
@Category(LiveProviderTest.class)
public class ChatRerankerAnthropicLiveTest {

    @Test
    public void glm_anthropic_chat_reranker_reorders_hits() throws Exception {
        String key = LiveProviderTestSupport.requireEnv("skip: GLM_API_KEY not set", "GLM_API_KEY");
        String baseUrl = System.getenv("GLM_BASE_URL");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = "https://open.bigmodel.cn/api/anthropic/";
        }
        String model = System.getenv("GLM_MODEL");
        if (model == null || model.trim().isEmpty()) {
            model = "glm-5.1";
        }

        AnthropicConfig config = new AnthropicConfig();
        config.setApiKey(key);
        config.setApiHost(baseUrl);

        Configuration configuration = new Configuration();
        configuration.setAnthropicConfig(config);
        configuration.setOkHttpClient(new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build());

        AiService aiService = new AiService(configuration);
        Reranker reranker = aiService.getChatReranker(PlatformType.ANTHROPIC, model, 2, null, true, 10);
        Assert.assertTrue(reranker instanceof ChatReranker);

        List<RagHit> hits = Arrays.asList(
                RagHit.builder().id("a").content("Banana bread recipe uses ripe bananas and flour.").retrievalScore(0.9f).build(),
                RagHit.builder().id("b").content("Company vacation policy allows 15 paid leave days per year.").retrievalScore(0.85f).build(),
                RagHit.builder().id("c").content("How to brew espresso with a manual lever machine.").retrievalScore(0.80f).build()
        );

        System.out.println("=== ChatReranker live: platform=ANTHROPIC model=" + model + " ===");
        List<RagHit> out = reranker.rerank("employee paid vacation leave policy", hits);
        Assert.assertNotNull(out);
        Assert.assertFalse(out.isEmpty());

        for (int i = 0; i < out.size(); i++) {
            RagHit h = out.get(i);
            System.out.println(i + ": id=" + h.getId()
                    + " rerankScore=" + h.getRerankScore()
                    + " score=" + h.getScore()
                    + " content=" + (h.getContent() == null ? "" : h.getContent()));
        }

        Assert.assertEquals(3, out.size());
        if (out.get(0).getRerankScore() != null) {
            Assert.assertEquals("b", out.get(0).getId());
            Assert.assertTrue(out.get(0).getRerankScore() > 0f);
        } else {
            System.out.println("WARN: no rerankScore — soft fallback to original order (still non-throwing)");
        }
    }
}
