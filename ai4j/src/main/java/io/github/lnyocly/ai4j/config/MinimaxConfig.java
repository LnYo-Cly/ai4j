package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MiniMax OpenAI-compatible and Anthropic-compatible configuration.
 * <p>
 * Defaults to the global endpoints. Override {@code apiHost} and
 * {@code anthropicApiHost} for the CN endpoints when needed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MinimaxConfig {
    private String apiHost = "https://api.minimax.io/";
    private String apiKey = "";
    private String chatCompletionUrl = "v1/chat/completions";
    private String anthropicApiHost = "https://api.minimax.io/anthropic/";
    private String anthropicMessagesUrl = "v1/messages";

    public MinimaxConfig(String apiHost, String apiKey, String chatCompletionUrl) {
        this.apiHost = apiHost;
        this.apiKey = apiKey;
        this.chatCompletionUrl = chatCompletionUrl;
    }
}
