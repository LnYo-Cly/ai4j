package io.github.lnyocly.ai4j.config;

import lombok.Data;

@Data
public class AiPlatform {
    private String id;
    private String platform;
    private String apiHost;
    private String apiKey;
    /**
     * Names an environment variable that supplies the api key at runtime.
     * When set, it takes precedence over {@link #apiKey}: the resolved value is
     * read via {@code System.getenv} during service registration and the
     * plaintext field is ignored. Lets shared config avoid plaintext keys.
     */
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
}
