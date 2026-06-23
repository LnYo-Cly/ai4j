package io.github.lnyocly.ai4j;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Anthropic（Claude / Messages API，{@code /v1/messages}）配置。
 * <p>默认指向 {@code https://api.anthropic.com/}；覆盖 {@code apiHost} 可对接合作厂家的
 * Anthropic 兼容入口（如智谱 coding-plan {@code open.bigmodel.cn/api/anthropic}、
 * MiniMax coding-plan {@code api.minimaxi.com/anthropic}）。
 */
@Data
@ConfigurationProperties(prefix = "ai.anthropic")
public class AnthropicConfigProperties {
    private String apiHost = "https://api.anthropic.com/";
    private String apiKey = "";
    private String chatCompletionUrl = "v1/messages";
    private String apiVersion = "2023-06-01";
    private long streamTimeoutMillis = 600_000L;
}
