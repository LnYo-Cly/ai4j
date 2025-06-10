package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description Ollama配置文件
 * @Date 2024/9/20 23:01
 */
@Data
@ConfigurationProperties(prefix = "ai.ollama")
public class OllamaConfigProperties {
    private String apiHost = "http://localhost:11434/";
    private String apiKey = "";
    private String chatCompletionUrl = "api/chat";
    private String embeddingUrl = "api/embed";
}
