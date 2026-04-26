package io.github.lnyocly.ai4j.service.factory;

import io.github.lnyocly.ai4j.rag.RagService;
import io.github.lnyocly.ai4j.rag.Reranker;
import io.github.lnyocly.ai4j.rag.ingestion.IngestionPipeline;
import io.github.lnyocly.ai4j.service.AiConfig;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IAudioService;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.IEmbeddingService;
import io.github.lnyocly.ai4j.service.IImageService;
import io.github.lnyocly.ai4j.service.IRerankService;
import io.github.lnyocly.ai4j.service.IRealtimeService;
import io.github.lnyocly.ai4j.service.IResponsesService;
import io.github.lnyocly.ai4j.vector.store.VectorStore;

import java.util.Collections;
import java.util.Set;

/**
 * 兼容旧版本的多实例聊天入口。
 *
 * <p>主线入口仍然是 {@link AiService}。如果需要正式的多实例管理，请使用
 * {@link AiServiceRegistry}。本类保留旧构造方式和静态获取方式，仅作为兼容壳。</p>
 */
@Deprecated
public class FreeAiService {
    private static volatile AiServiceRegistry registry = DefaultAiServiceRegistry.empty();

    private final Configuration configuration;
    private final AiConfig aiConfig;
    private final AiServiceFactory aiServiceFactory;

    public FreeAiService(Configuration configuration, AiConfig aiConfig) {
        this(configuration, aiConfig, new DefaultAiServiceFactory());
    }

    public FreeAiService(Configuration configuration, AiConfig aiConfig, AiServiceFactory aiServiceFactory) {
        this.configuration = configuration;
        this.aiConfig = aiConfig;
        this.aiServiceFactory = aiServiceFactory;
        init();
    }

    public FreeAiService(AiServiceRegistry registry) {
        this.configuration = null;
        this.aiConfig = null;
        this.aiServiceFactory = null;
        setRegistry(registry);
    }

    public void init() {
        if (configuration == null) {
            return;
        }


        setRegistry(DefaultAiServiceRegistry.from(configuration, aiConfig, aiServiceFactory));
    }

    public static IChatService getChatService(String id) {
        AiServiceRegistration registration = registry.find(id);
        return registration == null ? null : registry.getChatService(id);
    }

    public static AiService getAiService(String id) {
        AiServiceRegistration registration = registry.find(id);
        return registration == null ? null : registration.getAiService();
    }

    public static IEmbeddingService getEmbeddingService(String id) {
        AiServiceRegistration registration = registry.find(id);
        return registration == null ? null : registry.getEmbeddingService(id);
    }

    public static IAudioService getAudioService(String id) {
        AiServiceRegistration registration = registry.find(id);
        return registration == null ? null : registry.getAudioService(id);
    }

    public static IRealtimeService getRealtimeService(String id) {
        AiServiceRegistration registration = registry.find(id);
        return registration == null ? null : registry.getRealtimeService(id);
    }

    public static IImageService getImageService(String id) {
        AiServiceRegistration registration = registry.find(id);
        return registration == null ? null : registry.getImageService(id);
    }

    public static IResponsesService getResponsesService(String id) {
        AiServiceRegistration registration = registry.find(id);
        return registration == null ? null : registry.getResponsesService(id);
    }

    public static IRerankService getRerankService(String id) {
        AiServiceRegistration registration = registry.find(id);
        return registration == null ? null : registry.getRerankService(id);
    }

    public static RagService getRagService(String id, VectorStore vectorStore) {
        AiServiceRegistration registration = registry.find(id);
        return registration == null ? null : registry.getRagService(id, vectorStore);
    }

    public static IngestionPipeline getIngestionPipeline(String id, VectorStore vectorStore) {
        AiServiceRegistration registration = registry.find(id);
        return registration == null ? null : registry.getIngestionPipeline(id, vectorStore);
    }

    public static IngestionPipeline getPineconeIngestionPipeline(String id) {
        AiServiceRegistration registration = registry.find(id);
        return registration == null ? null : registry.getPineconeIngestionPipeline(id);
    }

    public static Reranker getModelReranker(String id, String model) {
        AiServiceRegistration registration = registry.find(id);
        return registration == null ? null : registry.getModelReranker(id, model);
    }

    public static Reranker getModelReranker(String id,
                                            String model,
                                            Integer topN,
                                            String instruction) {
        AiServiceRegistration registration = registry.find(id);
        return registration == null ? null : registry.getModelReranker(id, model, topN, instruction);
    }

    public static Reranker getModelReranker(String id,
                                            String model,
                                            Integer topN,
                                            String instruction,
                                            boolean returnDocuments,
                                            boolean appendRemainingHits) {
        AiServiceRegistration registration = registry.find(id);
        return registration == null
                ? null
                : registry.getModelReranker(id, model, topN, instruction, returnDocuments, appendRemainingHits);
    }

    public static boolean contains(String id) {
        return registry.contains(id);
    }

    public static Set<String> ids() {
        return registry.ids();
    }

    public static AiServiceRegistry getRegistry() {
        return registry;
    }

    private static void setRegistry(AiServiceRegistry registry) {
        FreeAiService.registry = registry == null ? DefaultAiServiceRegistry.empty() : registry;
    }
}

