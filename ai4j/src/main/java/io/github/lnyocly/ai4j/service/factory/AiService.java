package io.github.lnyocly.ai4j.service.factory;

import io.github.lnyocly.ai4j.platform.baichuan.chat.BaichuanChatService;
import io.github.lnyocly.ai4j.platform.dashscope.DashScopeChatService;
import io.github.lnyocly.ai4j.platform.deepseek.chat.DeepSeekChatService;
import io.github.lnyocly.ai4j.platform.doubao.chat.DoubaoChatService;
import io.github.lnyocly.ai4j.platform.doubao.image.DoubaoImageService;
import io.github.lnyocly.ai4j.platform.doubao.rerank.DoubaoRerankService;
import io.github.lnyocly.ai4j.platform.hunyuan.chat.HunyuanChatService;
import io.github.lnyocly.ai4j.platform.jina.rerank.JinaRerankService;
import io.github.lnyocly.ai4j.platform.lingyi.chat.LingyiChatService;
import io.github.lnyocly.ai4j.platform.minimax.chat.MinimaxChatService;
import io.github.lnyocly.ai4j.platform.moonshot.chat.MoonshotChatService;
import io.github.lnyocly.ai4j.platform.ollama.chat.OllamaAiChatService;
import io.github.lnyocly.ai4j.platform.ollama.embedding.OllamaEmbeddingService;
import io.github.lnyocly.ai4j.platform.ollama.rerank.OllamaRerankService;
import io.github.lnyocly.ai4j.platform.openai.audio.OpenAiAudioService;
import io.github.lnyocly.ai4j.platform.openai.chat.OpenAiChatService;
import io.github.lnyocly.ai4j.platform.openai.embedding.OpenAiEmbeddingService;
import io.github.lnyocly.ai4j.platform.openai.image.OpenAiImageService;
import io.github.lnyocly.ai4j.platform.openai.realtime.OpenAiRealtimeService;
import io.github.lnyocly.ai4j.platform.zhipu.chat.ZhipuChatService;
import io.github.lnyocly.ai4j.rag.DefaultRagContextAssembler;
import io.github.lnyocly.ai4j.rag.DefaultRagService;
import io.github.lnyocly.ai4j.rag.DenseRetriever;
import io.github.lnyocly.ai4j.rag.ModelReranker;
import io.github.lnyocly.ai4j.rag.NoopReranker;
import io.github.lnyocly.ai4j.rag.RagService;
import io.github.lnyocly.ai4j.rag.Reranker;
import io.github.lnyocly.ai4j.rag.ingestion.IngestionPipeline;
import io.github.lnyocly.ai4j.service.*;
import io.github.lnyocly.ai4j.vector.service.PineconeService;
import io.github.lnyocly.ai4j.vector.store.milvus.MilvusVectorStore;
import io.github.lnyocly.ai4j.vector.store.pgvector.PgVectorStore;
import io.github.lnyocly.ai4j.vector.store.qdrant.QdrantVectorStore;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import io.github.lnyocly.ai4j.vector.store.pinecone.PineconeVectorStore;
import io.github.lnyocly.ai4j.websearch.ChatWithWebSearchEnhance;

/**
 * @Author cly
 * @Description AI鏈嶅姟宸ュ巶锛屽垱寤哄悇绉岮I搴旂敤
 * @Date 2024/8/7 18:10
 */
public class AiService {
   // private final ConcurrentMap<PlatformType, IChatService> chatServiceCache = new ConcurrentHashMap<>();
    //private final ConcurrentMap<PlatformType, IEmbeddingService> embeddingServiceCache = new ConcurrentHashMap<>();

    private final Configuration configuration;

