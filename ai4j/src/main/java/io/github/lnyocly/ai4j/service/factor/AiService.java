package io.github.lnyocly.ai4j.service.factor;

import io.github.lnyocly.ai4j.platform.deepseek.chat.DeepSeekChatService;
import io.github.lnyocly.ai4j.platform.hunyuan.chat.HunyuanChatService;
import io.github.lnyocly.ai4j.platform.lingyi.chat.LingyiChatService;
import io.github.lnyocly.ai4j.platform.moonshot.chat.MoonshotChatService;
import io.github.lnyocly.ai4j.platform.ollama.chat.OllamaAiChatService;
import io.github.lnyocly.ai4j.platform.openai.chat.OpenAiChatService;
import io.github.lnyocly.ai4j.platform.zhipu.chat.ZhipuChatService;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.platform.openai.embedding.OpenAiEmbeddingService;
import io.github.lnyocly.ai4j.service.IEmbeddingService;
import io.github.lnyocly.ai4j.vector.service.PineconeService;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @Author cly
 * @Description AI服务工厂，创建各种AI应用
 * @Date 2024/8/7 18:10
 */
public class AiService {
   // private final ConcurrentMap<PlatformType, IChatService> chatServiceCache = new ConcurrentHashMap<>();
    //private final ConcurrentMap<PlatformType, IEmbeddingService> embeddingServiceCache = new ConcurrentHashMap<>();

    private final Configuration configuration;

    public AiService(Configuration configuration) {
        this.configuration = configuration;
    }

    public IChatService getChatService(PlatformType platform) {
        //return chatServiceCache.computeIfAbsent(platform, this::createChatService);
        return createChatService(platform);
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
            default:
                throw new IllegalArgumentException("Unknown platform: " + platform);
        }
    }

    public PineconeService getPineconeService() {
        return new PineconeService(configuration);
    }

}
