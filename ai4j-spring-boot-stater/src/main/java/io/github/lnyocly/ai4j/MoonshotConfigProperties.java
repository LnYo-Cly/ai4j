package io.github.lnyocly.ai4j;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author cly
 * @Description Moonshot 配置文件
 * @Date 2024/8/30 15:56
 */
@ConfigurationProperties(prefix = "ai.moonshot")
public class MoonshotConfigProperties {
    private String apiHost = "https://api.moonshot.cn/";
    private String apiKey = "";
    private String chatCompletionUrl = "v1/chat/completions";

    public String getApiHost() {
        return apiHost;
    }

    public void setApiHost(String apiHost) {
        this.apiHost = apiHost;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getChatCompletionUrl() {
        return chatCompletionUrl;
    }

    public void setChatCompletionUrl(String chatCompletionUrl) {
        this.chatCompletionUrl = chatCompletionUrl;
    }
}
