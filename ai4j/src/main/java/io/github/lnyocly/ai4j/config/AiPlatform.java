package io.github.lnyocly.ai4j.config;

import lombok.Data;

@Data
public class AiPlatform {
    private String id;
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