package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description DeepSeek配置文件
 * @Date 2024/8/29 15:01
 */

@Data
@ConfigurationProperties(prefix = "ai.deepseek")
public class DeepSeekConfigProperties {

    private String apiHost = "https://api.deepseek.com/";
    private String apiKey = "";
    private String chatCompletionUrl = "chat/completions";
}
