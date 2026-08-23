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

    /**
     * 图片编辑端点（图生图/编辑）。OpenAI 标准为 multipart；JSON 形态（image 传
     * URL/base64 数组）为兼容网关通用扩展（chat2api/skyengine 等）。
     */
    private String imageEditUrl = "v1/images/edits";
    private String responsesUrl = "v1/responses";
    private String videoUrl = "v1/videos";
    /**
     * Path used to create a video task. Kept separate from {@link #videoUrl} because some
     * gateways create at {@code v1/videos/generations} while still polling {@code v1/videos/{id}}.
     */
    private String videoCreateUrl = "v1/videos";

}