    public AiService(Configuration configuration) {
        this.configuration = configuration;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public IChatService getChatService(PlatformType platform) {
        //return chatServiceCache.computeIfAbsent(platform, this::createChatService);
        return createChatService(platform);
    }

    public IChatService webSearchEnhance(IChatService chatService) {
        //IChatService chatService = getChatService(platform);
        return new ChatWithWebSearchEnhance(chatService, configuration);
    }

    private IChatService createChatService(PlatformType platform) {
        switch (platform) {
            case OPENAI:
                return new OpenAiChatService(configuration);
            case ZHIPU:
                return new ZhipuChatService(configuration);
            case DEEPSEEK:
                return new DeepSeekChatService(configuration);
            case MOONSHOT:
                return new MoonshotChatService(configuration);
            case HUNYUAN:
                return new HunyuanChatService(configuration);
            case LINGYI:
                return new LingyiChatService(configuration);
            case OLLAMA:
                return new OllamaAiChatService(configuration);
            case MINIMAX:
                return new MinimaxChatService(configuration);
            case BAICHUAN:
                return new BaichuanChatService(configuration);
            case DASHSCOPE:
                return new DashScopeChatService(configuration);
            case DOUBAO:
                return new DoubaoChatService(configuration);
            default:
                throw new IllegalArgumentException("Unknown platform: " + platform);
        }
    }



    public IEmbeddingService getEmbeddingService(PlatformType platform) {
        //return embeddingServiceCache.computeIfAbsent(platform, this::createEmbeddingService);
        return createEmbeddingService(platform);
    }

    private IEmbeddingService createEmbeddingService(PlatformType platform) {
        switch (platform) {
            case OPENAI:
                return new OpenAiEmbeddingService(configuration);
            case OLLAMA:
                return new OllamaEmbeddingService(configuration);
            default:
                throw new IllegalArgumentException("Unknown platform: " + platform);
        }
    }

    public IAudioService getAudioService(PlatformType platform) {
        return createAudioService(platform);
    }

    private IAudioService createAudioService(PlatformType platform) {
        switch (platform) {
            case OPENAI:
                return new OpenAiAudioService(configuration);
            default:
                throw new IllegalArgumentException("Unknown platform: " + platform);
        }
    }

    public IRealtimeService getRealtimeService(PlatformType platform) {
        return createRealtimeService(platform);
    }

    private IRealtimeService createRealtimeService(PlatformType platform) {
        switch (platform) {
            case OPENAI:
                return new OpenAiRealtimeService(configuration);
            default:
                throw new IllegalArgumentException("Unknown platform: " + platform);
        }
    }

    public PineconeService getPineconeService() {
        return new PineconeService(configuration);
    }

    public VectorStore getPineconeVectorStore() {
        return new PineconeVectorStore(getPineconeService());
    }

    public VectorStore getQdrantVectorStore() {
        return new QdrantVectorStore(configuration);
    }

    public VectorStore getMilvusVectorStore() {
        return new MilvusVectorStore(configuration);
    }

    public VectorStore getPgVectorStore() {
        return new PgVectorStore(configuration);
    }

    public IImageService getImageService(PlatformType platform) {
        return createImageService(platform);
    }

    private IImageService createImageService(PlatformType platform) {
        switch (platform) {
            case OPENAI:
                return new OpenAiImageService(configuration);
            case DOUBAO:
                return new DoubaoImageService(configuration);
            default:
                throw new IllegalArgumentException("Unknown platform: " + platform);
        }
    }


    public IResponsesService getResponsesService(PlatformType platform) {
        return createResponsesService(platform);
    }

    private IResponsesService createResponsesService(PlatformType platform) {
        switch (platform) {
            case OPENAI:
                return new io.github.lnyocly.ai4j.platform.openai.response.OpenAiResponsesService(configuration);
            case DOUBAO:
                return new io.github.lnyocly.ai4j.platform.doubao.response.DoubaoResponsesService(configuration);
            case DASHSCOPE:
                return new io.github.lnyocly.ai4j.platform.dashscope.response.DashScopeResponsesService(configuration);
            default:
                throw new IllegalArgumentException("Unknown platform: " + platform);
        }
    }

    public IRerankService getRerankService(PlatformType platform) {
        return createRerankService(platform);
    }

    private IRerankService createRerankService(PlatformType platform) {
        switch (platform) {
            case JINA:
                return new JinaRerankService(configuration);
            case OLLAMA:
                return new OllamaRerankService(configuration);
            case DOUBAO:
                return new DoubaoRerankService(configuration);
            default:
                throw new IllegalArgumentException("Unknown platform: " + platform);
        }
    }

    public RagService getRagService(PlatformType platform, VectorStore vectorStore) {
        return new DefaultRagService(
                new DenseRetriever(getEmbeddingService(platform), vectorStore),
                new NoopReranker(),
                new DefaultRagContextAssembler()
        );
    }

    public IngestionPipeline getIngestionPipeline(PlatformType platform, VectorStore vectorStore) {
        return new IngestionPipeline(getEmbeddingService(platform), vectorStore);
    }

    public Reranker getModelReranker(PlatformType platform, String model) {
        return new ModelReranker(getRerankService(platform), model);
    }

    public Reranker getModelReranker(PlatformType platform,
                                     String model,
                                     Integer topN,
                                     String instruction) {
        return getModelReranker(platform, model, topN, instruction, false, true);
    }

    public Reranker getModelReranker(PlatformType platform,
                                     String model,
                                     Integer topN,
                                     String instruction,
                                     boolean returnDocuments,
                                     boolean appendRemainingHits) {
        return new ModelReranker(
                getRerankService(platform),
                model,
                topN,
                instruction,
                returnDocuments,
                appendRemainingHits
        );
    }

    public RagService getPineconeRagService(PlatformType platform) {
        return getRagService(platform, getPineconeVectorStore());
    }

    public IngestionPipeline getPineconeIngestionPipeline(PlatformType platform) {
        return getIngestionPipeline(platform, getPineconeVectorStore());
    }
}


