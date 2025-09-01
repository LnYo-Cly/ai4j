package io.github.lnyocly.ai4j;

import lombok.Data;

@Data
public class AiPlatformProperties {
    // 唯一标识，用于获取对应的服务
    private String id;
    // 平台类型，如：openai、zhipu、deepseek、moonshot、hunyuan、lingyi、ollama、minimax、baichuan、pinecone、searxng
    private String platform;
    private String apiHost;
    private String apiKey;
    private String chatCompletionUrl;
    private String embeddingUrl;
    private String speechUrl;
    private String transcriptionUrl;
    private String translationUrl;
    private String realtimeUrl;
}