package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description 智谱 配置文件
 * @Date 2024/8/28 17:39
 */

@Data
@ConfigurationProperties(prefix = "ai.dashscope")
public class DashScopeConfigProperties {
    private String apiKey = "";
}
