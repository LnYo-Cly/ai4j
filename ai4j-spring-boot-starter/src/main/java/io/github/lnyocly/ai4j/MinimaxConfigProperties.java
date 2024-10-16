package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author : isxuwl
 * @Date: 2024/10/15 16:27
 * @Model Description:
 * @Description:
 */

@Data
@ConfigurationProperties(prefix = "ai.minimax")
public class MinimaxConfigProperties {
    private String apiHost = "https://api.minimax.chat/";
    private String apiKey = "";
    private String chatCompletionUrl = "v1/text/chatcompletion_v2";
}
