package io.github.lnyocly.service;

import io.github.lnyocly.ai4j.config.AiPlatform;
import io.github.lnyocly.ai4j.config.JinaConfig;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.config.SunoConfig;
import io.github.lnyocly.ai4j.platform.jina.rerank.JinaRerankService;
import io.github.lnyocly.ai4j.platform.openai.chat.OpenAiChatService;
import io.github.lnyocly.ai4j.platform.openai.video.OpenAiVideoService;
import io.github.lnyocly.ai4j.platform.suno.music.SunoMusicService;
import io.github.lnyocly.ai4j.rag.Reranker;
import io.github.lnyocly.ai4j.rag.ingestion.IngestionPipeline;
import io.github.lnyocly.ai4j.service.AiConfig;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.service.factory.AiServiceRegistration;
import io.github.lnyocly.ai4j.service.factory.AiServiceRegistry;
import io.github.lnyocly.ai4j.service.factory.DefaultAiServiceRegistry;
import io.github.lnyocly.ai4j.service.factory.FreeAiService;
import io.github.lnyocly.ai4j.vector.store.VectorDeleteRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchResult;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import io.github.lnyocly.ai4j.vector.store.VectorStoreCapabilities;
import okhttp3.OkHttpClient;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class AiServiceRegistryTest {

    @Test
    public void shouldBuildRegistryFromConfiguredPlatforms() {
        Configuration configuration = new Configuration();
        configuration.setOkHttpClient(new OkHttpClient());

        AiPlatform aiPlatform = new AiPlatform();
        aiPlatform.setId("tenant-a-openai");
        aiPlatform.setPlatform("openai");
        aiPlatform.setApiHost("https://example-openai.local/");
        aiPlatform.setApiKey("sk-test");
        aiPlatform.setImageGenerationUrl("v1/images/generations");
        aiPlatform.setResponsesUrl("v1/responses");
        aiPlatform.setVideoUrl("v1/videos");

        AiConfig aiConfig = new AiConfig();
        aiConfig.setPlatforms(Collections.singletonList(aiPlatform));

        AiServiceRegistry registry = DefaultAiServiceRegistry.from(configuration, aiConfig);
        AiServiceRegistration registration = registry.get("tenant-a-openai");
        AiService aiService = registration.getAiService();

        Assert.assertTrue(registry.contains("tenant-a-openai"));
        Assert.assertEquals(PlatformType.OPENAI, registration.getPlatformType());
        Assert.assertTrue(registry.getChatService("tenant-a-openai") instanceof OpenAiChatService);
        Assert.assertTrue(registry.getVideoService("tenant-a-openai") instanceof OpenAiVideoService);
        Assert.assertNotNull(aiService);
        Assert.assertNotNull(aiService.getConfiguration());
        Assert.assertNotSame(configuration, aiService.getConfiguration());

        OpenAiConfig scopedOpenAiConfig = aiService.getConfiguration().getOpenAiConfig();
        Assert.assertEquals("https://example-openai.local/", scopedOpenAiConfig.getApiHost());
        Assert.assertEquals("sk-test", scopedOpenAiConfig.getApiKey());
        Assert.assertEquals("v1/images/generations", scopedOpenAiConfig.getImageGenerationUrl());
        Assert.assertEquals("v1/responses", scopedOpenAiConfig.getResponsesUrl());
        Assert.assertEquals("v1/videos", scopedOpenAiConfig.getVideoUrl());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void shouldKeepFreeAiServiceAsCompatibilityShell() {
        Configuration configuration = new Configuration();
        configuration.setOkHttpClient(new OkHttpClient());

        AiPlatform aiPlatform = new AiPlatform();
        aiPlatform.setId("tenant-a-openai");
        aiPlatform.setPlatform("openai");
        aiPlatform.setApiHost("https://example-openai.local/");
        aiPlatform.setApiKey("sk-test");

        AiConfig aiConfig = new AiConfig();
        aiConfig.setPlatforms(Collections.singletonList(aiPlatform));

        new FreeAiService(configuration, aiConfig);

        Assert.assertTrue(FreeAiService.contains("tenant-a-openai"));
        Assert.assertTrue(FreeAiService.getChatService("tenant-a-openai") instanceof OpenAiChatService);
        Assert.assertTrue(FreeAiService.getVideoService("tenant-a-openai") instanceof OpenAiVideoService);
        Assert.assertNull(FreeAiService.getChatService("missing"));
    }


    @Test
    @SuppressWarnings("deprecation")
    public void shouldExposeSunoMusicServiceFromRegistryAndCompatibilityShell() {
        Configuration configuration = new Configuration();
        configuration.setOkHttpClient(new OkHttpClient());

        AiPlatform aiPlatform = new AiPlatform();
        aiPlatform.setId("tenant-suno");
        aiPlatform.setPlatform("suno");
        aiPlatform.setApiHost("https://api.chatfire.cn/");
        aiPlatform.setApiKey("suno-key");
        aiPlatform.setMusicUrl("suno/submit/music");
        aiPlatform.setLyricsUrl("suno/submit/lyrics");
        aiPlatform.setFetchUrl("suno/fetch/{task_id}");

        AiConfig aiConfig = new AiConfig();
        aiConfig.setPlatforms(Collections.singletonList(aiPlatform));

        AiServiceRegistry registry = DefaultAiServiceRegistry.from(configuration, aiConfig);
        AiServiceRegistration registration = registry.get("tenant-suno");
        AiService aiService = registration.getAiService();

        Assert.assertEquals(PlatformType.SUNO, registration.getPlatformType());
        Assert.assertTrue(registry.getMusicService("tenant-suno") instanceof SunoMusicService);

        SunoConfig scopedSunoConfig = aiService.getConfiguration().getSunoConfig();
        Assert.assertEquals("https://api.chatfire.cn/", scopedSunoConfig.getApiHost());
        Assert.assertEquals("suno-key", scopedSunoConfig.getApiKey());
        Assert.assertEquals("suno/submit/music", scopedSunoConfig.getMusicUrl());
        Assert.assertEquals("suno/submit/lyrics", scopedSunoConfig.getLyricsUrl());
        Assert.assertEquals("suno/fetch/{task_id}", scopedSunoConfig.getFetchUrl());

        new FreeAiService(registry);
        Assert.assertTrue(FreeAiService.getMusicService("tenant-suno") instanceof SunoMusicService);
    }

    @Test
    public void shouldFailFastWhenPlatformIsUnsupported() {
        Configuration configuration = new Configuration();
        configuration.setOkHttpClient(new OkHttpClient());

        AiPlatform aiPlatform = new AiPlatform();
        aiPlatform.setId("tenant-a-unknown");
        aiPlatform.setPlatform("unknown-provider");

        AiConfig aiConfig = new AiConfig();
        aiConfig.setPlatforms(Collections.singletonList(aiPlatform));

        try {
            DefaultAiServiceRegistry.from(configuration, aiConfig);
            Assert.fail("Expected unsupported platform to fail fast");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Unsupported ai platform 'unknown-provider' for id 'tenant-a-unknown'", e.getMessage());
        }
    }

    @Test
    public void shouldExposeJinaCompatibleRerankServiceFromRegistry() {
        Configuration configuration = new Configuration();
        configuration.setOkHttpClient(new OkHttpClient());

        AiPlatform aiPlatform = new AiPlatform();
        aiPlatform.setId("tenant-a-rerank");
        aiPlatform.setPlatform("jina");
        aiPlatform.setApiHost("https://api.jina.ai/");
        aiPlatform.setApiKey("jina-key");
        aiPlatform.setRerankUrl("v1/rerank");

        AiConfig aiConfig = new AiConfig();
        aiConfig.setPlatforms(Collections.singletonList(aiPlatform));

        AiServiceRegistry registry = DefaultAiServiceRegistry.from(configuration, aiConfig);
        AiServiceRegistration registration = registry.get("tenant-a-rerank");

        Assert.assertEquals(PlatformType.JINA, registration.getPlatformType());
        Assert.assertTrue(registry.getRerankService("tenant-a-rerank") instanceof JinaRerankService);
        JinaConfig scopedJinaConfig = registration.getAiService().getConfiguration().getJinaConfig();
        Assert.assertEquals("https://api.jina.ai/", scopedJinaConfig.getApiHost());
        Assert.assertEquals("jina-key", scopedJinaConfig.getApiKey());
        Assert.assertEquals("v1/rerank", scopedJinaConfig.getRerankUrl());

        Reranker reranker = registry.getModelReranker(
                "tenant-a-rerank",
                "jina-reranker-v2-base-multilingual",
                5,
                "优先制度原文"
        );
        Assert.assertNotNull(reranker);
    }

    @Test
    public void shouldExposeIngestionPipelineFromRegistryAndCompatibilityShell() {
        Configuration configuration = new Configuration();
        configuration.setOkHttpClient(new OkHttpClient());

        AiPlatform aiPlatform = new AiPlatform();
        aiPlatform.setId("tenant-a-openai");
        aiPlatform.setPlatform("openai");
        aiPlatform.setApiHost("https://example-openai.local/");
        aiPlatform.setApiKey("sk-test");

        AiConfig aiConfig = new AiConfig();
        aiConfig.setPlatforms(Collections.singletonList(aiPlatform));

        AiServiceRegistry registry = DefaultAiServiceRegistry.from(configuration, aiConfig);
        VectorStore vectorStore = new NoopVectorStore();

        IngestionPipeline pipeline = registry.getIngestionPipeline("tenant-a-openai", vectorStore);
        Assert.assertNotNull(pipeline);

        new FreeAiService(registry);
        Assert.assertNotNull(FreeAiService.getIngestionPipeline("tenant-a-openai", vectorStore));
    }

    private static class NoopVectorStore implements VectorStore {
        @Override
        public int upsert(io.github.lnyocly.ai4j.vector.store.VectorUpsertRequest request) {
            return 0;
        }

        @Override
        public java.util.List<VectorSearchResult> search(VectorSearchRequest request) {
            return Collections.emptyList();
        }

        @Override
        public boolean delete(VectorDeleteRequest request) {
            return false;
        }

        @Override
        public VectorStoreCapabilities capabilities() {
            return VectorStoreCapabilities.builder().dataset(true).metadataFilter(true).build();
        }
    }
}

