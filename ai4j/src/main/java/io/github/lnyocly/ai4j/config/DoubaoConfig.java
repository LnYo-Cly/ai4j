package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description 豆包(火山引擎方舟) 配置文件
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoubaoConfig {

    private String apiHost = "https://ark.cn-beijing.volces.com/api/v3/";
    private String apiKey = "";
    private String chatCompletionUrl = "chat/completions";
    private String imageGenerationUrl = "images/generations";
}
