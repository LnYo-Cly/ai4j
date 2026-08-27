package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description 璞嗗寘(鐏北寮曟搸鏂硅垷) 閰嶇疆鏂囦欢
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoubaoConfig {

    private String apiHost = "https://ark.cn-beijing.volces.com/api/v3/";
    private String apiKey = "";
    private String chatCompletionUrl = "chat/completions";
    private String imageGenerationUrl = "images/generations";
    private String responsesUrl = "responses";
    private String rerankApiHost = "https://api-knowledgebase.mlp.cn-beijing.volces.com/";
    private String rerankUrl = "api/knowledge/service/rerank";

    /**
     * Seedance 视频网关根地址（含 /api/v3 前缀由路径承担）。默认空：调用方经
     * {@code IVideoService.create(baseUrl, ...)} 传入网关 baseUrl；为空且未传参时回退 Ark 官方根
     * （不回退 {@link #apiHost}，其默认值已含 /api/v3/ 段，直接拼接会重复）。
     */
    private String videoApiHost = "";

    /** 视频网关 key。为空时回退 {@link #apiKey}。 */
    private String videoApiKey = "";

    private String videoCreatePath = "api/v3/contents/generations/tasks";

    /** 轮询路径：GET 同路径 + /{task_id}。 */
    private String videoQueryPath = "api/v3/contents/generations/tasks";
}

