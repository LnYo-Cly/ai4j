package io.github.lnyocly.ai4j;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description OpenAI配置文件
 * @Date 2024/8/9 23:17
 */

@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "ai.openai")
public class OpenAiConfigProperties {
    private String apiHost = "https://api.openai.com/";
    private String apiKey = "";
    private String chatCompletionUrl = "v1/chat/completions";
    private String embeddingUrl = "v1/embeddings";
    private String speechUrl = "v1/audio/speech";
    private String transcriptionUrl = "v1/audio/transcriptions";
    private String translationUrl = "v1/audio/translations";
}
