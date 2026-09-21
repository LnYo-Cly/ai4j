package io.github.lnyocly.ai4j;

import lombok.Data;

@Data
public class AiPlatformProperties {
    // 唯一标识，用于获取对应的服务
    private String id;
    // 平台类型，如：openai、zhipu、deepseek、moonshot、hunyuan、lingyi、ollama、minimax、baichuan、pinecone、searxng、suno
    private String platform;
    private String apiHost;
    private String apiKey;
    // 声明环境变量名，运行时由 System.getenv 解析为 apiKey；设置后优先于明文 apiKey
    private String apiKeyEnv;
    private String chatCompletionUrl;
    private String embeddingUrl;
    private String speechUrl;
    private String transcriptionUrl;
    private String translationUrl;
    private String realtimeUrl;
    private String imageGenerationUrl;
    private String responsesUrl;
    private String videoUrl;
    private String rerankApiHost;
    private String rerankUrl;
    private String musicUrl;
    private String lyricsUrl;
    private String fetchUrl;
    private String systemOneUrl;
}
