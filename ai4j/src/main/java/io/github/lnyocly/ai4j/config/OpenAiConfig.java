package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description OpenAi骞冲彴閰嶇疆鏂囦欢淇℃伅
 * @Date 2024/8/8 0:18
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiConfig {
    private String apiHost = "https://api.openai.com/";
    private String apiKey = "";
    private String chatCompletionUrl = "v1/chat/completions";
    private String embeddingUrl = "v1/embeddings";
    private String speechUrl = "v1/audio/speech";
    private String transcriptionUrl = "v1/audio/transcriptions";
    private String translationUrl = "v1/audio/translations";
    private String realtimeUrl = "v1/realtime";
    private String imageGenerationUrl = "v1/images/generations";
    private String responsesUrl = "v1/responses";

}

