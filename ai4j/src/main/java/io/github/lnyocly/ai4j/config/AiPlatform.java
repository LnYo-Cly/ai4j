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
    private String imageGenerationUrl;
    private String responsesUrl;
    private String videoUrl;
    private String rerankApiHost;
    private String rerankUrl;
    private String musicUrl;
    private String lyricsUrl;
    private String fetchUrl;
}
