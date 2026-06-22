package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Anthropic（Claude / Messages API）平台配置。
 * <p>
 * 默认指向 Anthropic 官方 {@code https://api.anthropic.com/}。
 * 合作厂家的 Anthropic 兼容入口（如智谱 Coding Plan {@code open.bigmodel.cn/api/anthropic}）
 * 只需覆盖 {@code apiHost} 即可复用同一适配器。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnthropicConfig {

    private String apiHost = "https://api.anthropic.com/";
    private String apiKey = "";
    private String chatCompletionUrl = "v1/messages";
    private String apiVersion = "2023-06-01";
}
