package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description 智谱AI平台配置信息
 * @Date 2024/8/27 22:12
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZhipuConfig {

    private String apiHost = "https://open.bigmodel.cn/api/paas/";
    private String apiKey = "";
    private String chat_completion = "v4/chat/completions";
    private String embedding= "v4/embeddings";
}
