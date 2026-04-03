package io.github.lnyocly.ai4j.service.factory;

import io.github.lnyocly.ai4j.rag.RagService;
import io.github.lnyocly.ai4j.service.IAudioService;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.IEmbeddingService;
import io.github.lnyocly.ai4j.service.IImageService;
import io.github.lnyocly.ai4j.service.IRerankService;
import io.github.lnyocly.ai4j.service.IRealtimeService;
import io.github.lnyocly.ai4j.service.IResponsesService;
import io.github.lnyocly.ai4j.rag.ingestion.IngestionPipeline;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import io.github.lnyocly.ai4j.rag.Reranker;

import java.util.Set;

/**
 * 按 id 管理多套 {@link AiService} 的正式抽象。
 */
public interface AiServiceRegistry {

    AiServiceRegistration find(String id);

    Set<String> ids();

    default boolean contains(String id) {
        return find(id) != null;
    }

    default AiServiceRegistration get(String id) {
        AiServiceRegistration registration = find(id);
        if (registration == null) {
            throw new IllegalArgumentException("Unknown ai service id: " + id);
        }
        return registration;
    }

    default AiService getAiService(String id) {
        return get(id).getAiService();
    }

    default IChatService getChatService(String id) {
        AiServiceRegistration registration = get(id);
        return registration.getAiService().getChatService(registration.getPlatformType());
    }

    default IEmbeddingService getEmbeddingService(String id) {
        AiServiceRegistration registration = get(id);
        return registration.getAiService().getEmbeddingService(registration.getPlatformType());
    }

    default IAudioService getAudioService(String id) {
        AiServiceRegistration registration = get(id);
        return registration.getAiService().getAudioService(registration.getPlatformType());
    }

    default IRealtimeService getRealtimeService(String id) {
        AiServiceRegistration registration = get(id);
        return registration.getAiService().getRealtimeService(registration.getPlatformType());
    }

    default IImageService getImageService(String id) {
        AiServiceRegistration registration = get(id);
        return registration.getAiService().getImageService(registration.getPlatformType());
    }

    default IResponsesService getResponsesService(String id) {
        AiServiceRegistration registration = get(id);
        return registration.getAiService().getResponsesService(registration.getPlatformType());
    }

    default IRerankService getRerankService(String id) {
        AiServiceRegistration registration = get(id);
        return registration.getAiService().getRerankService(registration.getPlatformType());
    }

    default RagService getRagService(String id, VectorStore vectorStore) {
        AiServiceRegistration registration = get(id);
        return registration.getAiService().getRagService(registration.getPlatformType(), vectorStore);
    }

    default RagService getPineconeRagService(String id) {
        AiServiceRegistration registration = get(id);
        return registration.getAiService().getPineconeRagService(registration.getPlatformType());
    }

    default IngestionPipeline getIngestionPipeline(String id, VectorStore vectorStore) {
        AiServiceRegistration registration = get(id);
        return registration.getAiService().getIngestionPipeline(registration.getPlatformType(), vectorStore);
    }

    default IngestionPipeline getPineconeIngestionPipeline(String id) {
        AiServiceRegistration registration = get(id);
        return registration.getAiService().getPineconeIngestionPipeline(registration.getPlatformType());
    }

    default Reranker getModelReranker(String id, String model) {
        AiServiceRegistration registration = get(id);
        return registration.getAiService().getModelReranker(registration.getPlatformType(), model);
    }

    default Reranker getModelReranker(String id,
                                      String model,
                                      Integer topN,
                                      String instruction) {
        AiServiceRegistration registration = get(id);
        return registration.getAiService().getModelReranker(
                registration.getPlatformType(),
                model,
                topN,
                instruction
        );
    }

    default Reranker getModelReranker(String id,
                                      String model,
                                      Integer topN,
                                      String instruction,
                                      boolean returnDocuments,
                                      boolean appendRemainingHits) {
        AiServiceRegistration registration = get(id);
        return registration.getAiService().getModelReranker(
                registration.getPlatformType(),
                model,
                topN,
                instruction,
                returnDocuments,
                appendRemainingHits
        );
    }
}

