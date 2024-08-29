package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description DeepSeek 配置文件
 * @Date 2024/8/29 10:31
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeepSeekConfig {

    private String apiHost = "https://api.deepseek.com/";
    private String apiKey = "";
    private String chat_completion = "chat/completions";
}
