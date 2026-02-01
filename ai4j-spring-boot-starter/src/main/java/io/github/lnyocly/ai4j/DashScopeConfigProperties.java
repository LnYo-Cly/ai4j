package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description 鏅鸿氨 閰嶇疆鏂囦欢
 * @Date 2024/8/28 17:39
 */

@Data
@ConfigurationProperties(prefix = "ai.dashscope")
public class DashScopeConfigProperties {
    private String apiHost = "https://dashscope.aliyuncs.com/api/v2/apps/protocols/compatible-mode/v1/";
    private String responsesUrl = "responses";
    private String apiKey = "";
}

