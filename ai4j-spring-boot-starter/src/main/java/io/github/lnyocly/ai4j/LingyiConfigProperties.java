package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description 零一万物配置文件
 * @Date 2024/9/9 23:31
 */

@Data
@ConfigurationProperties(prefix = "ai.lingyi")
public class LingyiConfigProperties {
    private String apiHost = "https://api.lingyiwanwu.com/";
    private String apiKey = "";
    private String chatCompletionUrl = "v1/chat/completions";
}
