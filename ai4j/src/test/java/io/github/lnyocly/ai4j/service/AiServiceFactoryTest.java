package io.github.lnyocly.ai4j.service;

import io.github.lnyocly.ai4j.config.BaichuanConfig;
import io.github.lnyocly.ai4j.config.DashScopeConfig;
import io.github.lnyocly.ai4j.config.DoubaoConfig;
import io.github.lnyocly.ai4j.config.JinaConfig;
import io.github.lnyocly.ai4j.config.MinimaxConfig;
import io.github.lnyocly.ai4j.config.RedisVectorConfig;
import io.github.lnyocly.ai4j.config.ZhipuConfig;
import io.github.lnyocly.ai4j.service.factory.AiService;
import org.junit.Assert;
import org.junit.Test;

public class AiServiceFactoryTest {

    @Test
    public void shouldExposeRedisVectorStoreGetter() {
        Configuration configuration = new Configuration();
        configuration.setRedisVectorConfig(new RedisVectorConfig());
        AiService aiService = new AiService(configuration);

        Assert.assertNotNull(aiService.getRedisVectorStore());
    }

    @Test
    public void shouldExposeGenericStandardRerankService() {
        AiService aiService = new AiService(new Configuration());

        Assert.assertNotNull(aiService.getStandardRerankService(
                "https://api.example.com/", "test-key", "v1/rerank"));
    }

    @Test
    public void shouldWireOpenAiCompatibleEmbeddingServices() {
        Configuration configuration = new Configuration();
        configuration.setZhipuConfig(new ZhipuConfig());
        configuration.setDoubaoConfig(new DoubaoConfig());
        configuration.setDashScopeConfig(new DashScopeConfig());
        configuration.setJinaConfig(new JinaConfig());
        configuration.setBaichuanConfig(new BaichuanConfig());
        configuration.setMinimaxConfig(new MinimaxConfig());
        AiService aiService = new AiService(configuration);

        Assert.assertNotNull(aiService.getEmbeddingService(PlatformType.ZHIPU));
        Assert.assertNotNull(aiService.getEmbeddingService(PlatformType.DOUBAO));
        Assert.assertNotNull(aiService.getEmbeddingService(PlatformType.DASHSCOPE));
        Assert.assertNotNull(aiService.getEmbeddingService(PlatformType.JINA));
        Assert.assertNotNull(aiService.getEmbeddingService(PlatformType.BAICHUAN));
        Assert.assertNotNull(aiService.getEmbeddingService(PlatformType.MINIMAX));
    }

    @Test
    public void shouldRejectPlatformsWithoutEmbeddingSupport() {
        AiService aiService = new AiService(new Configuration());
        try {
            aiService.getEmbeddingService(PlatformType.DEEPSEEK);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("embedding"));
        }
    }
}
