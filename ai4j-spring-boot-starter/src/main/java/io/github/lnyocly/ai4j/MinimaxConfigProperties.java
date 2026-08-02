package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MiniMax OpenAI-compatible configuration properties.
 * <p>
 * Defaults to the global endpoint {@code https://api.minimax.io/}.
 * Override {@code ai.minimax.api-host} for another regional base such as
 * {@code https://api.minimaxi.com/}.
 */

@Data
@ConfigurationProperties(prefix = "ai.minimax")
public class MinimaxConfigProperties {
    private String apiHost = "https://api.minimax.io/";
    private String apiKey = "";
    private String chatCompletionUrl = "v1/chat/completions";
}
