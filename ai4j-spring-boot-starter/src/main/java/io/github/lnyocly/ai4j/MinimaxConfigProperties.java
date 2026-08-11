package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MiniMax OpenAI-compatible and Anthropic-compatible configuration properties.
 * <p>
 * Defaults to the global endpoints. Override {@code ai.minimax.api-host} and
 * {@code ai.minimax.anthropic-api-host} for the CN endpoints when needed.
 */

@Data
@ConfigurationProperties(prefix = "ai.minimax")
public class MinimaxConfigProperties {
    private String apiHost = "https://api.minimax.io/";
    private String apiKey = "";
    private String chatCompletionUrl = "v1/chat/completions";
    private String anthropicApiHost = "https://api.minimax.io/anthropic/";
    private String anthropicMessagesUrl = "v1/messages";
}
