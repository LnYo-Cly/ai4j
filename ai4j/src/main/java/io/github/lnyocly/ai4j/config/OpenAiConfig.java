package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author cly
 * @Description OpenAi平台配置文件信息
 * @Date 2024/8/8 0:18
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiConfig {
    private String apiHost = "https://api.openai.com/";
    private String apiKey = "";
    private String v1_chat_completions = "v1/chat/completions";
    private String v1_embeddings = "v1/embeddings";

}
