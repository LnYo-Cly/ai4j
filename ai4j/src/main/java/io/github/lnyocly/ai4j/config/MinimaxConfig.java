package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MiniMax OpenAI-compatible configuration.
 * <p>
 * Defaults to the global endpoint {@code https://api.minimax.io/}.
 * Override {@code apiHost} together with {@code chatCompletionUrl} when switching
 * to another regional base such as {@code https://api.minimaxi.com/}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MinimaxConfig {
    private String apiHost = "https://api.minimax.io/";
    private String apiKey = "";
    private String chatCompletionUrl = "v1/chat/completions";
}
