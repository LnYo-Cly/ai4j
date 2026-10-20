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

    public MinimaxConfig(String apiHost, String apiKey, String chatCompletionUrl,
                         String anthropicApiHost, String anthropicMessagesUrl) {
        this(apiHost, apiKey, chatCompletionUrl);
        this.anthropicApiHost = anthropicApiHost;
        this.anthropicMessagesUrl = anthropicMessagesUrl;
    }

    public MinimaxConfig(String apiHost, String apiKey, String chatCompletionUrl,
                         String videoApiHost, String videoApiKey, String videoCreateUrl,
                         String videoQueryUrl, String fileRetrieveUrl) {
        this(apiHost, apiKey, chatCompletionUrl);
        this.videoApiHost = videoApiHost;
        this.videoApiKey = videoApiKey;
        this.videoCreateUrl = videoCreateUrl;
        this.videoQueryUrl = videoQueryUrl;
        this.fileRetrieveUrl = fileRetrieveUrl;
    }

    /**
     * Hailuo 视频网关。视频 API 与 chat 网关不同域（私有协议 {@code api.minimax.chat}，
     * 非 OpenAI 兼容），故独立配置 host；为空时回退 {@link #apiHost}。
     */
    private String videoApiHost = "https://api.minimax.chat";

    /** 视频网关 key（api.minimax.chat 平台 key）。为空时回退 {@link #apiKey}。 */
    private String videoApiKey = "";

    private String videoCreateUrl = "v1/video_generation";

    private String videoQueryUrl = "v1/query/video_generation";

    private String fileRetrieveUrl = "v1/files/retrieve";
}
