package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author : isxuwl
 * @Date: 2024/10/15 16:08
 * @Model Description:
 * @Description:
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MinimaxConfig {
    /**
     * MiniMax 网关。默认指向当前推荐的 OpenAI 兼容网关 {@code https://api.minimaxi.com/}，
     * 支持最新模型（MiniMax-M3 等）与 coding-plan key。
     * 旧私有 {@code v1/text/chatcompletion_v2} 端点（{@code api.minimax.chat}）对新模型返回
     * plan-not-support / 空 choices；如需回退到旧端点，覆盖本字段与 {@link #chatCompletionUrl}。
     */
    private String apiHost = "https://api.minimaxi.com/";
    private String apiKey = "";
    private String chatCompletionUrl = "v1/chat/completions";

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
