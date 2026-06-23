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

    /** 单次流式调用的安全网超时上限（毫秒），防止挂起的流永久阻塞 messagesStream。默认 10 分钟。 */
    private long streamTimeoutMillis = 600_000L;
}
