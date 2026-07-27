package io.github.lnyocly.ai4j.service;

import io.github.lnyocly.ai4j.config.*;
import io.github.lnyocly.ai4j.websearch.searxng.SearXNGConfig;
import lombok.Data;
import okhttp3.OkHttpClient;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSources;

import java.util.concurrent.TimeUnit;


/**
 * @Author cly
 * @Description 统一的配置管理
 * @Date 2024/8/8 23:44
 */

@Data
public class Configuration {

    private OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build();

    public EventSource.Factory createRequestFactory() {
        return EventSources.createFactory(okHttpClient);
    }

    private OpenAiConfig openAiConfig;
    private AnthropicConfig anthropicConfig;
    private ZhipuConfig zhipuConfig;
    private DeepSeekConfig deepSeekConfig;
    private MoonshotConfig moonshotConfig;
    private HunyuanConfig hunyuanConfig;
    private LingyiConfig lingyiConfig;
    private OllamaConfig ollamaConfig;
    private MinimaxConfig minimaxConfig;
    private BaichuanConfig baichuanConfig;

    private PineconeConfig pineconeConfig;

    private QdrantConfig qdrantConfig;

    private MilvusConfig milvusConfig;

    private PgVectorConfig pgVectorConfig;

    private RedisVectorConfig redisVectorConfig;

    private SearXNGConfig searXNGConfig;

    private McpConfig mcpConfig;

    private DashScopeConfig dashScopeConfig;

    private DoubaoConfig doubaoConfig;

    private JinaConfig jinaConfig;

    private SunoConfig sunoConfig;
}
