package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description OpenAi骞冲彴閰嶇疆鏂囦欢淇℃伅
 * @Date 2024/8/8 0:18
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashScopeConfig {
    private String apiHost = "https://dashscope.aliyuncs.com/api/v2/apps/protocols/compatible-mode/v1/";
    private String responsesUrl = "responses";
    private String apiKey = "";
}

