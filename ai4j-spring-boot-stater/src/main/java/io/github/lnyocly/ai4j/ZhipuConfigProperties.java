package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description 智谱 配置文件
 * @Date 2024/8/28 17:39
 */

@Data
@ConfigurationProperties(prefix = "ai.zhipu")
public class ZhipuConfigProperties {
    private String apiHost = "https://open.bigmodel.cn/api/paas/";
    private String apiKey = "";
    private String chatCompletionUrl = "v4/chat/completions";
    private String embeddingUrl = "v4/embeddings";
}
